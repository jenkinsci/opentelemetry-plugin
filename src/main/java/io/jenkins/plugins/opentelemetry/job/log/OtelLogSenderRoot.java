package io.jenkins.plugins.opentelemetry.job.log;

/**
 * Sender for logs send from the controller, pipeline flow control and pipeline steps ran in Jenkins controller.
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/master/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L79
 */
final class OtelLogSenderRoot extends OtelLogSender {
    private static final long serialVersionUID = 1;

    public OtelLogSenderRoot(BuildInfo buildInfo) {
        super(buildInfo);
    }
}