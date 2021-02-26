/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry.resource;

import hudson.util.VersionNumber;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import io.opentelemetry.sdk.resources.ResourceProvider;
import jenkins.model.Jenkins;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ResourceProvider} which provides information about the current Jenkins instance.
 */
public class JenkinsResource extends ResourceProvider {
    private final static Logger LOGGER = Logger.getLogger(JenkinsResource.class.getName());

    @Override
    protected Attributes getAttributes() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        String rootUrl = jenkins == null ? "#unknown#" : Objects.toString(jenkins.getRootUrl(), "#undefined#");
        final VersionNumber versionNumber = Jenkins.getVersion();
        final String version =  versionNumber == null ? "#unknown" : versionNumber.toString(); // should not be null except maybe in development of Jenkins itself
        Attributes attributes = Attributes.of(
                ResourceAttributes.SERVICE_NAMESPACE, JenkinsOtelSemanticAttributes.SERVICE_NAMESPACE_JENKINS,
                ResourceAttributes.SERVICE_NAME, JenkinsOtelSemanticAttributes.SERVICE_NAME_JENKINS,
                ResourceAttributes.SERVICE_VERSION, version,
                JenkinsOtelSemanticAttributes.JENKINS_URL, rootUrl
        );
        LOGGER.log(Level.FINE, () -> "Attributes: " + attributes);
        return attributes;
    }
}
