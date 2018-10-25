package com.elastisys.scale.cloudpool.api.server;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Paths;
import java.util.List;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.jetty.server.Server;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.elastisys.scale.cloudpool.api.CloudPool;
import com.elastisys.scale.cloudpool.api.types.CloudPoolStatus;
import com.elastisys.scale.cloudpool.api.types.MachinePool;
import com.elastisys.scale.commons.net.host.HostUtils;
import com.elastisys.scale.commons.net.ssl.KeyStoreType;
import com.elastisys.scale.commons.rest.client.RestClients;
import com.elastisys.scale.commons.util.io.Resources;
import com.elastisys.scale.commons.util.time.UtcTime;
import com.google.gson.JsonObject;

/**
 * Verifies the behavior of the {@link CloudPoolServer} when executed without
 * requiring any client authentication at all.
 */
public class TestRestApiSecurityNoAuth {

    private static final String SERVER_KEYSTORE = Resources.getResource("security/server/server_keystore.p12")
            .toString();
    private static final String SERVER_KEYSTORE_PASSWORD = "serverpass";

    /** Web server to use throughout the tests. */
    private static Server server;
    /** Server port to use for HTTPS. */
    private static int httpsPort;
    /** Storage dir for configurations. */
    private static final String storageDir = Paths.get("target", "cloudpool", "storage").toString();

    private static CloudPool cloudPool = mock(CloudPool.class);

    @BeforeClass
    public static void onSetup() throws Exception {
        List<Integer> freePorts = HostUtils.findFreePorts(1);
        httpsPort = freePorts.get(0);

        // cloudpool is configured and started
        CloudPoolStatus startedStatus = new CloudPoolStatus(true, true);
        when(cloudPool.getStatus()).thenReturn(startedStatus);
        // set up mocked cloud pool that will back the created server
        when(cloudPool.getMachinePool()).thenReturn(MachinePool.emptyPool(UtcTime.now()));

        CloudPoolOptions options = new CloudPoolOptions();
        options.httpsPort = httpsPort;
        options.sslKeyStore = SERVER_KEYSTORE;
        options.sslKeyStorePassword = SERVER_KEYSTORE_PASSWORD;
        options.requireClientCert = false;
        options.storageDir = storageDir;

        server = CloudPoolServer.createServer(cloudPool, options);
        server.start();
    }

    @AfterClass
    public static void onTeardown() throws Exception {
        server.stop();
        server.join();
    }

    /**
     * Test connecting with clients using different authentication mechanisms.
     * All should succeed, since the server doesn't care about client
     * authentication.
     */
    @Test
    public void testConnect() {
        Client noAuthClient = RestClients.httpsNoAuth();
        Response response = noAuthClient.target(getUrl()).request().get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertNotNull(response.readEntity(JsonObject.class));

        Client basicAuthClient = RestClients.httpsBasicAuth("admin", "adminpassword");
        response = basicAuthClient.target(getUrl()).request().get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertNotNull(response.readEntity(JsonObject.class));

        Client certAuthClient = RestTestUtils.httpsCertAuth("src/test/resources/security/client/client_keystore.p12",
                "clientpass", KeyStoreType.PKCS12);
        response = certAuthClient.target(getUrl()).request().get();
        assertThat(response.getStatus(), is(Status.OK.getStatusCode()));
        assertNotNull(response.readEntity(JsonObject.class));
    }

    /**
     * URL to do a {@code GET /pool} request.
     *
     * @return
     */
    private static String getUrl() {
        return String.format("https://localhost:%d/pool", httpsPort);
    }

}
