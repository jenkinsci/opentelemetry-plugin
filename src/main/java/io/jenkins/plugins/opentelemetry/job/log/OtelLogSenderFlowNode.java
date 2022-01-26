package io.jenkins.plugins.opentelemetry.job.log;

import jenkins.util.JenkinsJVM;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

import java.io.IOException;

/**
 * Sender for logs retaled to a flow node.
 * See https://github.com/jenkinsci/pipeline-cloudwatch-logs-plugin/blob/master/src/main/java/io/jenkins/plugins/pipeline_cloudwatch_logs/CloudWatchSender.java#L108
 */
final class OtelLogSenderFlowNode extends OtelLogSender {
    private static final long serialVersionUID = 1;
    final FlowNode node;

    public OtelLogSenderFlowNode(BuildInfo buildInfo, FlowNode node) {
        super(buildInfo);
        this.node = node;
    }

    @Override
    public void close() throws IOException {
        super.close();
        if (JenkinsJVM.isJenkinsJVM()) { // TODO why
            OtelLogStorageFactory.get().close(buildInfo);
        }
    }
}