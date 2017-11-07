package com.elastisys.scale.cloudpool.api.server;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

import javax.ws.rs.client.Client;

import org.glassfish.jersey.logging.LoggingFeature;

import com.elastisys.scale.commons.net.ssl.KeyStoreType;
import com.elastisys.scale.commons.rest.client.RestClients;

public class RestTestUtils {
    public static Client httpsCertAuth(String keyStorePath, String keyStorePassword, KeyStoreType keystoreType)
            throws RuntimeException {
        try (InputStream keyStoreStream = new FileInputStream(keyStorePath)) {
            KeyStore keystore = KeyStore.getInstance(keystoreType.name());
            keystore.load(keyStoreStream, keyStorePassword.toCharArray());
            Client client = RestClients.httpsCertAuth(keystore, keyStorePassword);
            client.register(new LoggingFeature());
            return client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
