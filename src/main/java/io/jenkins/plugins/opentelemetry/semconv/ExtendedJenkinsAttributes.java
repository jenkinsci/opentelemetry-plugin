/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

import hudson.PluginWrapper;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.api.semconv.JenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.util.List;

/**
 * @see io.opentelemetry.api.common.Attributes
 * @see io.opentelemetry.semconv.ServiceAttributes
 */
public final class ExtendedJenkinsAttributes extends JenkinsAttributes {
    public static final AttributeKey<String> CI_PIPELINE_TYPE = AttributeKey.stringKey("ci.pipeline.type");
    public static final AttributeKey<String> CI_PIPELINE_MULTIBRANCH_TYPE = AttributeKey.stringKey("ci.pipeline.multibranch.type");
    /**
     * @see Job#getFullName()
     */
    public static final AttributeKey<String> CI_PIPELINE_ID = AttributeKey.stringKey("ci.pipeline.id");
    public static final AttributeKey<String> CI_PIPELINE_NAME = AttributeKey.stringKey("ci.pipeline.name");
    public static final AttributeKey<String> CI_PIPELINE_TEMPLATE_ID = AttributeKey.stringKey("ci.pipeline.template.id");
    public static final AttributeKey<String> CI_PIPELINE_TEMPLATE_URL = AttributeKey.stringKey("ci.pipeline.template.url");
    /**
     * @see hudson.model.Node#getNodeName()
     */
    public static final AttributeKey<String> CI_PIPELINE_AGENT_ID = AttributeKey.stringKey("ci.pipeline.agent.id");
    /**
     * @see hudson.model.Node#getDisplayName() ()
     */
    public static final AttributeKey<String> CI_PIPELINE_AGENT_NAME = AttributeKey.stringKey("ci.pipeline.agent.name");
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_COMMITTERS = AttributeKey.stringArrayKey("ci.pipeline.run.committers");
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_CAUSE = AttributeKey.stringArrayKey("ci.pipeline.run.cause");
    public static final AttributeKey<Boolean> CI_PIPELINE_RUN_COMPLETED = AttributeKey.booleanKey("ci.pipeline.run.completed");
    public static final AttributeKey<Long> CI_PIPELINE_RUN_DURATION_MILLIS = AttributeKey.longKey("ci.pipeline.run.durationMillis");
    public static final AttributeKey<String> CI_PIPELINE_RUN_DESCRIPTION = AttributeKey.stringKey("ci.pipeline.run.description");
    public static final AttributeKey<Long> CI_PIPELINE_RUN_NUMBER = AttributeKey.longKey("ci.pipeline.run.number");
    public static final AttributeKey<List<Boolean>> CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE = AttributeKey.booleanArrayKey("ci.pipeline.parameter.sensitive");
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_PARAMETER_NAME = AttributeKey.stringArrayKey("ci.pipeline.parameter.name");
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_PARAMETER_VALUE = AttributeKey.stringArrayKey("ci.pipeline.parameter.value");
    /**
     * @see Run#getResult()
     */
    public static final AttributeKey<String> CI_PIPELINE_RUN_RESULT = AttributeKey.stringKey("ci.pipeline.run.result");
    public static final AttributeKey<String> CI_PIPELINE_RUN_URL = AttributeKey.stringKey("ci.pipeline.run.url");
    public static final AttributeKey<String> CI_PIPELINE_RUN_USER = AttributeKey.stringKey("ci.pipeline.run.user");

    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_AXIS_NAMES = AttributeKey.stringArrayKey("ci.pipeline.axis.names");
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_AXIS_VALUES = AttributeKey.stringArrayKey("ci.pipeline.axis.values");

    public static final AttributeKey<String> GIT_REPOSITORY = AttributeKey.stringKey("git.repository");
    public static final AttributeKey<String> GIT_BRANCH = AttributeKey.stringKey("git.branch");
    public static final AttributeKey<String> GIT_USERNAME = AttributeKey.stringKey("git.username");
    public static final AttributeKey<Long> GIT_CLONE_DEPTH = AttributeKey.longKey("git.clone.depth");
    public static final AttributeKey<Boolean> GIT_CLONE_SHALLOW = AttributeKey.booleanKey("git.clone.shallow");

