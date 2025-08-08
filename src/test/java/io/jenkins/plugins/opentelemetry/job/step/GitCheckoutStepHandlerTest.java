package io.jenkins.plugins.opentelemetry.job.step;

import java.util.Map;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.sdk.testing.trace.SpanBuilderMock;
import io.opentelemetry.sdk.testing.trace.TracerMock;

import jenkins.plugins.git.ExtendedGitSampleRepoRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class GitCheckoutStepHandlerTest {

    private final GitCheckoutStepHandler handler = new GitCheckoutStepHandler();
    private final TracerMock tracer = new TracerMock();

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Rule
    public ExtendedGitSampleRepoRule sampleRepo = new ExtendedGitSampleRepoRule();

    // This system property allows local checkouts
    static {
        System.setProperty("hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT", "true");
    }

    /**
     * Verifies that a simple Git checkout without any extensions (i.e., the "extensions" field is absent)
     * is handled correctly by {@link GitCheckoutStepHandler#createSpanBuilder}, and no NullPointerException is thrown.
     * <p>
     * Prior to the NPE fix in the handler, this test would have failed with a NullPointerException
     * when attempting to access the "extensions" field. After the fix, the code safely handles
     * a missing or null "extensions" field, and the test passes as expected.
     */
    @Test
    @Issue("https://github.com/jenkinsci/opentelemetry-plugin/issues/1170")
    public void testSimpleGitCheckout() throws Exception {
        initSampleRepo();
        String pipeline =
            "node {\n" +
            "    checkout([$class: 'GitSCM', \n" +
            "        branches: [[name: 'master']], \n" +
            "        userRemoteConfigs: [[url: '" + sampleRepo.toString() + "']]\n" +
            "    ])\n" +
            "}";
        WorkflowRun run = runPipeline("simple-git-checkout", pipeline);
        FlowNode checkoutNode = findCheckoutNode(run);
        Map<AttributeKey<?>, Object> attributes = getSpanAttributes(checkoutNode, run);
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_SHALLOW), is(false));
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_DEPTH), is(0L));
    }

    /**
     * Verifies that a Git checkout with the "extensions" field specifying a shallow clone
     * (i.e., "shallow: true" and "depth: 1") is handled correctly by
     * {@link GitCheckoutStepHandler#createSpanBuilder}. This test ensures that the shallow
     * clone attributes are extracted and set as expected, providing coverage for the
     * scenario where the "extensions" field is present in the pipeline script.
     */
    @Test
    public void testGitCheckoutWithShallowClone() throws Exception {
        initSampleRepo();
        String pipeline =
            "node {\n" +
            "    checkout([$class: 'GitSCM', \n" +
            "        branches: [[name: 'master']], \n" +
            "        userRemoteConfigs: [[url: '" + sampleRepo.toString() + "']], \n" +
            "        extensions: [[\n" +
            "            $class: 'CloneOption', \n" +
            "            shallow: true, \n" +
            "            depth: 1\n" +
            "        ]]\n" +
            "    ])\n" +
            "}";
        WorkflowRun run = runPipeline("shallow-clone", pipeline);
        FlowNode checkoutNode = findCheckoutNode(run);
        Map<AttributeKey<?>, Object> attributes = getSpanAttributes(checkoutNode, run);
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_SHALLOW), is(true));
        assertThat(attributes.get(ExtendedJenkinsAttributes.GIT_CLONE_DEPTH), is(1L));
    }

    private WorkflowRun runPipeline(String jobName, String pipelineScript) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, jobName);
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        return jenkins.buildAndAssertSuccess(job);
    }

    private FlowNode findCheckoutNode(WorkflowRun run) throws Exception {
        FlowGraphWalker walker = new FlowGraphWalker(run.getExecution());
        for (FlowNode node : walker) {
            if ("checkout".equals(node.getDisplayFunctionName())) {
                return node;
            }
        }
        throw new AssertionError("Checkout step node not found");
    }

    private Map<AttributeKey<?>, Object> getSpanAttributes(FlowNode checkoutNode, WorkflowRun run) {
        SpanBuilder spanBuilder = handler.createSpanBuilder(checkoutNode, run, tracer);
        return ((SpanBuilderMock) spanBuilder).getAttributes();
    }

    private void initSampleRepo() throws Exception {
        sampleRepo.init();
        sampleRepo.write("README.md", "# dummy");
        sampleRepo.git("add", "README.md");
        sampleRepo.git("commit", "-m", "init");
    }
}
