/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.ComputeEngineInstance;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import jenkins.YesNoMaybe;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Customization of spans for google cloud attributes.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class GoogleCloudHandler implements CloudHandler {

    @Override
    public boolean canAddAttributes(@Nonnull Cloud cloud) {
        return cloud.getDescriptor() instanceof ComputeEngineCloud.GoogleCloudDescriptor;
    }

    @Override
    public void addCloudAttributes(@Nonnull Cloud cloud, @Nonnull Label label, @Nonnull SpanBuilder rootSpanBuilder) throws Exception {
        ComputeEngineCloud ceCloud = (ComputeEngineCloud) cloud;
        rootSpanBuilder
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_NAME, ceCloud.getCloudName())
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_PLATFORM, JenkinsOtelSemanticAttributes.GOOGLE_CLOUD_COMPUTE_ENGINE_PLATFORM)
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_PROJECT_ID, ceCloud.getProjectId())
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_PROVIDER, JenkinsOtelSemanticAttributes.GOOGLE_CLOUD_PROVIDER);
        if (label.getNodes().size() == 1) {
            Optional<Node> node = label.getNodes().stream().findFirst();
            if (node.isPresent()) {
                ComputeEngineInstance instance = (ComputeEngineInstance) node.get();
                InstanceConfiguration configuration = ceCloud.getInstanceConfigurationByDescription(instance.getNodeDescription());
                if (configuration != null) {
                    rootSpanBuilder
                        .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_ACCOUNT_ID, configuration.getServiceAccountEmail())
                        .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_MACHINE_TYPE, transformMachineType(configuration.getMachineType()))
                        .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_REGION, transformRegion(configuration.getRegion()))
                        .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_RUN_AS_USER, configuration.getRunAsUser())
                        .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_ZONE, transformZone(configuration.getZone()));
                }
            }
        }
    }

    protected String transformRegion(String region) {
        // f.e: "https://www.googleapis.com/compute/v1/projects/project-name/zones/us-central1-a"
        return transform(region);
    }

    protected String transformMachineType(String machineType) {
        // f.e: "https://www.googleapis.com/compute/v1/projects/project-name/zones/us-central1-a/machineTypes/n2-standard-2"
        return transform(machineType);
    }

    protected String transformZone(String zone) {
        // f.e: "https://www.googleapis.com/compute/v1/projects/project-name/zones/us-central1-a"
        return transform(zone);
    }

    private String transform(String value) {
        if (value.contains("/")) {
            return value.substring(value.lastIndexOf("/") + 1, value.length());
        }
        return value;
    }
}
