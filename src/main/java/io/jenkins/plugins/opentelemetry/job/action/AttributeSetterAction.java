package io.jenkins.plugins.opentelemetry.job.action;

import hudson.model.Action;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.job.step.WithSpanAttributeStep;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.trace.Span;
import jenkins.model.RunAction2;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Set this attribute to any child spans.
 */
public class AttributeSetterAction implements Action, RunAction2 {

    private static final Logger logger = Logger.getLogger(WithSpanAttributeStep.class.getName());

    private final String key;

    private final Object value;

    private final AttributeType attributeType;

    private transient AttributeKey attributeKey;
    private transient Object convertedValue;
    private transient Run run;

    public AttributeSetterAction(Run run, String key, Object value, AttributeType attributeType) {
        this.run = run;
        this.key = key;
        this.value = value;
        this.attributeType = attributeType;
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

    private void computeValues() {
        switch (attributeType) {
            case BOOLEAN:
                attributeKey = AttributeKey.booleanKey(key);
                convertedValue = value instanceof Boolean ? value : Boolean.parseBoolean(value.toString());
                break;
            case DOUBLE:
                attributeKey = AttributeKey.doubleKey(key);
                convertedValue = value instanceof Double ? value : value instanceof Float ? ((Float) value).doubleValue() : Double.parseDouble(value.toString());
                break;
            case STRING:
                attributeKey = AttributeKey.stringKey(key);
                convertedValue = value instanceof String ? value : value.toString();
                break;
            case LONG:
                attributeKey = AttributeKey.longKey(key);
                convertedValue = value instanceof Long ?  value : value instanceof Integer ?  ((Integer) value).longValue() : Long.parseLong(value.toString());
                break;
            case BOOLEAN_ARRAY:
                attributeKey = AttributeKey.booleanArrayKey(key);
                convertedValue = value; // todo try to convert if needed
                break;
            case DOUBLE_ARRAY:
                attributeKey = AttributeKey.doubleArrayKey(key);
                convertedValue = value; // todo try to convert if needed
                break;
            case LONG_ARRAY:
                attributeKey = AttributeKey.longArrayKey(key);
                convertedValue = value; // todo try to convert if needed
                break;
            case STRING_ARRAY:
                attributeKey = AttributeKey.stringArrayKey(key);
                convertedValue = value; // todo try to convert if needed
                break;
            default:
                throw new IllegalArgumentException("Unsupported span attribute type '" + attributeType + "'. ");
        }
    }

    public void setToSpan(Span span) {
        if (attributeKey == null) {
            computeValues();
        }
        span.setAttribute(attributeKey, convertedValue);
        logger.log(Level.FINE, () -> "setSpanAttribute: run=\"" + run.getParent().getName() + "#" + run.getId() + "\", key=" + key + " value=\"" + value + "\" type=" + attributeType);
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

}
