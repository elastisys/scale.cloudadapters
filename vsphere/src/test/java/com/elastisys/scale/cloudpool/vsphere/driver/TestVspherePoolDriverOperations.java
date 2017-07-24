package com.elastisys.scale.cloudpool.vsphere.driver;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.hamcrest.CoreMatchers.is;

import java.rmi.RemoteException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.elastisys.scale.cloudpool.api.types.Machine;
import com.elastisys.scale.cloudpool.commons.basepool.driver.DriverConfig;
import com.elastisys.scale.cloudpool.vsphere.client.VsphereClient;
import com.elastisys.scale.cloudpool.vsphere.driver.MachinesMatcher;
import com.elastisys.scale.commons.json.JsonUtils;
import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.ManagedEntityStatus;
import com.vmware.vim25.mo.VirtualMachine;

import jersey.repackaged.com.google.common.collect.Lists;

public class TestVspherePoolDriverOperations {

    private VspherePoolDriver driver;
    private VsphereClient mockedClient = mock(VsphereClient.class);

    @Before
    public void setup() {
        String specificConfigPath = "config/valid-vsphere-config.json";
        DriverConfig configuration = JsonUtils.toObject(JsonUtils.parseJsonResource(specificConfigPath), DriverConfig.class);
        driver = new VspherePoolDriver(mockedClient);
        driver.configure(configuration);
    }

    @Test
    public void testVspherePoolDriver() {
        fail("Not yet implemented");
    }

    @Test
    public void emptyListOfMachines() {
        assertTrue(driver.listMachines().isEmpty());
    }

    @Test
    public void listSingleMachine() throws RemoteException {
        String name = "vmName";
        VirtualMachine vm = getMockedVM(name);
        when(vm.getName()).thenReturn(name);

        List<VirtualMachine> vms = Lists.newArrayList();
        vms.add(vm);
        when(mockedClient.getVirtualMachines(any())).thenReturn(vms);

        List<Machine> result = driver.listMachines();
        assertEquals(1, result.size());
        assertThat(result, is(MachinesMatcher.machines(name)));
    }

    @Test
    public void testStartMachines() {
        fail("Not yet implemented");
    }

    @Test
    public void testTerminateMachine() {
        fail("Not yet implemented");
    }

    @Test
    public void testAttachMachine() {
        fail("Not yet implemented");
    }

    @Test
    public void testDetachMachine() {
        fail("Not yet implemented");
    }

    @Test
    public void testSetServiceState() {
        fail("Not yet implemented");
    }

    @Test
    public void testSetMembershipStatus() {
        fail("Not yet implemented");
    }

    @Test
    public void testGetPoolName() {
        fail("Not yet implemented");
    }

    private VirtualMachine getMockedVM(String name) {
        GuestInfo guestInfo = mock(GuestInfo.class);

        VirtualMachine vm = mock(VirtualMachine.class);
        when(vm.getName()).thenReturn(name);
        when(vm.getGuest()).thenReturn(guestInfo);
        when(guestInfo.getGuestState()).thenReturn("running");
        when(vm.getGuestHeartbeatStatus()).thenReturn(ManagedEntityStatus.green);
        return vm;
    }
}
