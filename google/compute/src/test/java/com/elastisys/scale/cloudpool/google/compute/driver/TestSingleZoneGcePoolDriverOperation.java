package com.elastisys.scale.cloudpool.google.compute.driver;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elastisys.scale.cloudpool.api.NotFoundException;
import com.elastisys.scale.cloudpool.api.types.Machine;
import com.elastisys.scale.cloudpool.api.types.MachineState;
import com.elastisys.scale.cloudpool.api.types.MembershipStatus;
import com.elastisys.scale.cloudpool.api.types.ServiceState;
import com.elastisys.scale.cloudpool.commons.basepool.BaseCloudPool;
import com.elastisys.scale.cloudpool.commons.basepool.driver.CloudPoolDriverException;
import com.elastisys.scale.cloudpool.commons.basepool.driver.DriverConfig;
import com.elastisys.scale.cloudpool.commons.basepool.driver.TerminateMachinesException;
import com.elastisys.scale.cloudpool.google.commons.api.CloudApiSettings;
import com.elastisys.scale.cloudpool.google.commons.api.compute.ComputeClient;
import com.elastisys.scale.cloudpool.google.commons.api.compute.InstanceGroupClient;
import com.elastisys.scale.cloudpool.google.commons.api.compute.functions.InstanceToMachine;
import com.elastisys.scale.cloudpool.google.commons.api.compute.metadata.MetadataKeys;
import com.elastisys.scale.cloudpool.google.commons.errors.GceException;
import com.elastisys.scale.cloudpool.google.commons.utils.InstanceGroupUrl;
import com.elastisys.scale.cloudpool.google.commons.utils.MetadataUtil;
import com.elastisys.scale.cloudpool.google.compute.driver.config.ProvisioningTemplate;
import com.elastisys.scale.commons.json.JsonUtils;
import com.elastisys.scale.commons.net.url.UrlUtils;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;

/**
 * Exercises the {@link GoogleComputeEnginePoolDriver} against a mocked
 * single-zone instance group.
 */
public class TestSingleZoneGcePoolDriverOperation {
    private static final Logger LOG = LoggerFactory.getLogger(TestSingleZoneGcePoolDriverOperation.class);

    /** Sample project name. */
    private static final String PROJECT = "my-project";
    /** Sample zone name. */
    private static final String ZONE = "eu-west1-b";
    /** Sample instance group name. */
    private static final String INSTANCE_GROUP = "webserver-instance-group";
    private static final String INSTANCE_GROUP_URL = InstanceGroupUrl.managedZonal(PROJECT, ZONE, INSTANCE_GROUP)
            .getUrl();

    /** Sample cloud pool name. */
    private static final String POOL_NAME = "webserver-pool";

    private static final DriverConfig POOL_DRIVER_CONFIG = driverConfig(POOL_NAME,
            new ProvisioningTemplate(INSTANCE_GROUP, PROJECT, null, ZONE));

    /** Mocked GCE client used by the GCE pool driver under test. */
    private ComputeClient gceClientMock = mock(ComputeClient.class);
    /**
     * Mocked (single-zone) instance group client client used by the GCE pool
     * driver under test.
     */
    private InstanceGroupClient instanceGroupClientMock = mock(InstanceGroupClient.class);
    /** Object under test. */
    private GoogleComputeEnginePoolDriver driver;

    @Before
    public void beforeTestMethod() {
        this.driver = new GoogleComputeEnginePoolDriver(this.gceClientMock);
        this.driver.configure(POOL_DRIVER_CONFIG);
        reset(this.gceClientMock);
        reset(this.instanceGroupClientMock);
    }

    /**
     * A call to list machines should invoke the GCE client to retrieve metadata
     * for each instance in the group and convert them to {@link Machine}s.
     */
    @Test
    public void listMachines() {
        int targetSize = 2;
        int runningInstances = 2;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        List<Machine> machines = this.driver.listMachines();

        // verify that instances were correctly translated to machines
        assertThat(machines.size(), is(2));
        assertThat(machines.get(0), is(new InstanceToMachine().apply(simulatedGroup.instances().get(0))));
        assertThat(machines.get(1), is(new InstanceToMachine().apply(simulatedGroup.instances().get(1))));

        //
        // verify expected calls to backend API clients
        //

        // should call to get a single-zone instance group client
        verify(this.gceClientMock).singleZoneInstanceGroup(INSTANCE_GROUP_URL);
        // should retrieve instance group metadata (targetSize)
        verify(this.instanceGroupClientMock).getInstanceGroup();
        // should retrieve instance group members
        verify(this.instanceGroupClientMock).listInstances();
        // should retrieve instance metadata about each member
        verify(this.gceClientMock).getInstance(simulatedGroup.instances().get(0).getSelfLink());
        verify(this.gceClientMock).getInstance(simulatedGroup.instances().get(1).getSelfLink());
    }

