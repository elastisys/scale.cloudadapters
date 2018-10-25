package com.elastisys.scale.cloudpool.google.compute.driver;

import static com.elastisys.scale.commons.util.precond.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.elastisys.scale.cloudpool.commons.basepool.driver.DriverConfig;
import com.elastisys.scale.cloudpool.google.commons.utils.InstanceGroupUrl;
import com.elastisys.scale.cloudpool.google.commons.utils.InstanceTemplateUrl;
import com.elastisys.scale.cloudpool.google.commons.utils.InstanceUrl;
import com.elastisys.scale.cloudpool.google.compute.driver.config.ProvisioningTemplate;
import com.elastisys.scale.commons.util.time.UtcTime;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceGroupManager;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.ManagedInstance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;

/**
 * Captures a (fake) snapshot of a single-zone instance group.
 */
public class FakeSingleZoneInstanceGroup {

    private final DriverConfig driverConfig;

    public static final String BASE_INSTANCE_NAME = "webserver";
    public static final String MACHINE_TYPE_NAME = "n1-standard-1";

    /** Sample instance template short name. */
    public static final String INSTANCE_TEMPLATE_NAME = "webserver-template";
    private final int targetSize;
    private final int numRunning;

    public FakeSingleZoneInstanceGroup(DriverConfig driverConfig, int targetSize, int numRunning) {
        this.driverConfig = driverConfig;
        this.targetSize = targetSize;
        this.numRunning = numRunning;

        checkArgument(provisioningTemplate().isSingleZoneGroup(), "provisioningTemplate must specify a single zone");
    }

    /**
     * Returns (fake) metadata about the instance group (manager).
     * <p/>
     * Note: only a subset of the group attributes are set (the ones needed by
     * the {@link GoogleComputeEnginePoolDriver}.
     *
     * @return
     */
    public InstanceGroupManager instanceGroupManager() {

        String instanceTemplateUrl = InstanceTemplateUrl.from(project(), INSTANCE_TEMPLATE_NAME).getUrl();
        String instanceGroupUrl = InstanceGroupUrl
                .managedZonal(project(), zone(), provisioningTemplate().getInstanceGroup()).getUrl();
        return new InstanceGroupManager().setName(provisioningTemplate().getInstanceGroup())
                .setBaseInstanceName(BASE_INSTANCE_NAME).setTargetSize(this.targetSize)
                .setInstanceGroup(instanceGroupUrl).setInstanceTemplate(instanceTemplateUrl);
    }

    /**
     * Returns the {@link ManagedInstance} representations of the
     * {@link Instance}s in the group.
     * <p/>
     * Note: only a subset of the instance attributes are set (the ones needed
     * by the {@link GoogleComputeEnginePoolDriver}.
     *
     * @return
     */
    public List<ManagedInstance> managedInstances() {
        List<ManagedInstance> managedInstances = new ArrayList<>(this.numRunning);
        for (Instance instance : instances()) {
            managedInstances.add(
                    new ManagedInstance().setInstance(instance.getSelfLink()).setInstanceStatus(instance.getStatus()));
        }
        return managedInstances;
    }

    /**
     * Returns the started {@link Instance}s in the (fake) instance group.
     *
     * @return
     */
    public List<Instance> instances() {
        List<Instance> memberInstances = new ArrayList<>();
        for (int i = 1; i <= this.numRunning; i++) {
            String privateIp = "10.0.0." + i;
            String publicIp = "192.100.100." + i;
            memberInstances.add(instance(BASE_INSTANCE_NAME + "-" + i, "RUNNING", privateIp, publicIp));
        }
        return memberInstances;
    }

    /**
     * Returns the (fake) {@link InstanceTemplate} from which the group members
     * are created.
     * <p/>
     * Note: only a subset of the template attributes are set (the ones needed
     * by the {@link GoogleComputeEnginePoolDriver}.
     *
     * @return
     */
    public InstanceTemplate instanceTemplate() {
        return new InstanceTemplate().setName(INSTANCE_TEMPLATE_NAME)
                .setProperties(new InstanceProperties().setMachineType(MACHINE_TYPE_NAME));
    }

    public String instanceTemplateName() {
        return INSTANCE_TEMPLATE_NAME;
    }

    private Instance instance(String name, String status, String privateIp, String publicIp) {
        List<NetworkInterface> networkInterfaces = Arrays.asList(new NetworkInterface().setName("nic0")
                .setNetworkIP(privateIp).setAccessConfigs(Arrays.asList(new AccessConfig().setNatIP(publicIp))));
        return new Instance().setName(name).setStatus(status).setZone(zoneUrl())
                .setCreationTimestamp(UtcTime.parse("2017-01-01T12:00:00.000Z").toString()).setKind("compute#instance")
                .setMachineType(machineTypeUrl("n1-standard-1")).setNetworkInterfaces(networkInterfaces)
                .setSelfLink(instanceUrl(name))//
                .setMetadata(new Metadata().setItems(new ArrayList<>()));
    }

    private String machineTypeUrl(String machineTypeName) {
        return String.format("https://www.googleapis.com/compute/v1/projects/%s/zones/%s/machineTypes/%s", project(),
                zone(), machineTypeName);
    }

    private String zoneUrl() {
        return String.format("https://www.googleapis.com/compute/v1/projects/%s/zones/%s", project(), zone());
    }

    private String instanceUrl(String instanceShortName) {
        return InstanceUrl.from(project(), zone(), instanceShortName).getUrl();
    }

    private String project() {
        return provisioningTemplate().getProject();
    }

    private String zone() {
        return provisioningTemplate().getZone();
    }

    private ProvisioningTemplate provisioningTemplate() {
        return this.driverConfig.parseProvisioningTemplate(ProvisioningTemplate.class);
    }

}
