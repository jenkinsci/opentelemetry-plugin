/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.computer;

import hudson.Extension;
import hudson.model.Node;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import jenkins.YesNoMaybe;
import org.csanchez.jenkins.plugins.kubernetes.KubernetesSlave;
import org.csanchez.jenkins.plugins.kubernetes.PodTemplate;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Customization of spans for kubernetes cloud attributes.
 */
@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class KubernetesCloudNodeHandler implements CloudNodeHandler {

    private final static Logger LOGGER = Logger.getLogger(KubernetesCloudNodeHandler.class.getName());

    @Override
    public boolean canAddAttributes(@Nonnull Node node) {
        return node instanceof KubernetesSlave;
    }

    @Override
    public void addCloudSpanAttributes(@Nonnull Node node, @Nonnull Span span) throws Exception {
        KubernetesSlave instance = (KubernetesSlave) node;
        span
            .setAttribute(JenkinsOtelSemanticAttributes.K8S_POD_NAME, instance.getPodName());
        PodTemplate podTemplate = instance.getTemplateOrNull();
        if (podTemplate != null) {
            // TODO: add resourceLimit attributes to detect misbehaviours?
            span
                .setAttribute(JenkinsOtelSemanticAttributes.CONTAINER_IMAGE_NAME, getImageName(podTemplate.getImage()))
                .setAttribute(JenkinsOtelSemanticAttributes.CONTAINER_IMAGE_TAG, getImageTag(podTemplate.getImage()))
                .setAttribute(JenkinsOtelSemanticAttributes.CONTAINER_NAME, podTemplate.getName());
        } else {
            LOGGER.log(Level.FINE, () -> "There is no podTemplate for the existing node.");
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
