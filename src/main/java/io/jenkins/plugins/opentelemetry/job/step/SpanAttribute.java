package io.jenkins.plugins.opentelemetry.job.step;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributeType;
import io.opentelemetry.api.trace.Span;

import java.io.Serializable;

public class SpanAttribute implements Serializable {

    private static final long serialVersionUID = 1476074822521826731L;

    private String key;

    private Object value;

    private AttributeType attributeType;

    private SpanAttributeTarget target;

    private transient AttributeKey<?> attributeKey;

    private transient Object convertedValue;

    private transient Span targetSpan;

	public SpanAttribute(String key, Object value, AttributeType attributeType, SpanAttributeTarget target) {
		this.key = key;
		this.value = value;
		this.attributeType = attributeType;
		this.target = target;
	}

    public void setDefaultType() {
        if (attributeType != null) {
            return;
        }

        boolean isArray = value.getClass().isArray();

        if (value instanceof Boolean) {
            attributeType = isArray ? AttributeType.BOOLEAN_ARRAY : AttributeType.BOOLEAN;
        } else if (value instanceof Double || value instanceof Float) {
            attributeType = isArray ? AttributeType.DOUBLE_ARRAY: AttributeType.DOUBLE;
        } else if (value instanceof Long || value instanceof Integer) {
            attributeType = isArray ? AttributeType.LONG_ARRAY : AttributeType.LONG;
        } else {
            attributeType = isArray ? AttributeType.STRING_ARRAY : AttributeType.STRING;
        }
    }

    public void convert() {
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

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public AttributeType getAttributeType() {
        return attributeType;
    }

    public void setAttributeType(AttributeType attributeType) {
        this.attributeType = attributeType;
    }

    public SpanAttributeTarget getTarget() {
        return target;
    }

    public void setTarget(SpanAttributeTarget target) {
        this.target = target;
    }

    public AttributeKey getAttributeKey() {
        return attributeKey;
    }

    public Object getConvertedValue() {
        return convertedValue;
    }

    public Span getTargetSpan() {
        return targetSpan;
    }

    public void setTargetSpan(Span targetSpan) {
        this.targetSpan = targetSpan;
    }

}
