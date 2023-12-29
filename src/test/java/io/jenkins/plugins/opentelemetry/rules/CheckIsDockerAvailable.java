/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.rules;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.rules.ExternalResource;
import org.testcontainers.DockerClientFactory;


/**
 * Rule to check if Docker is available.
 */
public class CheckIsDockerAvailable extends ExternalResource {
      @Override
  protected void before() {
    assumeTrue(DockerClientFactory.instance().isDockerAvailable());
  }
}
