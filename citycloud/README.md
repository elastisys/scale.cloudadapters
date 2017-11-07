# CityCloud cloud pool

The [elastisys](http://elastisys.com/) CityCloud 
[cloud pool](http://cloudpoolrestapi.readthedocs.org/en/latest/)
manages a pool of [CityCloud](https://www.citycloud.com/) servers. 

As it makes use of CityCloud's OpenStack API, it is implemented as a 
very thin wrapper over the [OpenStack cloudpool](../openstack/README.md).
Refer to it for more detailed documentation.

Also **note** that CityCloud makes use of Keystone version 3 for 
authentication, so the `cloudApiSettings` part of the configuration needs to look
something like:

```javascript
    "cloudApiSettings": {
        "auth": {
            "keystoneUrl": "https://identity1.citycloud.com:5000/v3/",
            "v3Credentials": {
                "userName": "foo",
                "userDomainName": "CCP_Domain_12345",
                "password": "secret",
                "projectName": "myproject",
                "projectDomainName": "CCP_Domain_12345"
            }
        },
        "region": "Kna1",
        "connectionTimeout": 10000,
        "socketTimeout": 10000
    },
```

The domain, available users, and projects of your account can be found in
the API access part of the [CityCloud dashboard](https://citycontrolpanel.com/openstack#openstack_api_access).

The `region` is one of the available CityCloud regions (for example, `Kna1` for
Karlskrona/Sweden) . The available regions, with their short-codes, are
listed [here](https://citycontrolpanel.com/openstack#apiEndpoints) under the
`Endpoints` tab.



## Running the cloud pool in a Docker container
The cloud pool can be executed inside a 
[Docker](https://www.docker.com/) container. First, however, a docker image 
needs to be built that includes the cloud pool. The steps for building 
the image and running a container from the image are outlined below.

Before proceeding, make sure that your user is a member of the `docker` user group.
Without being a member of that user group, you won't be able to use docker without
 sudo/root privileges. 

See the [docker documentation](https://docs.docker.com/installation/ubuntulinux/#giving-non-root-access) 
for more details.



### Building the docker image
The module's build file contains a build goal that can be used to produce a
Docker image, once the project binary has been built in the `target`
directory (for example, via `mvn package`). Whenever `mvn install` is
executed the Docker image gets built locally on the machine. When
`mvn deploy` is run, such as during a release, the image also gets
pushed to our private docker registry.

*Note: make sure that you have issued `docker login` against the docker registry before trying to push an image.*



### Running a container from the image
Once the docker image is built for the server, it can be run by either 
specfying a HTTP port or an HTTPS port. For example, running with an HTTP port:

    docker run -d -p 8080:80 -e HTTP_PORT=80 <image>

This will start publish the container's HTTP port on host port `8080`.

*Note: for production settings, it is recommended to run the server with an HTTPS port.*

The following environment variables can be passed to the Docker container (`-e`)
to control its behavior. At least one of `${HTTP_PORT}` and `${HTTPS_PORT}` 
_must_ be specified.

Singleton/multipool mode:

  - `MULTIPOOL`: Set to `true` to start the server in [multipool](../multipool/README.md)-mode, 
    in which it will publish a dynamic collection of *cloudpool instances*.
    The default is to run the server as a singleton cloudpool.


HTTP/HTTPS configuration:

  - `HTTP_PORT`: Enables a HTTP port on the server.  

  - `HTTPS_PORT`: Enables a HTTPS port on the server.  
    *Note: when specified, a `${SSL_KEYSTORE}` must be specified to identify to clients.*
	
  - `SSL_KEYSTORE`: The location of the server's SSL key store (PKCS12 format).  
     You typically combine this with mounting a volume that holds the key store.  
	 *Note: when specified, an `${SSL_KEYSTORE_PASSWORD}` must is required.*
	 
  - `SSL_KEYSTORE_PASSWORD`: The password that protects the key store.  

Runtime configuration:

  - `STORAGE_DIR`: destination folder for runtime state.  
    *Note: to persist across container recreation, this directory should be 
	mapped via a volume to a directory on the host.*  
    Default: `/var/lib/elastisys/citycloudpool`.


Debug-related:

  - `LOG_CONFIG`: [logback](http://logback.qos.ch/manual/configuration.html)
    logging configuration file (`logback.xml`).  
    Default: `/etc/elastisys/citycloudpool/logback.xml`.
  - `JUL_CONFIG`: `java.util.logging` `logging.properties` configuration.  
    Default: `/etc/elastisys/citycloudpool/logging.properties`.
  - `LOG_DIR`: destination folder for log files (when using default
    `${LOG_CONFIG}` setup).  
    Default: `/var/log/elastisys/citycloudpool`.
  - `STDOUT_LOG_LEVEL`: output level for logging to stdout (note: log output 
    that is written to file includes `DEBUG` level).  
    Default: `INFO`.

Client authentication:

  - `REQUIRE_BASIC_AUTH`: If `true`, require clients to provide username/password
    credentials according to the HTTP BASIC authentication scheme.  
    *Note: when specified, `${BASIC_AUTH_REALM_FILE}` and `${BASIC_AUTH_ROLE}` must be specified to identify trusted clients.*  
	Default: `false`.
  - `BASIC_AUTH_ROLE`: The role that an authenticated user must be assigned to be granted access to the server.  
  - `BASIC_AUTH_REALM_FILE`: A credentials store with users, passwords, and
    roles according to the format prescribed by the [Jetty HashLoginService](http://www.eclipse.org/jetty/documentation/9.2.6.v20141205/configuring-security-authentication.html#configuring-login-service).  
  - `REQUIRE_CERT_AUTH`: Require SSL clients to authenticate with a certificate,
    which must be included in the server's trust store.  
    *Note: when specified, `${CERT_AUTH_TRUSTSTORE}` and `${CERT_AUTH_TRUSTSTORE_PASSWORD}` must be specified to identify trusted clients.*  	
  - `CERT_AUTH_TRUSTSTORE`. The location of a SSL trust store (JKS format), containing trusted client certificates.
  - `CERT_AUTH_TRUSTSTORE_PASSWORD`: The password that protects the SSL trust store.

JVM-related:

  - `JVM_OPTS`: JVM settings such as heap size. Default: `-Xmx128m`.




### Debugging a running container
The simplest way to debug a running container is to get a shell session via
  
    docker exec -it <container-id/name> /bin/bash

and check out the log files under `/var/log/elastisys`. Configurations are
located under `/etc/elastisys` and binaries under `/opt/elastisys`.



## Interacting with the cloud pool over its REST API
The following examples, all using the [curl](http://en.wikipedia.org/wiki/CURL) 
command-line tool, shows how to interact with the cloud pool over its
[REST API](http://cloudpoolrestapi.readthedocs.org/en/latest/).

The exact command-line arguments to pass to curl depends on the security
settings that the server was launched with. For example, if client-certificate
authentication is enforced (`--require-cert`), one needs to pass client 
certificate credentials via `curl`: 

    --key-type pem --key key.pem --cert-type pem --cert cert.pem

Here are some examples illustrating basic interactions with the cloud pool
(these assume that the server was started on a HTTP port):


1. Retrieve the currently set configuration document:

        curl -X GET http://localhost:8080/config
    

2. Set configuration:

        curl --header "Content-Type:application/json" \
                    -X POST -d @myconfig.json http://localhost:8080/config


3. Start:

        curl -X POST http://localhost:8080/start


4. Retrieve the current machine pool:

        curl -X POST http://localhost:8080/pool

5. Request the machine pool to be resized to size ``4``:

        curl --header "Content-Type:application/json" \
                    -X POST -d '{"desiredCapacity": 4}' http://localhost:8080/config
