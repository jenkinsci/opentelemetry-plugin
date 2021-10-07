/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.resource;

import hudson.util.VersionNumber;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.OtelUtils;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ResourceProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import jenkins.model.Jenkins;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A factory of a {@link Resource} which provides information about the current Jenkins instance.
 */
public class JenkinsResourceProvider implements ResourceProvider {
    private final static Logger LOGGER = Logger.getLogger(JenkinsResourceProvider.class.getName());

    @Override
    public Resource createResource(ConfigProperties config) {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        String rootUrl = jenkins == null ? "#unknown#" : Objects.toString(jenkins.getRootUrl(), "#undefined#");
        final String version = OtelUtils.getJenkinsVersion();
        Attributes attributes = Attributes.of(
                ResourceAttributes.SERVICE_NAMESPACE, Objects.requireNonNull(JenkinsOpenTelemetryPluginConfiguration.get().getServiceNamespace()),
                ResourceAttributes.SERVICE_NAME, Objects.requireNonNull(JenkinsOpenTelemetryPluginConfiguration.get().getServiceName()),
                ResourceAttributes.SERVICE_VERSION, version,
                JenkinsOtelSemanticAttributes.JENKINS_URL, rootUrl
        );
        LOGGER.log(Level.FINE, () -> "Attributes: " + attributes);
        return Resource.create(attributes);
    }
}
