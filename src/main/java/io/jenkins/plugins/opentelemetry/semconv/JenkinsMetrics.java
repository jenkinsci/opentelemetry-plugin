/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

public class JenkinsMetrics {
    /** Metric name: number of currently active (running) pipeline builds. */
    public static final String CI_PIPELINE_RUN_ACTIVE = "ci.pipeline.run.active";
    /** Metric name: duration of completed pipeline runs. */
    public static final String CI_PIPELINE_RUN_DURATION = "ci.pipeline.run.duration";
    /** Metric name: number of pipeline runs that have been launched (queued). */
    public static final String CI_PIPELINE_RUN_LAUNCHED = "ci.pipeline.run.launched";
    /** Metric name: number of pipeline runs that have started executing. */
    public static final String CI_PIPELINE_RUN_STARTED = "ci.pipeline.run.started";
    /** Metric name: number of pipeline runs that have completed. */
    public static final String CI_PIPELINE_RUN_COMPLETED = "ci.pipeline.run.completed";
    /** Metric name: number of pipeline runs that were aborted. */
    public static final String CI_PIPELINE_RUN_ABORTED = "ci.pipeline.run.aborted";
    /** Metric name: number of pipeline runs that succeeded. */
    public static final String CI_PIPELINE_RUN_SUCCESS = "ci.pipeline.run.success";
    /** Metric name: number of pipeline runs that failed. */
    public static final String CI_PIPELINE_RUN_FAILED = "ci.pipeline.run.failed";
    /** Metric name: number of available (non-occupied) executors. */
    public static final String JENKINS_EXECUTOR_AVAILABLE = "jenkins.executor.available";
    /** Metric name: number of busy (currently running a build) executors. */
    public static final String JENKINS_EXECUTOR_BUSY = "jenkins.executor.busy";
    /** Metric name: number of idle executors. */
    public static final String JENKINS_EXECUTOR_IDLE = "jenkins.executor.idle";
    /** Metric name: number of executors on online nodes. */
    public static final String JENKINS_EXECUTOR_ONLINE = "jenkins.executor.online";
    /** Metric name: number of executors on nodes that are currently connecting. */
    public static final String JENKINS_EXECUTOR_CONNECTING = "jenkins.executor.connecting";
    /** Metric name: total number of defined executors across all nodes. */
    public static final String JENKINS_EXECUTOR_DEFINED = "jenkins.executor.defined";
    /** Metric name: number of executors in the build queue. */
    public static final String JENKINS_EXECUTOR_QUEUE = "jenkins.executor.queue";
    /** Metric name: total executor count (alias for {@link #JENKINS_EXECUTOR_DEFINED}). */
    public static final String JENKINS_EXECUTOR_TOTAL = "jenkins.executor.total";
    /** Metric name: current executor count gauge. */
    public static final String JENKINS_EXECUTOR_COUNT = "jenkins.executor.count";
    /** Metric name: Jenkins node count gauge. */
    public static final String JENKINS_NODE = "jenkins.node";
    /** Metric name: number of items in the build queue. */
    public static final String JENKINS_QUEUE_COUNT = "jenkins.queue.count";
    /** Metric name: number of items in the queue waiting for an executor. */
    public static final String JENKINS_QUEUE_WAITING = "jenkins.queue.waiting";
    /** Metric name: number of items in the queue that are blocked. */
    public static final String JENKINS_QUEUE_BLOCKED = "jenkins.queue.blocked";
    /** Metric name: number of items in the queue that are buildable. */
    public static final String JENKINS_QUEUE_BUILDABLE = "jenkins.queue.buildable";
    /** Metric name: number of items that have left the queue. */
    public static final String JENKINS_QUEUE_LEFT = "jenkins.queue.left";
    /** Metric name: total time items have spent in the queue, in milliseconds. */
    public static final String JENKINS_QUEUE_TIME_SPENT_MILLIS = "jenkins.queue.time_spent_millis";
    /** Metric name: total number of defined agents (online + offline). */
    public static final String JENKINS_AGENTS_TOTAL = "jenkins.agents.total";
    /** Metric name: number of online agents. */
    public static final String JENKINS_AGENTS_ONLINE = "jenkins.agents.online";
    /** Metric name: number of offline agents. */
    public static final String JENKINS_AGENTS_OFFLINE = "jenkins.agents.offline";
    /** Metric name: number of agent launch failures. */
    public static final String JENKINS_AGENTS_LAUNCH_FAILURE = "jenkins.agents.launch.failure";
    /** Metric name: number of cloud-provisioned agent failures. */
    public static final String JENKINS_CLOUD_AGENTS_FAILURE = "jenkins.cloud.agents.failure";
    /** Metric name: number of cloud-provisioned agents that completed their work and terminated. */
    public static final String JENKINS_CLOUD_AGENTS_COMPLETED = "jenkins.cloud.agents.completed";
    /** Metric name: disk usage of the Jenkins home directory, in bytes. */
    public static final String JENKINS_DISK_USAGE_BYTES = "jenkins.disk.usage.bytes";

    /** Metric name: total number of installed plugins. */
    public static final String JENKINS_PLUGINS_COUNT = "jenkins.plugins.count";
    /** Metric name: number of installed plugins that have available updates. */
    public static final String JENKINS_PLUGINS_UPDATES = "jenkins.plugins.updates";

    /** Metric name: SCM event thread-pool size. */
    public static final String JENKINS_SCM_EVENT_POOL_SIZE = "jenkins.scm.event.pool_size";
    /** Metric name: number of active threads processing SCM events. */
    public static final String JENKINS_SCM_EVENT_ACTIVE_THREADS = "jenkins.scm.event.active_threads";
    /** Metric name: number of queued SCM event tasks. */
    public static final String JENKINS_SCM_EVENT_QUEUED_TASKS = "jenkins.scm.event.queued_tasks";
    /** Metric name: number of completed SCM event tasks. */
    public static final String JENKINS_SCM_EVENT_COMPLETED_TASKS = "jenkins.scm.event.completed_tasks";

    /** Metric name: user login counter. */
    public static final String LOGIN = "login";
    /** Metric name: successful user login counter. */
    public static final String LOGIN_SUCCESS = "login_success";
    /** Metric name: failed user login counter. */
    public static final String LOGIN_FAILURE = "login_failure";

    private JenkinsMetrics() {}
}
