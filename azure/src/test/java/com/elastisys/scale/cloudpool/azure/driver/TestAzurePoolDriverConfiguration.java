package com.elastisys.scale.cloudpool.azure.driver;

import static com.elastisys.scale.cloudpool.azure.driver.AzureTestUtils.driverConfig;
import static com.elastisys.scale.cloudpool.azure.driver.AzureTestUtils.loadPoolConfig;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.Before;
import org.junit.Test;

import com.elastisys.scale.cloudpool.api.types.MembershipStatus;
import com.elastisys.scale.cloudpool.api.types.ServiceState;
import com.elastisys.scale.cloudpool.azure.driver.client.AzureClient;
import com.elastisys.scale.cloudpool.azure.driver.config.CloudApiSettings;
import com.elastisys.scale.cloudpool.azure.driver.config.ProvisioningTemplate;
import com.elastisys.scale.cloudpool.commons.basepool.config.BaseCloudPoolConfig;
import com.elastisys.scale.commons.json.JsonUtils;

/**
 * Exercises the configuration aspects of the {@link AzurePoolDriver}.
 */
public class TestAzurePoolDriverConfiguration {

    /** Mock azure client being used by driver. */
    private AzureClient clientMock = mock(AzureClient.class);
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);

    /** Object under test. */
    private AzurePoolDriver driver;

    @Before
    public void beforeTestMethod() {
        this.driver = new AzurePoolDriver(this.clientMock, this.executor);
    }

    /**
     * Ensure that a set configuration propagates to the {@link AzureClient} set
     * for the {@link AzurePoolDriver}.
     */
    @Test
    public void configure() {
        verifyZeroInteractions(this.clientMock);
        assertThat(this.driver.config(), is(nullValue()));

        BaseCloudPoolConfig poolConfig = loadPoolConfig("config/valid-config.json");
        this.driver.configure(driverConfig(poolConfig));

        CloudApiSettings expectedCloudApiSettings = JsonUtils.toObject(poolConfig.getCloudApiSettings(),
                CloudApiSettings.class);
        ProvisioningTemplate expectedProvisioningTemplate = JsonUtils.toObject(poolConfig.getProvisioningTemplate(),
                ProvisioningTemplate.class);

        // verify that config was set on driver
        assertThat(this.driver.cloudApiSettings(), is(expectedCloudApiSettings));
        assertThat(this.driver.provisioningTemplate(), is(expectedProvisioningTemplate));

        // verify that the cloud api settings were passed along to the client
        verify(this.clientMock).configure(expectedCloudApiSettings);
    }

    /**
     * When passed an illegal configuration, no part of the configuration should
     * be applied.
     */
    @Test
    public void onIllegalConfig() {
        try {
            this.driver.configure(driverConfig(loadPoolConfig("config/invalid-config.json")));
            fail("expected to fail config validation");
        } catch (IllegalArgumentException e) {
            // expected
        }

        assertThat(this.driver.config(), is(nullValue()));
        // configuration should not have been applied on client
        verifyZeroInteractions(this.clientMock);
    }

    @Test(expected = IllegalStateException.class)
    public void listMachinesBeforeConfigured() {
        this.driver.listMachines();
    }

    @Test(expected = IllegalStateException.class)
    public void attachgMachinesBeforeConfigured() {
        this.driver.attachMachine("id");
    }

    @Test(expected = IllegalStateException.class)
    public void detachMachinesBeforeConfigured() {
        this.driver.detachMachine("id");
    }

    @Test(expected = IllegalStateException.class)
    public void getPoolNameBeforeConfigured() {
        this.driver.getPoolName();
    }

    @Test(expected = IllegalStateException.class)
    public void setMembershipStatusBeforeConfigured() {
        this.driver.setMembershipStatus("id", MembershipStatus.blessed());
    }

    @Test(expected = IllegalStateException.class)
    public void setServiceStateBeforeConfigured() {
        this.driver.setServiceState("id", ServiceState.IN_SERVICE);
    }

    @Test(expected = IllegalStateException.class)
    public void startMachinesBeforeConfigured() {
        this.driver.startMachines(1);
    }

    @Test(expected = IllegalStateException.class)
    public void terminateMachineBeforeConfigured() {
        this.driver.terminateMachines(asList("id"));
    }

}
