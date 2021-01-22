package io.jenkins.plugins.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;

import java.util.List;

/**
 * @see io.opentelemetry.api.common.Attributes
 * @see io.opentelemetry.semconv.trace.attributes.SemanticAttributes
 */
public final class JenkinsOtelSemanticAttributes {
    public static final AttributeKey<String>        CI_PIPELINE_ID = AttributeKey.stringKey("ci.pipeline.id");
    public static final AttributeKey<String>        CI_PIPELINE_NAME = AttributeKey.stringKey("ci.pipeline.name");
    public static final AttributeKey<String>        CI_PIPELINE_NODE_ID = AttributeKey.stringKey("ci.pipeline.node.id");
    public static final AttributeKey<String>        CI_PIPELINE_NODE_NAME = AttributeKey.stringKey("ci.pipeline.node.name");
    public static final AttributeKey<Boolean>       CI_PIPELINE_RUN_COMPLETED = AttributeKey.booleanKey("ci.pipeline.run.completed");
    public static final AttributeKey<Long>          CI_PIPELINE_RUN_DURATION_MILLIS = AttributeKey.longKey("ci.pipeline.run.durationMillis");
    public static final AttributeKey<Long>          CI_PIPELINE_RUN_NUMBER = AttributeKey.longKey("ci.pipeline.number");
    public static final AttributeKey<List<Boolean>> CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE = AttributeKey.booleanArrayKey("ci.pipeline.parameter.sensitive");
    public static final AttributeKey<List<String>>  CI_PIPELINE_RUN_PARAMETER_NAME = AttributeKey.stringArrayKey("ci.pipeline.parameter.name");
    public static final AttributeKey<List<String>>  CI_PIPELINE_RUN_PARAMETER_VALUE = AttributeKey.stringArrayKey("ci.pipeline.parameter.value");
    public static final AttributeKey<String>        CI_PIPELINE_RUN_RESULT = AttributeKey.stringKey("ci.pipeline.run.result");
    public static final AttributeKey<String>        CI_PIPELINE_RUN_USER = AttributeKey.stringKey("ci.pipeline.run.user");

    public static final AttributeKey<String>        JENKINS_URL = AttributeKey.stringKey("jenkins.url");
    public static final AttributeKey<String>        JENKINS_STEP_TYPE = AttributeKey.stringKey("jenkins.pipeline.step.type");

    /**
     * @see io.opentelemetry.sdk.resources.ResourceAttributes#SERVICE_NAME
     */
    public static final String SERVICE_NAME_JENKINS = "jenkins";
}