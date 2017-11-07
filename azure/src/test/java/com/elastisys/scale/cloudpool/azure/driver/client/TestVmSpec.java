package com.elastisys.scale.cloudpool.azure.driver.client;

import static com.elastisys.scale.cloudpool.azure.driver.config.TestUtils.invalidLinuxSettings;
import static com.elastisys.scale.cloudpool.azure.driver.config.TestUtils.invalidNetworkSettings;
import static com.elastisys.scale.cloudpool.azure.driver.config.TestUtils.invalidWindowsSettings;
import static com.elastisys.scale.cloudpool.azure.driver.config.TestUtils.validLinuxSettings;
import static com.elastisys.scale.cloudpool.azure.driver.config.TestUtils.validWindowsSettings;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.Test;

import com.elastisys.scale.cloudpool.azure.driver.Constants;
import com.elastisys.scale.cloudpool.azure.driver.config.LinuxSettings;
import com.elastisys.scale.cloudpool.azure.driver.config.NetworkSettings;
import com.elastisys.scale.cloudpool.azure.driver.config.TestUtils;
import com.elastisys.scale.cloudpool.azure.driver.config.WindowsSettings;
import com.google.common.collect.ImmutableMap;
import com.microsoft.azure.management.compute.StorageAccountTypes;

/**
 * Exercise {@link VmSpec}
 *
 */
public class TestVmSpec {

    private static final NetworkSettings VM_NETWORK = TestUtils.validNetworkSettings();
    private static final String VM_NAME = "vm-0";
    private static final VmImage VM_IMAGE_REF = new VmImage("Canonical:UbuntuServer:16.04.0-LTS:latest");
    private static final VmImage VM_IMAGE_ID = new VmImage(
            "subscriptions/123/resourceGroups/rg/providers/Microsoft.Compute/images/ubuntu");
    private static final String VM_SIZE = "Standard_DS1_v2";
    private static final StorageAccountTypes OS_DISK_TYPE = StorageAccountTypes.PREMIUM_LRS;
    private static final String AVAILABILITY_SET = "availabiltiy-set";
    private static final Map<String, String> VM_TAGS = ImmutableMap.of(//
            Constants.CLOUD_POOL_TAG, "scaling-pool", //
            "tier", "web");

    /**
     * Specify all fields.
     */
    @Test
    public void completeSpec() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();

        assertThat(vmSpec.getVmSize(), is(VM_SIZE));
        assertThat(vmSpec.getVmImage(), is(VM_IMAGE_REF));
        assertThat(vmSpec.getOsDiskType(), is(OS_DISK_TYPE));
        assertThat(vmSpec.getVmName(), is(VM_NAME));
        assertThat(vmSpec.getLinuxSettings(), is(linuxSettings));
        assertThat(vmSpec.getWindowsSettings().isPresent(), is(false));
        assertThat(vmSpec.getNetwork(), is(VM_NETWORK));
        assertThat(vmSpec.getAvailabilitySet().get(), is(AVAILABILITY_SET));
        assertThat(vmSpec.getTags(), is(VM_TAGS));

        // create with vm image id
        vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_ID, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings, VM_NETWORK,
                AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();
        assertThat(vmSpec.getVmImage(), is(VM_IMAGE_ID));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingVmSize() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(null, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingVmImage() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, null, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings, VM_NETWORK,
                AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingOsDiskType() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, null, VM_NAME, linuxSettings, windowsSettings, VM_NETWORK,
                AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingVmName() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, null, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void onNullLinuxSettings() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = null;

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void onNullWindowsSettings() {
        Optional<WindowsSettings> windowsSettings = null;
        Optional<LinuxSettings> linuxSettings = Optional.empty();

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingLinuxAndWindowsSettings() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.empty();

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();

    }

    @Test(expected = IllegalArgumentException.class)
    public void bothLinuxAndWindowsSettings() {
        Optional<WindowsSettings> windowsSettings = Optional.of(validWindowsSettings());
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();

    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalLinuxSettings() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(invalidLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();

    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalWindowsSettings() {
        Optional<WindowsSettings> windowsSettings = Optional.of(invalidWindowsSettings());
        Optional<LinuxSettings> linuxSettings = Optional.empty();

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();

    }

    @Test(expected = IllegalArgumentException.class)
    public void missingNetwork() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings, null,
                AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();

    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalNetwork() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                invalidNetworkSettings(), AVAILABILITY_SET, VM_TAGS);
        vmSpec.validate();

    }

    @Test(expected = IllegalArgumentException.class)
    public void missingTags() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, null);
        vmSpec.validate();
    }

    /**
     * It is optional to specify an availability set for VMs.
     */
    @Test
    public void missingAvailabilitySet() {
        String nullAvailabilitySet = null;
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, nullAvailabilitySet, VM_TAGS);
        vmSpec.validate();
    }

    @Test(expected = IllegalArgumentException.class)
    public void illegalTags() {
        Optional<WindowsSettings> windowsSettings = Optional.empty();
        Optional<LinuxSettings> linuxSettings = Optional.of(validLinuxSettings());

        Map<String, String> invalidTags = ImmutableMap.of("a/key", "value");
        VmSpec vmSpec = new VmSpec(VM_SIZE, VM_IMAGE_REF, OS_DISK_TYPE, VM_NAME, linuxSettings, windowsSettings,
                VM_NETWORK, AVAILABILITY_SET, invalidTags);
        vmSpec.validate();

    }

}
