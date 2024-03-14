package io.jenkins.plugins.opentelemetry.job.action;

import hudson.model.Action;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;

/**
 * Set this attribute to any child spans.
 */
public class AttributeSetterAction implements Action {

    AttributeKey attributeKey;
    Object convertedValue;

    public AttributeSetterAction(AttributeKey attributeKey, Object convertedValue) {
        this.attributeKey = attributeKey;
        this.convertedValue = convertedValue;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return null;
    }

    public void setToSpan(Span span) {
        span.setAttribute(attributeKey, convertedValue);
    }

}
