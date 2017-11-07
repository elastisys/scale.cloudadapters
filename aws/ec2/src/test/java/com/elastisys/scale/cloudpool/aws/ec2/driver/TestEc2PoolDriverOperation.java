package com.elastisys.scale.cloudpool.aws.ec2.driver;

import static com.elastisys.scale.cloudpool.api.types.ServiceState.BOOTING;
import static com.elastisys.scale.cloudpool.api.types.ServiceState.IN_SERVICE;
import static com.elastisys.scale.cloudpool.aws.commons.ScalingTags.CLOUD_POOL_TAG;
import static com.elastisys.scale.cloudpool.aws.commons.ScalingTags.SERVICE_STATE_TAG;
import static com.elastisys.scale.cloudpool.aws.ec2.driver.MachinesMatcher.machines;
import static com.elastisys.scale.cloudpool.aws.ec2.driver.TestUtils.driverConfig;
import static com.elastisys.scale.cloudpool.aws.ec2.driver.TestUtils.ec2Instances;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.junit.Before;
import org.junit.Test;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Tag;
import com.elastisys.scale.cloudpool.api.NotFoundException;
import com.elastisys.scale.cloudpool.api.types.Machine;
import com.elastisys.scale.cloudpool.api.types.MembershipStatus;
import com.elastisys.scale.cloudpool.api.types.ServiceState;
import com.elastisys.scale.cloudpool.aws.commons.ScalingFilters;
import com.elastisys.scale.cloudpool.aws.commons.ScalingTags;
import com.elastisys.scale.cloudpool.aws.commons.poolclient.Ec2Client;
import com.elastisys.scale.cloudpool.commons.basepool.driver.CloudPoolDriver;
import com.elastisys.scale.cloudpool.commons.basepool.driver.CloudPoolDriverException;
import com.elastisys.scale.cloudpool.commons.basepool.driver.DriverConfig;
import com.elastisys.scale.cloudpool.commons.basepool.driver.StartMachinesException;
import com.elastisys.scale.cloudpool.commons.basepool.driver.TerminateMachinesException;
import com.elastisys.scale.commons.json.JsonUtils;

/**
 * Verifies the operational behavior of the {@link Ec2PoolDriver}.
 */
public class TestEc2PoolDriverOperation {

    private static final String POOL_NAME = "MyScalingPool";

    private static final Filter POOL_MEMBER_QUERY_FILTER = new Filter().withName(ScalingFilters.CLOUD_POOL_TAG_FILTER)
            .withValues(POOL_NAME);

    private Ec2Client mockClient = mock(Ec2Client.class);
    /** Object under test. */
    private Ec2PoolDriver driver;

    @Before
    public void onSetup() throws CloudPoolDriverException {
        this.driver = new Ec2PoolDriver(this.mockClient);
        this.driver.configure(driverConfig(POOL_NAME));
    }

