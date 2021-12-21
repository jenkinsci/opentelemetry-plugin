/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.resources;

import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.resources.ResourceBuilder;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

import java.util.logging.Level;
import java.util.logging.Logger;

public class JenkinsResourceProvider implements ResourceProvider {
    private final static Logger LOGGER = Logger.getLogger(JenkinsResourceProvider.class.getName());

    @Override
    public Resource createResource(ConfigProperties config) {
        ResourceBuilder resourceBuilder = Resource.builder();
        resourceBuilder.put(ResourceAttributes.SERVICE_NAME, JenkinsOtelSemanticAttributes.JENKINS);
        resourceBuilder.put(ResourceAttributes.SERVICE_NAMESPACE, JenkinsOtelSemanticAttributes.JENKINS);
        // resourceBuilder.put(JenkinsOtelSemanticAttributes.JENKINS_URL, Jenkins.get().getRootUrl()); FIXME GET JENKINS URL
        final Resource resource = resourceBuilder.build();
        LOGGER.log(Level.FINER, () -> "Jenkins resource: " + resource);
        return resource;
    }
}
