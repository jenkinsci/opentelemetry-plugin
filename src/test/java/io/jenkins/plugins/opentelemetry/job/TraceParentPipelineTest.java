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
    public void TraceParentPropagation() throws Exception {
        JenkinsOpenTelemetryPluginConfiguration config =
                GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);

        assertNotNull(config);

        config.setEndpoint("http://localhost:4317");
        config.setExportOtelConfigurationAsEnvironmentVariables(true);
        config.save();

        WorkflowJob job = jenkinsRule.createProject(WorkflowJob.class, "traceparent-withenv-trycatch-test");

        String pipelineScript = "node {\n" + "  stage('Stage-A') {\n"
                + "    sh 'echo a_tp=$TRACEPARENT'\n"
                + "  }\n"
                + "\n"
                + "  withEnv(['FOO=bar']) {\n"
                + "    stage('Stage-B') {\n"
                + "      sh 'echo b_tp=$TRACEPARENT'\n"
                + "      try {\n"
                + "        sh 'echo try_tp=$TRACEPARENT'\n"
                + "        sh 'exit 1'\n"
                + "      } catch (err) {\n"
                + "        sh 'echo catch_tp=$TRACEPARENT'\n"
                + "      } finally {\n"
                + "        sh 'echo final_tp=$TRACEPARENT'\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        WorkflowRun run = jenkinsRule.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));

        String logs = JenkinsRule.getLog(run);

        String aTp = extract("a_tp=", logs);
        String bTp = extract("b_tp=", logs);
        String tryTp = extract("try_tp=", logs);
        String catchTp = extract("catch_tp=", logs);
        String finalTp = extract("final_tp=", logs);

        assertNotNull(aTp);
        assertNotNull(bTp);
        assertNotNull(tryTp);
        assertNotNull(catchTp);
        assertNotNull(finalTp);

        assertNotEquals(aTp, bTp);

        String traceId = traceId(aTp);

        assertEquals(traceId, traceId(bTp));
        assertEquals(traceId, traceId(tryTp));
        assertEquals(traceId, traceId(catchTp));
        assertEquals(traceId, traceId(finalTp));
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
