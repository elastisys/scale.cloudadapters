package com.elastisys.scale.cloudpool.openstack.requests.lab;

import java.util.List;

import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.KeypairService;
import org.openstack4j.core.transport.internal.HttpLoggingFilter;
import org.openstack4j.model.compute.Keypair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.elastisys.scale.cloudpool.openstack.driver.config.CloudApiSettings;
import com.elastisys.scale.commons.openstack.OSClientFactory;

public class ListKeypairsMain {

    private static Logger LOG = LoggerFactory.getLogger(CreateServerMain.class);

    public static void main(String[] args) throws Exception {
        HttpLoggingFilter.toggleLogging(false);
        CloudApiSettings driverConfig = DriverConfigLoader.loadDefault();

        OSClient client = new OSClientFactory(driverConfig).authenticatedClient();
        KeypairService keyApi = client.compute().keypairs();

        listKeys(keyApi);
    }

    private static void listKeys(KeypairService keyApi) {
        List<? extends Keypair> keypairs = keyApi.list();
        LOG.info("found {} keypair(s)", keypairs.size());
        for (Keypair keypair : keypairs) {
            LOG.info("keypair: " + keypair);
        }
    }
}
