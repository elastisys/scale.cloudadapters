package com.elastisys.scale.cloudpool.api.server;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.jetty.server.Server;
import org.junit.Before;
import org.junit.Test;

import com.elastisys.scale.cloudpool.api.CloudPool;
import com.elastisys.scale.cloudpool.api.restapi.impl.CloudPoolRestApiImpl;
import com.elastisys.scale.cloudpool.api.types.CloudPoolStatus;
import com.elastisys.scale.commons.json.JsonUtils;
import com.elastisys.scale.commons.net.host.HostUtils;
import com.elastisys.scale.commons.rest.client.RestClients;
import com.elastisys.scale.commons.util.file.FileUtils;
import com.elastisys.scale.commons.util.io.Resources;
import com.google.gson.JsonObject;

/**
 * Exercises the configuration handling of the {@link CloudPoolServer} on
 * startups with and without an existing server configuration to be restored.
 */
public class TestCloudPoolServerStartup {
    private static final String SERVER_KEYSTORE = Resources.getResource("security/server/server_keystore.p12")
            .toString();
    private static final String SERVER_KEYSTORE_PASSWORD = "serverpass";

    /** Web server to use throughout the tests. */
    private Server server;
    /** Server port to use for HTTPS. */
    private int httpsPort;
    private final static String storageDir = "target/test/cloudpool/storage";

    /** {@link CloudPool} that only supports configuration operations. */
    private CloudPool cloudPool = new ConfigurableCloudPoolStub();

    @Before
    public void beforeTestMethod() throws IOException {
        FileUtils.deleteRecursively(new File(storageDir));
    }

    @Before
    public void onSetup() throws Exception {
        List<Integer> freePorts = HostUtils.findFreePorts(1);
        this.httpsPort = freePorts.get(0);

    }

    /**
     * Make sure an explicitly specified config file gets set and is also saved.
     */
    @Test
    public void startServerWithExplicitConfig() throws Exception {
        CloudPoolOptions options = basicServerOptions();

        File configFile = new File(storageDir, "explicitconf.json");
        JsonObject config = JsonUtils.parseJsonString("{\"key\": \"value\"}").getAsJsonObject();
        saveConfig(JsonUtils.toString(config), configFile);
        options.config = configFile.getAbsolutePath();

        this.server = CloudPoolServer.createServer(this.cloudPool, options);
        try {
            this.server.start();
            // verify that explicitly set config was indeed set
            Client client = RestClients.httpsNoAuth();
            Response response = client.target(url("/config")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(JsonObject.class), is(config));

            // by default (without --stopped flag), cloudpool is to be started
            response = client.target(url("/status")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(CloudPoolStatus.class), is(new CloudPoolStatus(true, true)));
        } finally {
            this.server.stop();
            this.server.join();
        }
    }

    /**
     * If no configuration is explicitly specified, the server should load any
     * saved configuration from the storage directory.
     */
    @Test
    public void startServerWithSavedConfig() throws Exception {
        File savedConfigFile = new File(storageDir, CloudPoolRestApiImpl.DEFAULT_CONFIG_FILE_NAME);
        JsonObject config = JsonUtils.parseJsonString("{\"key1\": \"value1\"}").getAsJsonObject();
        saveConfig(JsonUtils.toString(config), savedConfigFile);

        // no explicit config given. config will be restored from storage
        // directory.
        assertTrue(savedConfigFile.isFile());
        CloudPoolOptions options = basicServerOptions();
        this.server = CloudPoolServer.createServer(this.cloudPool, options);
        try {
            this.server.start();
            // verify that config was loaded from storage directory
            Client client = RestClients.httpsNoAuth();
            Response response = client.target(url("/config")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(JsonObject.class), is(config));

            // by default (without --stopped flag), cloudpool is to be started
            response = client.target(url("/status")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(CloudPoolStatus.class), is(new CloudPoolStatus(true, true)));
        } finally {
            this.server.stop();
            this.server.join();
        }
    }

    /**
     * When started with {@code --stopped} flag, the {@link CloudPool} should be
     * in a stopped state.
     */
    @Test
    public void startServerWithExplicitConfigInStoppedState() throws Exception {
        CloudPoolOptions options = basicServerOptions();

        File configFile = new File(storageDir, "explicitconf.json");
        JsonObject config = JsonUtils.parseJsonString("{\"key\": \"value\"}").getAsJsonObject();
        saveConfig(JsonUtils.toString(config), configFile);
        options.config = configFile.getAbsolutePath();
        // set --stopped
        options.stopped = true;

        this.server = CloudPoolServer.createServer(this.cloudPool, options);
        try {
            this.server.start();
            // verify that explicitly set config was indeed set
            Client client = RestClients.httpsNoAuth();
            Response response = client.target(url("/config")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(JsonObject.class), is(config));

            // with --stopped flag, cloudpool should be stopped
            response = client.target(url("/status")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(CloudPoolStatus.class), is(new CloudPoolStatus(false, true)));
        } finally {
            this.server.stop();
            this.server.join();
        }
    }

