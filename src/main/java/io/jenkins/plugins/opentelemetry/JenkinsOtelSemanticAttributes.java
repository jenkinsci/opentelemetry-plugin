package io.jenkins.plugins.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

import java.util.List;

/**
 * @see io.opentelemetry.api.trace.attributes.SemanticAttributes
 */
public class JenkinsOtelSemanticAttributes {

    public static final AttributeKey<String> CI_PIPELINE_ID = AttributeKey.stringKey("ci.pipeline.id");
    public static final AttributeKey<String> CI_PIPELINE_NAME = AttributeKey.stringKey("ci.pipeline.name");
    public static final AttributeKey<Long> CI_PIPELINE_RUN_NUMBER = AttributeKey.longKey("ci.pipeline.number");
    public static final AttributeKey<String> CI_PIPELINE_NODE_ID = AttributeKey.stringKey("ci.pipeline.node.id");
    public static final AttributeKey<String> CI_PIPELINE_NODE_NAME = AttributeKey.stringKey("ci.pipeline.node.name");

    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_PARAMETER_NAME = AttributeKey.stringArrayKey("ci.pipeline.parameter.name");
    public static final AttributeKey<List<Boolean>> CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE = AttributeKey.booleanArrayKey("ci.pipeline.parameter.sensitive");
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_PARAMETER_VALUE = AttributeKey.stringArrayKey("ci.pipeline.parameter.value");
    public static final AttributeKey<String> CI_PIPELINE_RUN_RESULT = AttributeKey.stringKey("ci.pipeline.run.result");
    public static final AttributeKey<Boolean> CI_PIPELINE_RUN_COMPLETED = AttributeKey.booleanKey("ci.pipeline.run.completed");
    public static final AttributeKey<Long> CI_PIPELINE_RUN_DURATION_MILLIS = AttributeKey.longKey("ci.pipeline.run.durationMillis");
    public static final AttributeKey<String> CI_PIPELINE_TYPE = AttributeKey.stringKey("ci.pipeline.type");

}