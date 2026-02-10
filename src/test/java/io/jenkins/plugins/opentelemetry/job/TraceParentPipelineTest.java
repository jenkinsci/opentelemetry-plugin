package io.jenkins.plugins.opentelemetry.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class TraceParentPipelineTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @Test
    public void traceParentPropagationInNestedStages() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration config =
                GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        assertNotNull("OpenTelemetry plugin configuration must exist", config);

        config.setEndpoint("http://localhost:4317");
        config.setExportOtelConfigurationAsEnvironmentVariables(true);
        config.save();

        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, "traceparent-nested-test");

        String pipelineScript = "node {\n" + "  stage('Outer') {\n"
                + "    sh 'echo OUTER_TP=$TRACEPARENT'\n"
                + "    stage('Inner') {\n"
                + "      sh 'echo INNER_TP=$TRACEPARENT'\n"
                + "    }\n"
                + "  }\n"
                + "}";

        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));

        String logs = JenkinsRule.getLog(run);

        String outerTp = extract("OUTER_TP=", logs);
        String innerTp = extract("INNER_TP=", logs);

        assertNotNull("Outer TRACEPARENT missing", outerTp);
        assertNotNull("Inner TRACEPARENT missing", innerTp);

        assertNotEquals(outerTp, innerTp);

        assertEquals(traceId(outerTp), traceId(innerTp));
    }

    private String extract(String prefix, String log) {
        for (String line : log.split("\n")) {
            if (line.contains(prefix)) {
                return line.substring(line.indexOf(prefix) + prefix.length()).trim();
            }
        }
        return null;
    }

    private String traceId(String traceParent) {
        String[] parts = traceParent.split("-");
        return parts.length >= 2 ? parts[1] : null;
    }
}
