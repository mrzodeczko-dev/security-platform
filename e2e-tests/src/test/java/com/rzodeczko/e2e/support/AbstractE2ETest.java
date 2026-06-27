package com.rzodeczko.e2e.support;

import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for all E2E tests. Ensures the Docker Compose environment
 * is started before any test executes.
 */
public abstract class AbstractE2ETest {

    protected static final ApiClient api = new ApiClient();

    @BeforeAll
    static void startEnvironment() {
        E2ETestEnvironment.start();
    }
}