    /**
     * When started with {@code --stopped} flag, the {@link CloudPool} should be
     * in a stopped state.
     */
    @Test
    public void startServerWithSavedConfigInStoppedState() throws Exception {
        CloudPoolOptions options = basicServerOptions();

        File configFile = new File(storageDir, "explicitconf.json");
        JsonObject config = JsonUtils.parseJsonString("{\"key\": \"value\"}").getAsJsonObject();
        saveConfig(JsonUtils.toString(config), configFile);
        options.config = configFile.getAbsolutePath();
        // set --stopped
        options.stopped = true;

        this.server = CloudPoolServer.createServer(this.cloudPool, options);
        try {
            this.server.start();
            // verify that explicitly set config was indeed set
            Client client = RestClients.httpsNoAuth();
            Response response = client.target(url("/config")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(JsonObject.class), is(config));

            // with --stopped flag, cloudpool should be stopped
            response = client.target(url("/status")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(CloudPoolStatus.class), is(new CloudPoolStatus(false, true)));
        } finally {
            this.server.stop();
            this.server.join();
        }
    }

    /**
     * If no configuration is explicitly specified, the server should load any
     * saved configuration from the storage directory.
     */
    @Test
    public void startServerWithoutSavedConfig() throws Exception {
        assertFalse(new File(storageDir, CloudPoolRestApiImpl.DEFAULT_CONFIG_FILE_NAME).isFile());
        // no explicit config given and no config available from storage dir.
        CloudPoolOptions options = basicServerOptions();
        this.server = CloudPoolServer.createServer(this.cloudPool, options);
        try {
            this.server.start();
            // verify that no config has been set (yet)
            Client client = RestClients.httpsNoAuth();
            Response response = client.target(url("/config")).request().get();
            assertThat(response.getStatus(), is(Status.NOT_FOUND.getStatusCode()));

            // without a config, cloudpool is expected to be in a stopped state
            response = client.target(url("/status")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(CloudPoolStatus.class), is(new CloudPoolStatus(false, false)));
        } finally {
            this.server.stop();
            this.server.join();
        }
    }

    /**
     * Verify that setting the config results in it being saved, and that it is
     * reloaded on server restart.
     */
    @Test
    public void verifyThatSetConfigIsReloadedOnRestart() throws Exception {
        assertFalse(new File(storageDir, CloudPoolRestApiImpl.DEFAULT_CONFIG_FILE_NAME).isFile());
        // no explicit config given and no config available from storage dir.
        CloudPoolOptions options = basicServerOptions();
        this.server = CloudPoolServer.createServer(this.cloudPool, options);
        try {
            this.server.start();
            // verify that no config has been set (yet)
            Client client = RestClients.httpsNoAuth();
            Response response = client.target(url("/config")).request().get();
            assertThat(response.getStatus(), is(Status.NOT_FOUND.getStatusCode()));

            // set config
            JsonObject config = JsonUtils.parseJsonString("{\"k\": \"v\"}").getAsJsonObject();
            response = client.target(url("/config")).request(MediaType.APPLICATION_JSON).post(Entity.json(config));
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));

            // stop server
            this.server.stop();
            this.server.join();

            // restart server
            this.server = CloudPoolServer.createServer(this.cloudPool, options);
            this.server.start();

            // verify that previously set config was remembered on restart
            response = client.target(url("/config")).request().get();
            assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
            assertThat(response.readEntity(JsonObject.class), is(config));
        } finally {
            this.server.stop();
            this.server.join();
        }
    }

    private CloudPoolOptions basicServerOptions() {
        CloudPoolOptions options = new CloudPoolOptions();
        options.httpsPort = this.httpsPort;
        options.sslKeyStore = SERVER_KEYSTORE;
        options.sslKeyStorePassword = SERVER_KEYSTORE_PASSWORD;
        options.requireClientCert = false;
        options.storageDir = storageDir;
        return options;
    }

    private void saveConfig(String jsonConfig, File destination) throws IOException {
        destination.getParentFile().mkdirs();
        Files.write(destination.toPath(), jsonConfig.getBytes());
    }

    /**
     * URL to do a {@code GET /<path>} request.
     *
     * @param path
     *            The resource path on the remote server.
     * @return
     */
    private String url(String path) {
        return String.format("https://localhost:%d%s", this.httpsPort, path);
    }
}