    /**
     * If the actual size of the instance froup is less than the target size
     * (for example, when a target size has been requested but the group has not
     * reached its new desired state yet) the
     * {@link GoogleComputeEnginePoolDriver} should report the
     * requested-but-not-yet-acquired instances as pseudo instances in state
     * {@link MachineState#REQUESTED}, to not fool the {@link BaseCloudPool}
     * from believing that the pool is too small and order new machines to be
     * started (and excessively increase the target size).
     */
    @Test
    public void listMachinesOnGroupThatHasNotReachedTargetSize() throws CloudPoolDriverException {
        // two outstanding instances: target size: 3, actual size: 1
        int targetSize = 3;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        List<Machine> machines = this.driver.listMachines();

        // verify that instances were correctly translated to machines
        assertThat(machines.size(), is(3));
        assertThat(machines.get(0), is(new InstanceToMachine().apply(simulatedGroup.instances().get(0))));
        assertThat(UrlUtils.basename(machines.get(1).getId()),
                is("requested-" + simulatedGroup.instanceTemplateName() + "-1"));
        assertThat(machines.get(1).getMachineState(), is(MachineState.REQUESTED));
        assertThat(UrlUtils.basename(machines.get(2).getId()),
                is("requested-" + simulatedGroup.instanceTemplateName() + "-2"));
        assertThat(machines.get(2).getMachineState(), is(MachineState.REQUESTED));
    }

    /**
     * A call to {@link GoogleComputeEnginePoolDriver#startMachines(int)} should
     * call through to resize the instance group.
     */
    @Test
    public void startMachines() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        List<Machine> machines = this.driver.startMachines(2);
        // psuedo-machines should be returned for requested instances
        assertThat(UrlUtils.basename(machines.get(0).getId()),
                is("requested-" + simulatedGroup.instanceTemplateName() + "-1"));
        assertThat(machines.get(0).getMachineState(), is(MachineState.REQUESTED));
        assertThat(UrlUtils.basename(machines.get(1).getId()),
                is("requested-" + simulatedGroup.instanceTemplateName() + "-2"));
        assertThat(machines.get(1).getMachineState(), is(MachineState.REQUESTED));

        //
        // verify calls to mock api clients
        //

