/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.rules;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Extension to check if Docker is available.
 */
public class CheckIsDockerAvailable implements BeforeEachCallback {
    @Override
    public void beforeEach(ExtensionContext context) {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }
}
