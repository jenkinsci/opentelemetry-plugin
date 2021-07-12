/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import jenkins.YesNoMaybe;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Customization of spans for kubernetes cloud attributes.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class KubernetesCloudHandler implements CloudHandler {

    private final static Logger LOGGER = Logger.getLogger(KubernetesCloudHandler.class.getName());

    @Nonnull
    @Override
    public boolean canAddAttributes(@Nonnull Cloud cloud) {
        return cloud.getDescriptor() instanceof KubernetesCloud.DescriptorImpl;
    }

    @Nonnull
    @Override
    public void addCloudAttributes(@Nonnull Cloud cloud, @Nonnull Label label, @Nonnull SpanBuilder rootSpanBuilder) throws Exception {
        KubernetesCloud k8sCloud = (KubernetesCloud) cloud;

        rootSpanBuilder
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_NAME, k8sCloud.getDisplayName())
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_PROJECT_ID, k8sCloud.getDisplayName())
            .setAttribute(JenkinsOtelSemanticAttributes.CLOUD_PROVIDER, JenkinsOtelSemanticAttributes.K8S_CLOUD_PROVIDER)
            .setAttribute(JenkinsOtelSemanticAttributes.K8S_NAMESPACE_NAME, k8sCloud.getNamespace());

        if (label.getNodes().size() == 1) {
            Optional<Node> node = label.getNodes().stream().findFirst();
            if (node.isPresent()) {
                KubernetesSlave instance = (KubernetesSlave) node.get();
                rootSpanBuilder
                    .setAttribute(JenkinsOtelSemanticAttributes.K8S_POD_NAME, instance.getPodName());

                PodTemplate podTemplate = instance.getTemplateOrNull();
                if (podTemplate != null) {
                    // TODO: add resourceLimit attributes to detect misbehaviours?
                    rootSpanBuilder
                        .setAttribute(JenkinsOtelSemanticAttributes.CONTAINER_IMAGE_NAME, getImageName(podTemplate.getImage()))
                        .setAttribute(JenkinsOtelSemanticAttributes.CONTAINER_IMAGE_TAG, getImageTag(podTemplate.getImage()))
                        .setAttribute(JenkinsOtelSemanticAttributes.CONTAINER_NAME, podTemplate.getName());
                } else {
                    LOGGER.log(Level.FINE, () -> "There is no podTemplate for the existing node.");
                }
            } else {
                LOGGER.log(Level.FINE, () -> "There is no present node.");
            }
        } else {
            LOGGER.log(Level.FINE, () -> "There are more nodes assigned for the same label (total: " + label.getNodes().size() + ")");
        }
    }

    protected String getImageName(String image) {
        if (image.contains(":")) {
            return image.substring(0, image.lastIndexOf(":"));
        }
        return image;
    }

    protected String getImageTag(String image) {
        if (image.contains(":")) {
            return image.substring(image.lastIndexOf(":") + 1);
        }
        return "latest";
    }
}