        verify(this.instanceGroupClientMock).resize(3);
    }

    /**
     * A call to {@link GoogleComputeEnginePoolDriver#terminateMachine(String)}
     * should call through to resize the instance group.
     */
    @Test
    public void terminateMachine() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instanceUrl = simulatedGroup.instances().get(0).getSelfLink();
        this.driver.terminateMachines(asList(instanceUrl));

        //
        // verify calls to mock api clients
        //

        verify(this.instanceGroupClientMock).deleteInstances(Arrays.asList(instanceUrl));
    }

    /**
     * An attempt to terminate a psuedo instance (a stand-in for a
     * requested-but-not-yet-acquired instance) should result in decrementing
     * the target size of the instance group, but no attempt should be made to
     * delete the pseudo instance.
     */
    @Test
    public void terminatePseudoInstance() {
        // two outstanding instances: target size: 2, actual size: 1
        int targetSize = 2;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        List<Machine> machines = this.driver.listMachines();

        // verify that instances were correctly translated to machines
        assertThat(machines.size(), is(2));
        String pseudoInstanceUrl = machines.get(1).getId();

        assertThat(UrlUtils.basename(pseudoInstanceUrl),
                is("requested-" + simulatedGroup.instanceTemplateName() + "-1"));

        this.driver.terminateMachines(asList(pseudoInstanceUrl));

        // should call through to decrement group size
        verify(this.instanceGroupClientMock).resize(1);
        // should NOT call through to delete
        verify(this.instanceGroupClientMock, times(0)).deleteInstances(Arrays.asList(pseudoInstanceUrl));
    }

    /**
     * Verifies behavior when terminating multiple instances.
     */
    @Test
    public void terminateMultipleInstances() throws Exception {
        int targetSize = 3;
        int runningInstances = 3;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instance1Url = simulatedGroup.instances().get(0).getSelfLink();
        String instance2Url = simulatedGroup.instances().get(1).getSelfLink();

        this.driver.terminateMachines(asList(instance1Url, instance2Url));

        //
        // verify calls to mock api clients
        //

        verify(this.instanceGroupClientMock).deleteInstances(Arrays.asList(instance1Url));
        verify(this.instanceGroupClientMock).deleteInstances(Arrays.asList(instance2Url));
    }

    /**
     * On a client error, a {@link CloudPoolDriverException} should be raised.
     */
    @Test(expected = CloudPoolDriverException.class)
    public void terminateOnClientError() throws Exception {
        int targetSize = 3;
        int runningInstances = 3;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instance1Url = simulatedGroup.instances().get(0).getSelfLink();

        doThrow(new GceException("internal error")).when(this.instanceGroupClientMock)
                .deleteInstances(asList(instance1Url));

        this.driver.terminateMachines(asList(instance1Url));
    }

    /**
     * It should not be allowed to attempt to delete an instance that is not a
     * member of the instance group.
     */
    @Test
    public void terminateOnNonGroupMember() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instanceUrl = String.format("https://www.googleapis.com/compute/v1/projects/%s/zones/%s/instances/%s",
                PROJECT, ZONE, "mysql-1");
        try {
            this.driver.terminateMachines(asList(instanceUrl));
            fail("should not be possible to delete non-group member");
        } catch (TerminateMachinesException e) {
            // expected
            assertThat(e.getTerminatedMachines().size(), is(0));
            assertTrue(e.getTerminationErrors().containsKey(instanceUrl));
            assertThat(e.getTerminationErrors().get(instanceUrl), instanceOf(NotFoundException.class));
        }

        // should NOT call through to delete
        verify(this.instanceGroupClientMock, times(0)).deleteInstances(Arrays.asList(instanceUrl));
    }

    /**
     * When some terminations were successful and some failed, a
     * {@link TerminateMachinesException} should be thrown which indicates which
     * instances were terminated and which instance terminations failed.
     */
    @Test
    public void terminateOnPartialFailure() {
        int targetSize = 2;
        int runningInstances = 2;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instance1Url = simulatedGroup.instances().get(0).getSelfLink();
        String instance2Url = simulatedGroup.instances().get(1).getSelfLink();
        // note: not a member of the pool
        String instance3Url = "https://www.googleapis.com/compute/v1/projects/my-project/zones/eu-west1-b/instances/webserver-3";

        try {
            this.driver.terminateMachines(asList(instance3Url, instance1Url, instance2Url));
        } catch (TerminateMachinesException e) {
            // expected
            assertTrue(e.getTerminationErrors().keySet().contains(instance3Url));
            assertThat(e.getTerminationErrors().get(instance3Url), instanceOf(NotFoundException.class));
            assertThat(e.getTerminatedMachines(), is(asList(instance1Url, instance2Url)));
        }
    }

    /**
     * {@link GoogleComputeEnginePoolDriver#detachMachine(String)} should call
     * through to abandon instance in instance group API.
     */
    @Test
    public void detachInstance() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instanceUrl = simulatedGroup.instances().get(0).getSelfLink();

        this.driver.detachMachine(instanceUrl);

        // should call through to abandon instance
        verify(this.instanceGroupClientMock).abandonInstances(Arrays.asList(instanceUrl));
    }

    /**
     * It should not be possible to detach non-group members.
     */
    @Test
    public void detachNonMemberInstance() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instanceUrl = String.format("https://www.googleapis.com/compute/v1/projects/%s/zones/%s/instances/%s",
                PROJECT, ZONE, "mysql-1");
        try {
            this.driver.detachMachine(instanceUrl);
            fail("should not be possible to detach non-group member");
        } catch (NotFoundException e) {
            // expected
        }

        // should NOT call through to abandon instance
        verify(this.instanceGroupClientMock, times(0)).abandonInstances(Arrays.asList(instanceUrl));
    }

    /**
     * setServiceState should call through to modify the instance's metadata.
     */
    @Test
    public void setServiceState() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        Instance instance = simulatedGroup.instances().get(0);
        Map<String, String> instanceMetadata = MetadataUtil.toMap(instance.getMetadata());
        String instanceUrl = instance.getSelfLink();

        this.driver.setServiceState(instanceUrl, ServiceState.IN_SERVICE);

        instanceMetadata.put(MetadataKeys.SERVICE_STATE, ServiceState.IN_SERVICE.name());
        Metadata expectedMetadata = instance.getMetadata().clone().setItems(MetadataUtil.toItems(instanceMetadata));

        // should call through to set metadata
        verify(this.gceClientMock).setMetadata(instanceUrl, expectedMetadata);
    }

    /**
     * It should not be possible to set service state for a non-group member.
     */
    @Test
    public void setServiceStateOnNonMemberInstance() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instanceUrl = String.format("https://www.googleapis.com/compute/v1/projects/%s/zones/%s/instances/%s",
                PROJECT, ZONE, "mysql-1");

        try {
            this.driver.setServiceState(instanceUrl, ServiceState.IN_SERVICE);
            fail("should not be possible to set metadata for a non-member instance");
        } catch (NotFoundException e) {
            // expected
        }

        // should NOT call through to set metadata
        verify(this.gceClientMock, times(0)).setMetadata(argThat(is(instanceUrl)), argThat(isA(Metadata.class)));
    }

    /**
     * setMembershipStatus should call through to set the instance's metadata
     */
    @Test
    public void setMembershipStatus() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        Instance instance = simulatedGroup.instances().get(0);
        Map<String, String> instanceMetadata = MetadataUtil.toMap(instance.getMetadata());
        String instanceUrl = instance.getSelfLink();

        this.driver.setMembershipStatus(instanceUrl, MembershipStatus.blessed());

        instanceMetadata.put(MetadataKeys.MEMBERSHIP_STATUS,
                JsonUtils.toString(JsonUtils.toJson(MembershipStatus.blessed())));
        Metadata expectedMetadata = instance.getMetadata().clone().setItems(MetadataUtil.toItems(instanceMetadata));

        // should call through to set metadata
        verify(this.gceClientMock).setMetadata(instanceUrl, expectedMetadata);
    }

    /**
     * It should not be possible to set membership status for a non-group
     * member.
     */
    @Test
    public void setMembershipStatusOnNonMemberInstance() {
        int targetSize = 1;
        int runningInstances = 1;
        FakeSingleZoneInstanceGroup simulatedGroup = new FakeSingleZoneInstanceGroup(POOL_DRIVER_CONFIG, targetSize,
                runningInstances);
        setUpMockedInstanceGroup(simulatedGroup);

        String instanceUrl = String.format("https://www.googleapis.com/compute/v1/projects/%s/zones/%s/instances/%s",
                PROJECT, ZONE, "mysql-1");

        try {
            this.driver.setMembershipStatus(instanceUrl, MembershipStatus.blessed());
            fail("should not be possible to set metadata for a non-member instance");
        } catch (NotFoundException e) {
            // expected
        }

        // should NOT call through to set metadata
        verify(this.gceClientMock, times(0)).setMetadata(argThat(is(instanceUrl)), argThat(isA(Metadata.class)));
    }

    private static DriverConfig driverConfig(String poolName, ProvisioningTemplate instanceTemplate) {
        return new DriverConfig(POOL_NAME,
                JsonUtils.toJson(new CloudApiSettings("src/test/resources/config/valid-service-account-key.json", null))
                        .getAsJsonObject(),
                JsonUtils.toJson(instanceTemplate).getAsJsonObject());
    }

    /**
     * Sets up the mock API clients to front a given fake GCE instance group.
     *
     * @param simulatedGroup
     */
    private void setUpMockedInstanceGroup(FakeSingleZoneInstanceGroup simulatedGroup) {
        LOG.debug("setting up mocked call to get instance group ...");
        when(this.instanceGroupClientMock.getInstanceGroup()).thenReturn(simulatedGroup.instanceGroupManager());
        LOG.debug("setting up mocked call to get instance group members {} ...",
                simulatedGroup.instances().stream().map(Instance::getName).collect(Collectors.toList()));
        when(this.instanceGroupClientMock.listInstances()).thenReturn(simulatedGroup.managedInstances());

        when(this.gceClientMock.singleZoneInstanceGroup(INSTANCE_GROUP_URL)).thenReturn(this.instanceGroupClientMock);
        when(this.gceClientMock.getInstanceTemplate(simulatedGroup.instanceGroupManager().getInstanceTemplate()))
                .thenReturn(simulatedGroup.instanceTemplate());

        for (Instance instance : simulatedGroup.instances()) {
            LOG.debug("setting up mocked call to get instance metadata for {} ...", instance.getName());
            when(this.gceClientMock.getInstance(instance.getSelfLink())).thenReturn(instance);
        }
    }

}
