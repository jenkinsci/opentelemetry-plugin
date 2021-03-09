/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

public class JenkinsSemanticMetrics {
    public static final String  CI_PIPELINE_RUN_ACTIVE =            "ci.pipeline.run.active";
    public static final String  CI_PIPELINE_RUN_LAUNCHED =          "ci.pipeline.run.launched";
    public static final String  CI_PIPELINE_RUN_STARTED =           "ci.pipeline.run.started";
    public static final String  CI_PIPELINE_RUN_COMPLETED =         "ci.pipeline.run.completed";
    public static final String  CI_PIPELINE_RUN_ABORTED =           "ci.pipeline.run.aborted";
    public static final String JENKINS_QUEUE_WAITING =              "jenkins.queue.waiting";
    public static final String JENKINS_QUEUE_BLOCKED =              "jenkins.queue.blocked";
    public static final String JENKINS_QUEUE_BUILDABLE =            "jenkins.queue.buildable";
    public static final String JENKINS_QUEUE_LEFT =                 "jenkins.queue.left";
    public static final String JENKINS_QUEUE_TIME_SPENT_MILLIS =    "jenkins.queue.time_spent_millis";
    public static final String JENKINS_AGENTS_TOTAL =               "jenkins.agents.total";
    public static final String JENKINS_AGENTS_ONLINE =              "jenkins.agents.online";
    public static final String JENKINS_AGENTS_OFFLINE =             "jenkins.agents.offline";

    private JenkinsSemanticMetrics(){}
}