    /**
     * {@link CloudPoolDriver#listMachines()} should delegate to
     * {@link Ec2Client} and basically return anything it returns.
     */
    @Test
    public void listMachines() throws CloudPoolDriverException {
        // empty pool
        setUpMockedPoolClient(POOL_NAME, ec2Instances());
        assertThat(this.driver.listMachines(), is(machines()));
        verify(this.mockClient).getInstances(asList(POOL_MEMBER_QUERY_FILTER));

        // non-empty pool
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));
        assertThat(this.driver.listMachines(), is(machines("i-1")));

        // pool with machines in different states
        List<Instance> members = ec2Instances(memberInstance("i-1", "running"), memberInstance("i-2", "pending"),
                memberInstance("i-3", "terminated"));
        setUpMockedPoolClient(POOL_NAME, members);
        List<Machine> machines = this.driver.listMachines();
        assertThat(machines, is(machines("i-1", "i-2", "i-3")));
        // verify that cloud-specific metadata is included for each machine
        assertTrue(machines.get(0).getMetadata().getAsJsonObject().has("instanceId"));
        assertTrue(machines.get(1).getMetadata().getAsJsonObject().has("instanceId"));
        assertTrue(machines.get(2).getMetadata().getAsJsonObject().has("instanceId"));
    }

    /**
     * A {@link CloudPoolDriverException} should be thrown if listing scaling
     * pool members fails.
     */
    @Test(expected = CloudPoolDriverException.class)
    public void listMachinesOnError() throws CloudPoolDriverException {
        // set up Amazon API call to fail
        List<Filter> queryFilters = Arrays.asList(POOL_MEMBER_QUERY_FILTER);
        when(this.mockClient.getInstances(queryFilters)).thenThrow(new AmazonServiceException("API unreachable"));
        this.driver.listMachines();
    }

    /**
     * Started machines should the pool membership should be marked with an
     * instance tag.
     */
    @Test
    public void startMachines() throws Exception {
        DriverConfig config = driverConfig(POOL_NAME);

        // scale up from 0 -> 1
        List<Instance> instances = ec2Instances();
        FakeEc2Client fakeEc2Client = new FakeEc2Client(instances);
        this.driver = new Ec2PoolDriver(fakeEc2Client);
        this.driver.configure(config);
        List<Machine> startedMachines = this.driver.startMachines(1);
        assertThat(startedMachines, is(machines("i-1")));
        // verify that pool/name tag was set on instance
        assertThat(membershipTag(fakeEc2Client.getInstanceMetadata("i-1")), is(POOL_NAME));

        // scale up from 1 -> 2
        instances = ec2Instances(memberInstance("i-1", "running"));
        fakeEc2Client = new FakeEc2Client(instances);
        this.driver = new Ec2PoolDriver(fakeEc2Client);
        this.driver.configure(config);
        startedMachines = this.driver.startMachines(1);
        assertThat(startedMachines, is(machines("i-2")));
        // verify that pool/name tag was set on instance
        assertThat(membershipTag(fakeEc2Client.getInstanceMetadata("i-2")), is(POOL_NAME));

        // scale up from 2 -> 4
        instances = ec2Instances(memberInstance("i-1", "running"), memberInstance("i-2", "pending"));
        fakeEc2Client = new FakeEc2Client(instances);
        this.driver = new Ec2PoolDriver(fakeEc2Client);
        this.driver.configure(config);
        startedMachines = this.driver.startMachines(2);
        assertThat(startedMachines, is(machines("i-3", "i-4")));
        // verify that pool/name tag was set on instance
        assertThat(membershipTag(fakeEc2Client.getInstanceMetadata("i-3")), is(POOL_NAME));
        assertThat(membershipTag(fakeEc2Client.getInstanceMetadata("i-4")), is(POOL_NAME));
    }

    /**
     * On cloud API errors, a {@link StartMachinesException} should be thrown.
     */
    @Test
    public void startMachinesOnFailure() throws StartMachinesException {
        // set up mock to throw an error whenever asked to launch an instance
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));
        doThrow(new AmazonClientException("API unreachable")).when(this.mockClient).launchInstances(
                this.driver.provisioningTemplate(), 1, Arrays.asList(new Tag(CLOUD_POOL_TAG, POOL_NAME)));

        // should raise an exception
        try {
            this.driver.startMachines(1);
            fail("startMachines expected to fail");
        } catch (StartMachinesException e) {
            assertThat(e.getRequestedMachines(), is(1));
            assertThat(e.getStartedMachines().size(), is(0));
        }
    }

    /**
     * Verifies behavior when terminating a single pool member.
     */
    @Test
    public void terminateSingleInstance() throws Exception {
        DriverConfig config = driverConfig(POOL_NAME);

        this.driver = new Ec2PoolDriver(
                new FakeEc2Client(ec2Instances(memberInstance("i-1", "running"), memberInstance("i-2", "pending"))));
        this.driver.configure(config);
        this.driver.terminateMachines(asList("i-1"));
        assertThat(this.driver.listMachines(), is(machines("i-2")));

        this.driver.terminateMachines(asList("i-2"));
        assertThat(this.driver.listMachines(), is(machines()));
    }

    /**
     * Verifies behavior when terminating multiple pool members.
     */
    @Test
    public void terminateMultipleInstances() throws Exception {
        DriverConfig config = driverConfig(POOL_NAME);

        this.driver = new Ec2PoolDriver(
                new FakeEc2Client(ec2Instances(memberInstance("i-1", "running"), memberInstance("i-2", "pending"))));
        this.driver.configure(config);
        this.driver.terminateMachines(asList("i-1", "i-2"));
        assertThat(this.driver.listMachines(), is(Collections.emptyList()));
    }

    /**
     * On client error, a {@link CloudPoolDriverException} should be raised.
     */
    @Test(expected = CloudPoolDriverException.class)
    public void terminateOnClientError() throws Exception {
        // set up mock to throw an error whenever terminateInstance is called
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));
        doThrow(new AmazonClientException("API unreachable")).when(this.mockClient).terminateInstances(asList("i-1"));

        this.driver.terminateMachines(asList("i-1"));
    }

    /**
     * Trying to terminate a machine instance that is not recognized as a pool
     * member should result in a {@link TerminateMachinesException}.
     */
    @Test
    public void terminateOnNonGroupMember() {
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));

        try {
            this.driver.terminateMachines(asList("i-2"));
            fail("should fail to terminate instance that is not a pool member");
        } catch (TerminateMachinesException e) {
            // expected
            assertTrue(e.getTerminationErrors().keySet().contains("i-2"));
            assertThat(e.getTerminationErrors().get("i-2"), instanceOf(NotFoundException.class));
            assertThat(e.getTerminatedMachines(), is(Collections.emptyList()));
        }
    }

    /**
     * When some terminations were successful and some failed, a
     * {@link TerminateMachinesException} should be thrown which indicates which
     * instances were terminated and which instance terminations failed.
     */
    @Test
    public void terminateOnPartialFailure() {
        DriverConfig config = driverConfig(POOL_NAME);

        this.driver = new Ec2PoolDriver(new FakeEc2Client(ec2Instances(memberInstance("i-2", "running"))));
        this.driver.configure(config);
        // i-1 is not a pool member and should fail
        try {
            this.driver.terminateMachines(asList("i-1", "i-2"));
            fail("expected to fail");
        } catch (TerminateMachinesException e) {
            // terminating i-2 should succeed
            assertThat(e.getTerminatedMachines(), is(asList("i-2")));
            // terminating i-1 should fail
            assertTrue(e.getTerminationErrors().keySet().contains("i-1"));
            assertThat(e.getTerminationErrors().get("i-1"), instanceOf(NotFoundException.class));
        }
    }

    /**
     * Verify that the pool membership tag is removed from the server when
     * detaching a pool member.
     */
    @Test
    public void detach() {
        FakeEc2Client fakeClient = new FakeEc2Client(ec2Instances(memberInstance("i-1", "running")));
        this.driver = new Ec2PoolDriver(fakeClient);
        this.driver.configure(driverConfig(POOL_NAME));
        assertThat(membershipTag(fakeClient.getInstanceMetadata("i-1")), is(POOL_NAME));

        this.driver.detachMachine("i-1");
        assertThat(membershipTag(fakeClient.getInstanceMetadata("i-1")), is(nullValue()));
    }

    /**
     * It should not be possible to detach a machine instance that is not
     * recognized as a pool member.
     */
    @Test(expected = NotFoundException.class)
    public void detachOnNonGroupMember() {
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));

        this.driver.detachMachine("i-2");
    }

    /**
     * A {@link CloudPoolDriverException} should be thrown on failure to untag
     * an instance that is to be detached from the pool.
     */
    @Test(expected = CloudPoolDriverException.class)
    public void detachOnError() throws Exception {
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));

        List<Tag> poolTag = Arrays.asList(new Tag().withKey(CLOUD_POOL_TAG).withValue(POOL_NAME));
        doThrow(new RuntimeException("API unreachable")).when(this.mockClient).untagResource("i-1", poolTag);

        this.driver.detachMachine("i-1");
    }

    /**
     * Verifies that a pool membership tag gets set on instances that are
     * attached to the pool.
     */
    @Test
    public void attach() {
        FakeEc2Client fakeClient = new FakeEc2Client(ec2Instances(nonMemberInstance("i-1", "running")));
        this.driver = new Ec2PoolDriver(fakeClient);
        this.driver.configure(driverConfig(POOL_NAME));
        assertThat(membershipTag(fakeClient.getInstanceMetadata("i-1")), is(nullValue()));

        this.driver.attachMachine("i-1");
        assertThat(membershipTag(fakeClient.getInstanceMetadata("i-1")), is(POOL_NAME));
    }

    /**
     * An attempt to attach a non-existing machine should result in
     * {@link NotFoundException}.
     */
    @Test(expected = NotFoundException.class)
    public void attachNonExistingMachine() {
        FakeEc2Client fakeClient = new FakeEc2Client(ec2Instances(nonMemberInstance("i-1", "running")));

        this.driver = new Ec2PoolDriver(fakeClient);
        this.driver.configure(driverConfig(POOL_NAME));

        this.driver.attachMachine("i-2");
    }

    /**
     * A {@link CloudPoolDriverException} should be thrown on failure to tag an
     * instance that is to be attached to the pool.
     */
    @Test(expected = CloudPoolDriverException.class)
    public void attachOnError() throws Exception {
        setUpMockedPoolClient(POOL_NAME, ec2Instances(nonMemberInstance("i-1", "running")));
        List<Tag> poolTag = Arrays.asList(new Tag().withKey(CLOUD_POOL_TAG).withValue(POOL_NAME));
        doThrow(new RuntimeException("API unreachable")).when(this.mockClient).tagResource("i-1", poolTag);

        this.driver.attachMachine("i-1");
    }

    /**
     * Verifies that a
     * {@link CloudPoolDriver#setServiceState(String, ServiceState)} stores the
     * state by setting a tag on the server.
     */
    @Test
    public void setServiceState() {
        FakeEc2Client fakeClient = new FakeEc2Client(ec2Instances(memberInstance("i-1", "running")));
        this.driver = new Ec2PoolDriver(fakeClient);
        this.driver.configure(driverConfig(POOL_NAME));
        assertThat(serviceStateTag(fakeClient.getInstanceMetadata("i-1")), is(nullValue()));

        this.driver.setServiceState("i-1", BOOTING);
        assertThat(serviceStateTag(fakeClient.getInstanceMetadata("i-1")), is(BOOTING.name()));

        this.driver.setServiceState("i-1", IN_SERVICE);
        assertThat(serviceStateTag(fakeClient.getInstanceMetadata("i-1")), is(IN_SERVICE.name()));
    }

    /**
     * It should not be possible to set service state on a machine instance that
     * is not recognized as a pool member.
     */
    @Test(expected = NotFoundException.class)
    public void setServiceStateOnNonGroupMember() {
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));

        this.driver.setServiceState("i-2", ServiceState.IN_SERVICE);
    }

    /**
     * A {@link CloudPoolDriverException} should be thrown on failure to tag the
     * service state of a pool instance.
     */
    @Test(expected = CloudPoolDriverException.class)
    public void setServiceStateOnError() {
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));

        List<Tag> serviceStateTag = asList(new Tag().withKey(SERVICE_STATE_TAG).withValue(IN_SERVICE.name()));
        doThrow(new RuntimeException("API unreachable")).when(this.mockClient).tagResource("i-1", serviceStateTag);

        this.driver.setServiceState("i-1", IN_SERVICE);
    }

    /**
     * Verifies that a
     * {@link CloudPoolDriver#setMembershipStatus(String, MembershipStatus)}
     * stores the status by setting a tag on the server.
     */
    @Test
    public void setMembershipStatus() {
        FakeEc2Client fakeClient = new FakeEc2Client(ec2Instances(memberInstance("i-1", "running")));
        this.driver = new Ec2PoolDriver(fakeClient);
        this.driver.configure(driverConfig(POOL_NAME));
        assertThat(membershipStatusTag(fakeClient.getInstanceMetadata("i-1")), is(nullValue()));

        MembershipStatus status = MembershipStatus.awaitingService();
        String statusAsJson = JsonUtils.toString(JsonUtils.toJson(status));
        this.driver.setMembershipStatus("i-1", status);
        assertThat(membershipStatusTag(fakeClient.getInstanceMetadata("i-1")), is(statusAsJson));

        MembershipStatus otherStatus = MembershipStatus.blessed();
        String otherStatusAsJson = JsonUtils.toString(JsonUtils.toJson(otherStatus));
        this.driver.setMembershipStatus("i-1", otherStatus);
        assertThat(membershipStatusTag(fakeClient.getInstanceMetadata("i-1")), is(otherStatusAsJson));
    }

    /**
     * It should not be possible to set membership status on a machine instance
     * that is not recognized as a pool member.
     */
    @Test(expected = NotFoundException.class)
    public void setMembershipStatusOnNonGroupMember() {
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));

        this.driver.setMembershipStatus("i-2", MembershipStatus.blessed());
    }

    /**
     * A {@link CloudPoolDriverException} should be thrown on failure to tag the
     * membership status of a pool instance.
     */
    @Test(expected = CloudPoolDriverException.class)
    public void setMembershipStatusOnError() {
        setUpMockedPoolClient(POOL_NAME, ec2Instances(memberInstance("i-1", "running")));

        MembershipStatus status = MembershipStatus.awaitingService();
        String statusAsJson = JsonUtils.toString(JsonUtils.toJson(status));
        List<Tag> tag = asList(new Tag().withKey(ScalingTags.MEMBERSHIP_STATUS_TAG).withValue(statusAsJson));
        doThrow(new RuntimeException("API unreachable")).when(this.mockClient).tagResource("i-1", tag);

        this.driver.setMembershipStatus("i-1", status);
    }

    private void setUpMockedPoolClient(String poolName, List<Instance> poolMembers) {
        // set up response to queries for pool member instances
        List<Filter> poolQueryFilters = asList(POOL_MEMBER_QUERY_FILTER);
        when(this.mockClient.getInstances(poolQueryFilters)).thenReturn(poolMembers);

        // set up response to queries for pool member meta data
        for (Instance instance : poolMembers) {
            when(this.mockClient.getInstanceMetadata(instance.getInstanceId())).thenReturn(instance);
        }
    }

    /**
     * Gets the pool membership tag ({@link Constants#CLOUD_POOL_TAG}) for a
     * {@link Server} or <code>null</code> if none is set.
     *
     * @param server
     */
    private static String membershipTag(Instance instance) {
        return getTag(instance, ScalingTags.CLOUD_POOL_TAG);
    }

    /**
     * Gets the service state tag ({@link ScalingTags#SERVICE_STATE_TAG}) for a
     * {@link Server} or <code>null</code> if none is set.
     *
     * @param server
     */
    private static String serviceStateTag(Instance instance) {
        return getTag(instance, SERVICE_STATE_TAG);
    }

    /**
     * Gets the service state tag ({@link ScalingTags#MEMBERSHIP_STATUS_TAG})
     * for a {@link Server} or <code>null</code> if none is set.
     *
     * @param server
     */
    private static String membershipStatusTag(Instance instance) {
        return getTag(instance, ScalingTags.MEMBERSHIP_STATUS_TAG);
    }

    /**
     * Retrieves a particular meta data tag value from an {@link Instance} or
     * return <code>null</code> if no such tag key is set on the instance.
     *
     * @param instance
     * @param tagKey
     * @return The value set for the key or <code>null</code> if not found.
     */
    private static String getTag(Instance instance, String tagKey) {
        List<Tag> tags = instance.getTags();
        for (Tag tag : tags) {
            if (tag.getKey().equals(tagKey)) {
                return tag.getValue();
            }
        }
        return null;
    }

    private static Instance memberInstance(String id, String state) {
        List<Tag> tags = Arrays.asList(new Tag().withKey(ScalingTags.CLOUD_POOL_TAG).withValue(POOL_NAME));
        return TestUtils.ec2Instance(id, state, tags);
    }

    private static Instance nonMemberInstance(String id, String state) {
        List<Tag> tags = Arrays.asList();
        return TestUtils.ec2Instance(id, state, tags);
    }

}
