package com.elastisys.scale.cloudpool.vsphere;

import com.elastisys.scale.cloudpool.commons.basepool.driver.DriverConfig;
import com.elastisys.scale.cloudpool.vsphere.client.VsphereClient;
import com.elastisys.scale.cloudpool.vsphere.client.impl.StandardVsphereClient;
import com.elastisys.scale.cloudpool.vsphere.client.tag.Tag;
import com.elastisys.scale.cloudpool.vsphere.client.tag.Tagger;
import com.elastisys.scale.cloudpool.vsphere.client.tag.impl.CustomAttributeTagger;
import com.elastisys.scale.cloudpool.vsphere.driver.config.VsphereApiSettings;
import com.elastisys.scale.cloudpool.vsphere.driver.config.VsphereProvisioningTemplate;
import com.elastisys.scale.commons.json.JsonUtils;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.mo.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.rmi.RemoteException;
import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ITVsphere {

    private static DriverConfig driverConfig;
    private static VsphereApiSettings vsphereApiSettings;
    private static VsphereProvisioningTemplate vsphereProvisioningTemplate;

    private static ServiceInstance serviceInstance;
    private static VirtualMachine minimalVm;

    private static Tagger tagger;

    @BeforeClass
    public static void setup() throws Exception {
        driverConfig = JsonUtils.toObject(JsonUtils.parseJsonResource("VcenterInfo.json"), DriverConfig.class);
        vsphereApiSettings = driverConfig.parseCloudApiSettings(VsphereApiSettings.class);
        vsphereProvisioningTemplate = driverConfig.parseProvisioningTemplate(VsphereProvisioningTemplate.class);
        serviceInstance = new ServiceInstance(vsphereApiSettings.getUrl(), vsphereApiSettings.getUsername(),
                vsphereApiSettings.getPassword(), true);

        Folder root = serviceInstance.getRootFolder();
        VirtualMachine template = (VirtualMachine) new InventoryNavigator(root).searchManagedEntity("VirtualMachine",
                vsphereProvisioningTemplate.getTemplate());
        VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();
        ResourcePool pool = (ResourcePool) new InventoryNavigator(root)
                .searchManagedEntity(ResourcePool.class.getSimpleName(), "Resources");
        VirtualMachineRelocateSpec relocateSpec = new VirtualMachineRelocateSpec();
        relocateSpec.setPool(pool.getMOR());
        cloneSpec.setPowerOn(false);
        cloneSpec.setTemplate(false);
        cloneSpec.setLocation(relocateSpec);
        String cloneName = "integration-test-" + UUID.randomUUID().toString();
        Task task = template.cloneVM_Task((Folder) template.getParent(), cloneName, cloneSpec);
        task.waitForTask();
        if (task.getTaskInfo().getState().equals(Task.SUCCESS)) {
            throw new Exception();
        }
        minimalVm = (VirtualMachine) new InventoryNavigator(root).searchManagedEntity("VirtualMachine", cloneName);

        tagger = new CustomAttributeTagger();
        tagger.initialize(serviceInstance);
    }

    @AfterClass
    public static void teardown() throws Exception {
        Task task = minimalVm.destroy_Task();
        task.waitForTask();
        serviceInstance.getServerConnection().logout();
    }

    @Test
    public void taggedResourceShouldBeTagged() throws RemoteException {
        tagger.tag(minimalVm, Tag.CLOUD_POOL);
        assertTrue(tagger.isTagged(minimalVm, Tag.CLOUD_POOL));
        tagger.untag(minimalVm, Tag.CLOUD_POOL);
        assertFalse(tagger.isTagged(minimalVm, Tag.CLOUD_POOL));
    }

    @Test
    public void clientConfigurationShouldSucceed() throws RemoteException{
        VsphereClient vsphereClient = new StandardVsphereClient();
        vsphereClient.configure(vsphereApiSettings, vsphereProvisioningTemplate);
    }

}
