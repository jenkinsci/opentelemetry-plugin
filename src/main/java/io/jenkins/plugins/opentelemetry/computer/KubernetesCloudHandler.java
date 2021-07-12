/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import hudson.Extension;
import hudson.model.Label;
import hudson.slaves.Cloud;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import jenkins.YesNoMaybe;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;

import javax.annotation.Nonnull;

/**
 * Customization of spans for kubernetes cloud attributes.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class KubernetesCloudHandler implements CloudHandler {

    @Nonnull
    @Override
    public boolean canAddAttributes(@Nonnull Cloud cloud) {
        return cloud.getDescriptor() instanceof KubernetesCloud.DescriptorImpl;
    }

    @Override
    public void addCloudAttributes(@Nonnull Cloud cloud, @Nonnull Label label, @Nonnull SpanBuilder rootSpanBuilder) throws Exception {
        KubernetesCloud k8sCloud = (KubernetesCloud) cloud;
        rootSpanBuilder
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_NAME, k8sCloud.getDisplayName())
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_PROJECT_ID, k8sCloud.getDisplayName())
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_PROVIDER, JenkinsOtelSemanticAttributes.K8S_CLOUD_PROVIDER)
            .setAttribute(JenkinsOtelSemanticAttributes.K8S_NAMESPACE_NAME, k8sCloud.getNamespace());
    }
}