    /**
     * @see StepDescriptor#getDisplayName()
     */
    public static final AttributeKey<String> JENKINS_STEP_NAME = AttributeKey.stringKey("jenkins.pipeline.step.name");
    /**
     * @see StepDescriptor#getFunctionName()
     */
    public static final AttributeKey<String> JENKINS_STEP_TYPE = AttributeKey.stringKey("jenkins.pipeline.step.type");
    /**
     * @see org.jenkinsci.plugins.workflow.graph.FlowNode#getId()
     */
    public static final AttributeKey<String> JENKINS_STEP_ID = AttributeKey.stringKey("jenkins.pipeline.step.id");
    /**
     * @see org.jenkinsci.plugins.workflow.pipelinegraphanalysis.GenericStatus
     * @see org.jenkinsci.plugins.workflow.pipelinegraphanalysis.StatusAndTiming#computeChunkStatus2(org.jenkinsci.plugins.workflow.job.WorkflowRun,org.jenkinsci.plugins.workflow.graph.FlowNode,org.jenkinsci.plugins.workflow.graph.FlowNode,org.jenkinsci.plugins.workflow.graph.FlowNode,org.jenkinsci.plugins.workflow.graph.FlowNode)
     */
    public static final AttributeKey<String> JENKINS_STEP_RESULT = AttributeKey.stringKey("jenkins.pipeline.step.result");
    /**
     * @see PluginWrapper#getShortName()
     */
    public static final AttributeKey<String> JENKINS_STEP_PLUGIN_NAME = AttributeKey.stringKey("jenkins.pipeline.step.plugin.name");
    /**
     * @see PluginWrapper#getVersion()
     */
    public static final AttributeKey<String> JENKINS_STEP_PLUGIN_VERSION = AttributeKey.stringKey("jenkins.pipeline.step.plugin.version");
    /**
     * @see Computer#getName()
     */
    public static final AttributeKey<String> JENKINS_COMPUTER_NAME = AttributeKey.stringKey("jenkins.computer.name");

    public static final AttributeKey<String> JENKINS_STEP_AGENT_LABEL = AttributeKey.stringKey("jenkins.pipeline.step.agent.label");

    public static final AttributeKey<List<String>> JENKINS_STEP_INTERRUPTION_CAUSES = AttributeKey.stringArrayKey("jenkins.pipeline.step.interruption.causes");

    public static final AttributeKey<String> JENKINS_CREDENTIALS_ID = AttributeKey.stringKey("jenkins.credentials.id");

    /**
     * As {@link Jenkins.MasterComputer#getName()} returns "", choose another name
     *
     * @see Jenkins.MasterComputer#getName()
     */
    public static final String JENKINS_COMPUTER_NAME_CONTROLLER = "#controller#";

    /**
     * Prefix of build root spans
     */
    public static final String CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX = "BUILD ";

    public static final String JENKINS_JOB_SPAN_PHASE_START_NAME = "Phase: Start";
    public static final String JENKINS_JOB_SPAN_PHASE_RUN_NAME = "Phase: Run";
    public static final String JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME = "Phase: Finalise";

    /**
     * A machine or a container which is connected to the Jenkins coordinator and capable of executing
     * Pipelines or Jobs.
     */
    public static final String AGENT = "agent";
    public static final String AGENT_UI = "Agent";
    public static final String AGENT_ALLOCATE = "agent.allocate";
    public static final String AGENT_ALLOCATION_UI = "Agent Allocation";
    /**
     * The pipeline step node
     */
    public static final String STEP_NODE = "node";
    /**
     * The pipeline step name
     */
    public static final String STEP_NAME = "step";

    public static final AttributeKey<String> JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION = AttributeKey.stringKey("jenkins.opentelemetry.plugin.version");

    public static final AttributeKey<String> ELASTIC_TRANSACTION_TYPE = AttributeKey.stringKey("type");

    public static final AttributeKey<String> JENKINS_ANSI_ANNOTATIONS = AttributeKey.stringKey("jenkins.ansi.annotations");
    public static final String JENKINS_ANSI_ANNOTATIONS_POSITION_FIELD = "position";
    public static final String JENKINS_ANSI_ANNOTATIONS_NOTE_FIELD = "note";

    /**
     * Values in {@link EventCategoryValues}
     */
    public static final AttributeKey<String> EVENT_CATEGORY = AttributeKey.stringKey("event.category");
    public final static String INSTRUMENTATION_NAME = "io.jenkins.opentelemetry";

    /**
     * See https://www.elastic.co/guide/en/ecs/current/ecs-allowed-values-event-category.html
     */
    public static final class EventCategoryValues {
        public static final String AUTHENTICATION = "authentication";
    }

    public static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");
    public static final AttributeKey<String> LABEL = AttributeKey.stringKey("label");


    /**
     * Values in {@link EventOutcomeValues}
     */
    public static final AttributeKey<String> EVENT_OUTCOME = AttributeKey.stringKey("event.outcome");

    public static final class EventOutcomeValues {
        public static final String SUCCESS = "success";
        public static final String FAILURE = "failure";
        public static final String UNKNOWN = "unknown";
    }
}
