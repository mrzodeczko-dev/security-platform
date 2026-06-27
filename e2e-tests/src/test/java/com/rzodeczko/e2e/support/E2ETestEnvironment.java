package com.rzodeczko.e2e.support;

import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

/**
 * Shared Testcontainers environment that starts the full Security Platform stack
 * using docker-compose.yml.  Singleton – started once for the entire test suite.
 */
public final class E2ETestEnvironment {

    private static final String GATEWAY_SERVICE = "api-gateway-service";
    private static final int GATEWAY_PORT = 8085;

    private static final String MAILHOG_SERVICE = "mailhog";
    private static final int MAILHOG_API_PORT = 8025;

    private static ComposeContainer COMPOSE;
    private static String gatewayBaseUrl;
    private static String mailhogApiUrl;

    private E2ETestEnvironment() {
    }

    public static synchronized void start() {
        if (gatewayBaseUrl != null) {
            return;
        }

        var composeFile = resolveComposeFile();

        var compose = new ComposeContainer(composeFile)
                .withExposedService(GATEWAY_SERVICE, GATEWAY_PORT,
                        Wait.forHttp("/actuator/health")
                                .forPort(GATEWAY_PORT)
                                .forStatusCode(200)
                                .withStartupTimeout(Duration.ofMinutes(5)))
                .withExposedService(MAILHOG_SERVICE, MAILHOG_API_PORT,
                        Wait.forListeningPort()
                                .withStartupTimeout(Duration.ofMinutes(2)))
                .withLocalCompose(true);

        compose.start();
        COMPOSE = compose;

        var gatewayHost = COMPOSE.getServiceHost(GATEWAY_SERVICE, GATEWAY_PORT);
        var gatewayPort = COMPOSE.getServicePort(GATEWAY_SERVICE, GATEWAY_PORT);
        gatewayBaseUrl = "http://" + gatewayHost + ":" + gatewayPort;

        var mailhogHost = COMPOSE.getServiceHost(MAILHOG_SERVICE, MAILHOG_API_PORT);
        var mailhogPort = COMPOSE.getServicePort(MAILHOG_SERVICE, MAILHOG_API_PORT);
        mailhogApiUrl = "http://" + mailhogHost + ":" + mailhogPort;
    }

    public static String gatewayBaseUrl() {
        if (gatewayBaseUrl == null) {
            throw new IllegalStateException("E2ETestEnvironment not started");
        }
        return gatewayBaseUrl;
    }

    public static String mailhogApiUrl() {
        if (mailhogApiUrl == null) {
            throw new IllegalStateException("E2ETestEnvironment not started");
        }
        return mailhogApiUrl;
    }

    private static File resolveComposeFile() {
        // When running from Maven, cwd is e2e-tests/
        var f = new File("docker-compose.yml");
        if (f.exists()) {
            return f;
        }

        // Fallback: running from project root
        f = new File("e2e-tests/docker-compose.yml");
        if (f.exists()) {
            return f;
        }

        throw new IllegalStateException(
                "Cannot find docker-compose.yml. Run tests from the e2e-tests directory.");
    }
}
