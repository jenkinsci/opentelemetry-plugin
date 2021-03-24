/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

import hudson.model.Computer;
import io.opentelemetry.api.common.AttributeKey;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.util.List;

/**
 * @see io.opentelemetry.api.common.Attributes
 * @see io.opentelemetry.semconv.trace.attributes.SemanticAttributes
 */
public final class JenkinsOtelSemanticAttributes {
	public static final AttributeKey<String>        CI_PIPELINE_TYPE = AttributeKey.stringKey("ci.pipeline.type");
    public static final AttributeKey<String>        CI_PIPELINE_ID = AttributeKey.stringKey("ci.pipeline.id");
    public static final AttributeKey<String>        CI_PIPELINE_NAME = AttributeKey.stringKey("ci.pipeline.name");
    /**
     * @see hudson.model.Node#getNodeName()
     */
    public static final AttributeKey<String>        CI_PIPELINE_NODE_ID = AttributeKey.stringKey("ci.pipeline.node.id");
    /**
     * @see hudson.model.Node#getDisplayName() ()
     */
    public static final AttributeKey<String>        CI_PIPELINE_NODE_NAME = AttributeKey.stringKey("ci.pipeline.node.name");
    public static final AttributeKey<Boolean>       CI_PIPELINE_RUN_COMPLETED = AttributeKey.booleanKey("ci.pipeline.run.completed");
    public static final AttributeKey<Long>          CI_PIPELINE_RUN_DURATION_MILLIS = AttributeKey.longKey("ci.pipeline.run.durationMillis");
    public static final AttributeKey<String>        CI_PIPELINE_RUN_DESCRIPTION = AttributeKey.stringKey("ci.pipeline.run.description");
    public static final AttributeKey<Long>          CI_PIPELINE_RUN_NUMBER = AttributeKey.longKey("ci.pipeline.number");
    public static final AttributeKey<List<Boolean>> CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE = AttributeKey.booleanArrayKey("ci.pipeline.parameter.sensitive");
    public static final AttributeKey<List<String>>  CI_PIPELINE_RUN_PARAMETER_NAME = AttributeKey.stringArrayKey("ci.pipeline.parameter.name");
    public static final AttributeKey<List<String>>  CI_PIPELINE_RUN_PARAMETER_VALUE = AttributeKey.stringArrayKey("ci.pipeline.parameter.value");
    public static final AttributeKey<String>        CI_PIPELINE_RUN_RESULT = AttributeKey.stringKey("ci.pipeline.run.result");
    public static final AttributeKey<String>        CI_PIPELINE_RUN_URL = AttributeKey.stringKey("ci.pipeline.run.url");
    public static final AttributeKey<String>        CI_PIPELINE_RUN_USER = AttributeKey.stringKey("ci.pipeline.run.user");

    public static final AttributeKey<String>        GIT_REPOSITORY = AttributeKey.stringKey("git.repository");
    public static final AttributeKey<String>        GIT_BRANCH = AttributeKey.stringKey("git.branch");
    public static final AttributeKey<String>        GIT_USERNAME = AttributeKey.stringKey("git.username");

    /**
     * @see Jenkins#getRootUrl()
     */
    public static final AttributeKey<String>        JENKINS_URL = AttributeKey.stringKey("jenkins.url");
    /**
     * @see StepDescriptor#getDisplayName()
     */
    public static final AttributeKey<String>        JENKINS_STEP_NAME = AttributeKey.stringKey("jenkins.pipeline.step.name");
    /**
     * @see StepDescriptor#getFunctionName()
     */
    public static final AttributeKey<String>        JENKINS_STEP_TYPE = AttributeKey.stringKey("jenkins.pipeline.step.type");
    /**
     * @see org.jenkinsci.plugins.workflow.graph.FlowNode#getId()
     */
    public static final AttributeKey<String>        JENKINS_STEP_ID = AttributeKey.stringKey("jenkins.pipeline.step.id");
    /**
     * @see Computer#getName()
     */
    public static final AttributeKey<String>        JENKINS_COMPUTER_NAME = AttributeKey.stringKey("jenkins.computer.name");

    public static final AttributeKey<String>        JENKINS_STEP_NODE_LABEL = AttributeKey.stringKey("jenkins.pipeline.step.node.label");

    /**
     * @see io.opentelemetry.semconv.resource.attributes.ResourceAttributes#SERVICE_NAME
     */
    public static final String SERVICE_NAME_JENKINS = "jenkins";

    /**
     * @see io.opentelemetry.semconv.resource.attributes.ResourceAttributes#SERVICE_NAMESPACE
     */
    public static final String SERVICE_NAMESPACE_JENKINS = "jenkins";

    /**
     * As {@link Jenkins.MasterComputer#getName()} return "", choose another name
     * @see Jenkins.MasterComputer#getName()
     */
    public static final String JENKINS_COMPUTER_NAME_CONTROLLER = "#controller#";

    public static final String JENKINS_JOB_SPAN_PHASE_START_NAME = "Phase: Start";
    public static final String JENKINS_JOB_SPAN_PHASE_RUN_NAME = "Phase: Run";
    public static final String JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME = "Phase: Finalise";

}
