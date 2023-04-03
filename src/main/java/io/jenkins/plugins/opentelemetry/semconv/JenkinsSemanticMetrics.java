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
    public static final String  CI_PIPELINE_RUN_SUCCESS =           "ci.pipeline.run.success";
    public static final String  CI_PIPELINE_RUN_FAILED =            "ci.pipeline.run.failed";
    public static final String JENKINS_EXECUTOR_AVAILABLE = "jenkins.executor.available";
    public static final String JENKINS_EXECUTOR_BUSY = "jenkins.executor.busy";
    public static final String JENKINS_EXECUTOR_IDLE = "jenkins.executor.idle";
    public static final String JENKINS_EXECUTOR_ONLINE = "jenkins.executor.online";
    public static final String JENKINS_EXECUTOR_CONNECTING = "jenkins.executor.connecting";
    public static final String JENKINS_EXECUTOR_DEFINED = "jenkins.executor.defined";
    public static final String JENKINS_EXECUTOR_QUEUE = "jenkins.executor.queue";
    public static final String JENKINS_QUEUE_WAITING =              "jenkins.queue.waiting";
    public static final String JENKINS_QUEUE_BLOCKED =              "jenkins.queue.blocked";
    public static final String JENKINS_QUEUE_BUILDABLE =            "jenkins.queue.buildable";
    public static final String JENKINS_QUEUE_LEFT =                 "jenkins.queue.left";
    public static final String JENKINS_QUEUE_TIME_SPENT_MILLIS =    "jenkins.queue.time_spent_millis";
    public static final String JENKINS_AGENTS_TOTAL =               "jenkins.agents.total";
    public static final String JENKINS_AGENTS_ONLINE =              "jenkins.agents.online";
    public static final String JENKINS_AGENTS_OFFLINE =             "jenkins.agents.offline";
    public static final String JENKINS_AGENTS_LAUNCH_FAILURE =      "jenkins.agents.launch.failure";
    public static final String JENKINS_CLOUD_AGENTS_FAILURE =       "jenkins.cloud.agents.failure";
    public static final String JENKINS_CLOUD_AGENTS_COMPLETED =     "jenkins.cloud.agents.completed";
    public static final String JENKINS_DISK_USAGE_BYTES =           "jenkins.disk.usage.bytes";

    public static final String JENKINS_SCM_EVENT_POOL_SIZE =         "jenkins.scm.event.pool_size";
    public static final String JENKINS_SCM_EVENT_ACTIVE_THREADS =    "jenkins.scm.event.active_threads";
    public static final String JENKINS_SCM_EVENT_QUEUED_TASKS =      "jenkins.scm.event.queued_tasks";
    public static final String JENKINS_SCM_EVENT_COMPLETED_TASKS =   "jenkins.scm.event.completed_tasks";



    public static final String LOGIN =           "login";
    public static final String LOGIN_SUCCESS =   "login_success";
    public static final String LOGIN_FAILURE =   "login_failure";

    private JenkinsSemanticMetrics(){}
}
