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
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

/**
 * @see io.opentelemetry.api.common.Attributes
 * @see io.opentelemetry.semconv.ServiceAttributes
 */
public final class ExtendedJenkinsAttributes extends JenkinsAttributes {
    /** OTel attribute key for the Jenkins pipeline project type. */
    public static final AttributeKey<String> CI_PIPELINE_TYPE = AttributeKey.stringKey("ci.pipeline.type");
    /** OTel attribute key for the multibranch pipeline sub-type (branch, change_request, tag). */
    public static final AttributeKey<String> CI_PIPELINE_MULTIBRANCH_TYPE =
            AttributeKey.stringKey("ci.pipeline.multibranch.type");
    /**
     * @see Job#getFullName()
     */
    public static final AttributeKey<String> CI_PIPELINE_ID = AttributeKey.stringKey("ci.pipeline.id");

    /** OTel attribute key for the human-readable pipeline name. */
    public static final AttributeKey<String> CI_PIPELINE_NAME = AttributeKey.stringKey("ci.pipeline.name");
    /** OTel attribute key for the Shared Library template job identifier. */
    public static final AttributeKey<String> CI_PIPELINE_TEMPLATE_ID =
            AttributeKey.stringKey("ci.pipeline.template.id");
    /** OTel attribute key for the Shared Library template job URL. */
    public static final AttributeKey<String> CI_PIPELINE_TEMPLATE_URL =
            AttributeKey.stringKey("ci.pipeline.template.url");
    /**
     * @see hudson.model.Node#getNodeName()
     */
    public static final AttributeKey<String> CI_PIPELINE_AGENT_ID = AttributeKey.stringKey("ci.pipeline.agent.id");
    /**
     * @see hudson.model.Node#getDisplayName() ()
     */
    public static final AttributeKey<String> CI_PIPELINE_AGENT_NAME = AttributeKey.stringKey("ci.pipeline.agent.name");

    /** OTel attribute key for the list of SCM contributors that triggered the run. */
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_COMMITTERS =
            AttributeKey.stringArrayKey("ci.pipeline.run.committers");
    /** OTel attribute key for the list of causes that triggered the run. */
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_CAUSE =
            AttributeKey.stringArrayKey("ci.pipeline.run.cause");
    /** OTel attribute key indicating whether the run has completed. */
    public static final AttributeKey<Boolean> CI_PIPELINE_RUN_COMPLETED =
            AttributeKey.booleanKey("ci.pipeline.run.completed");
    /** OTel attribute key for the run duration in milliseconds. */
    public static final AttributeKey<Long> CI_PIPELINE_RUN_DURATION_MILLIS =
            AttributeKey.longKey("ci.pipeline.run.durationMillis");
    /** OTel attribute key for the run description set by the build. */
    public static final AttributeKey<String> CI_PIPELINE_RUN_DESCRIPTION =
            AttributeKey.stringKey("ci.pipeline.run.description");
    /** OTel attribute key for the Jenkins build number. */
    public static final AttributeKey<Long> CI_PIPELINE_RUN_NUMBER = AttributeKey.longKey("ci.pipeline.run.number");
    /** OTel attribute key for the sensitivity flag of each build parameter. */
    public static final AttributeKey<List<Boolean>> CI_PIPELINE_RUN_PARAMETER_IS_SENSITIVE =
            AttributeKey.booleanArrayKey("ci.pipeline.parameter.sensitive");
    /** OTel attribute key for the names of build parameters. */
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_PARAMETER_NAME =
            AttributeKey.stringArrayKey("ci.pipeline.parameter.name");
    /** OTel attribute key for the values of build parameters. */
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_PARAMETER_VALUE =
            AttributeKey.stringArrayKey("ci.pipeline.parameter.value");
    /**
     * @see Run#getResult()
     */
    public static final AttributeKey<String> CI_PIPELINE_RUN_RESULT = AttributeKey.stringKey("ci.pipeline.run.result");

    /** OTel attribute key for the Jenkins run URL. */
    public static final AttributeKey<String> CI_PIPELINE_RUN_URL = AttributeKey.stringKey("ci.pipeline.run.url");
    /** OTel attribute key for the user who triggered the run. */
    public static final AttributeKey<String> CI_PIPELINE_RUN_USER = AttributeKey.stringKey("ci.pipeline.run.user");

    /** OTel attribute key for matrix axis names in a multi-configuration build. */
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_AXIS_NAMES =
            AttributeKey.stringArrayKey("ci.pipeline.axis.names");
    /** OTel attribute key for matrix axis values in a multi-configuration build. */
    public static final AttributeKey<List<String>> CI_PIPELINE_RUN_AXIS_VALUES =
            AttributeKey.stringArrayKey("ci.pipeline.axis.values");

    /** OTel attribute key for the Git repository URL. */
    public static final AttributeKey<String> GIT_REPOSITORY = AttributeKey.stringKey("git.repository");
    /** OTel attribute key for the Git branch name. */
    public static final AttributeKey<String> GIT_BRANCH = AttributeKey.stringKey("git.branch");
    /** OTel attribute key for the Git committer username. */
    public static final AttributeKey<String> GIT_USERNAME = AttributeKey.stringKey("git.username");
    /** OTel attribute key for the Git shallow clone depth. */
    public static final AttributeKey<Long> GIT_CLONE_DEPTH = AttributeKey.longKey("git.clone.depth");
    /** OTel attribute key indicating whether the Git clone is shallow. */
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
    public static final AttributeKey<String> JENKINS_STEP_RESULT =
            AttributeKey.stringKey("jenkins.pipeline.step.result");
    /**
     * @see PluginWrapper#getShortName()
     */
    public static final AttributeKey<String> JENKINS_STEP_PLUGIN_NAME =
            AttributeKey.stringKey("jenkins.pipeline.step.plugin.name");
    /**
     * @see PluginWrapper#getVersion()
     */
    public static final AttributeKey<String> JENKINS_STEP_PLUGIN_VERSION =
            AttributeKey.stringKey("jenkins.pipeline.step.plugin.version");
    /**
     * @see Computer#getName()
     */
    public static final AttributeKey<String> JENKINS_COMPUTER_NAME = AttributeKey.stringKey("jenkins.computer.name");

    /** OTel attribute key for the label expression of the agent used by the pipeline step. */
    public static final AttributeKey<String> JENKINS_STEP_AGENT_LABEL =
            AttributeKey.stringKey("jenkins.pipeline.step.agent.label");

    /** OTel attribute key for the list of interruption causes that ended the step. */
    public static final AttributeKey<List<String>> JENKINS_STEP_INTERRUPTION_CAUSES =
            AttributeKey.stringArrayKey("jenkins.pipeline.step.interruption.causes");

    /** OTel attribute key for the Jenkins credentials identifier used in a step. */
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

        /** Span name for the start phase of a build. */
        public static final String JENKINS_JOB_SPAN_PHASE_START_NAME = "Phase: Start";
        /** Span name for the run (main execution) phase of a build. */
        public static final String JENKINS_JOB_SPAN_PHASE_RUN_NAME = "Phase: Run";
        /** Span name for the finalise phase of a build. */
        public static final String JENKINS_JOB_SPAN_PHASE_FINALIZE_NAME = "Phase: Finalise";

    /**
     * A machine or a container which is connected to the Jenkins coordinator and capable of executing
     * Pipelines or Jobs.
     */
    public static final String AGENT = "agent";

        /** Display name for agent spans in the UI. */
        public static final String AGENT_UI = "Agent";
        /** Span name for the agent allocation operation. */
        public static final String AGENT_ALLOCATE = "agent.allocate";
        /** Display name for agent allocation spans in the UI. */
        public static final String AGENT_ALLOCATION_UI = "Agent Allocation";
    /**
     * The pipeline step node
     */
    public static final String STEP_NODE = "node";
    /**
     * The pipeline step name
     */
    public static final String STEP_NAME = "step";

    /** OTel attribute key for the version of the Jenkins OpenTelemetry plugin. */
    public static final AttributeKey<String> JENKINS_OPEN_TELEMETRY_PLUGIN_VERSION =
            AttributeKey.stringKey("jenkins.opentelemetry.plugin.version");

    /** OTel attribute key for the Elastic APM transaction type. */
    public static final AttributeKey<String> ELASTIC_TRANSACTION_TYPE = AttributeKey.stringKey("type");

    /** OTel attribute key for ANSI annotation metadata embedded in log lines. */
    public static final AttributeKey<String> JENKINS_ANSI_ANNOTATIONS =
            AttributeKey.stringKey("jenkins.ansi.annotations");
    /** Field name for the character position within an ANSI annotation JSON object. */
    public static final String JENKINS_ANSI_ANNOTATIONS_POSITION_FIELD = "position";
    /** Field name for the note text within an ANSI annotation JSON object. */
    public static final String JENKINS_ANSI_ANNOTATIONS_NOTE_FIELD = "note";

    /**
     * Values in {@link EventCategoryValues}
     */
    public static final AttributeKey<String> EVENT_CATEGORY = AttributeKey.stringKey("event.category");

        /** Instrumentation scope name used when registering OpenTelemetry spans and meters. */
        public static final String INSTRUMENTATION_NAME = "io.jenkins.opentelemetry";
        /** OTel event name emitted on user login. */
        public static final String EVENT_NAME_USER_LOGIN = "user_login";

    /**
     * See https://www.elastic.co/guide/en/ecs/current/ecs-allowed-values-event-category.html
     */
    public static final class EventCategoryValues {
        /** ECS event category value for authentication events. */
        public static final String AUTHENTICATION = "authentication";
    }

        /** OTel attribute key for a generic status string on a span or event. */
        public static final AttributeKey<String> STATUS = AttributeKey.stringKey("status");
        /** OTel attribute key for a generic label string on a span or event. */
        public static final AttributeKey<String> LABEL = AttributeKey.stringKey("label");

    /**
     * Values in {@link EventOutcomeValues}
     */
    public static final AttributeKey<String> EVENT_OUTCOME = AttributeKey.stringKey("event.outcome");

    public static final class EventOutcomeValues {
        /** ECS event outcome value indicating a successful operation. */
        public static final String SUCCESS = "success";
        /** ECS event outcome value indicating a failed operation. */
        public static final String FAILURE = "failure";
        /** ECS event outcome value indicating an unknown outcome. */
        public static final String UNKNOWN = "unknown";
    }
}
