package io.jenkins.plugins.opentelemetry.remotespan;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.util.Optional;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;

import com.github.rutledgepaulv.prune.Tree;

import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.security.AuthorizationStrategy;
import hudson.security.SecurityRealm;
import io.jenkins.plugins.opentelemetry.BaseIntegrationTest;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;


public class RemoteSpanDisabledTest extends BaseIntegrationTest {
    static final String PARENT_TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    static final String PARENT_SPAN_ID = "00f067aa0ba902b7";


    @Test
    public void testRemoteSpanDisabledByDefault() throws Exception {

        //remote call (with parent trace)->target->(build step)->target-sub
        //So the target and target sub should have same trace id inherited from remote span.

        jenkinsRule.jenkins.setAuthorizationStrategy(AuthorizationStrategy.Unsecured.UNSECURED);
        jenkinsRule.jenkins.setSecurityRealm(SecurityRealm.NO_AUTHENTICATION);
        jenkinsRule.jenkins.setCrumbIssuer(null);


        //First we create a sub-project
        FreeStyleProject targetSubProject = jenkinsRule.createFreeStyleProject("target-sub-project");

        //We create a pipeline job to build sub-project locally
        WorkflowJob targetProject = jenkinsRule.createProject(WorkflowJob.class, "target-project");
        String pipelineScript = "build job: '" + targetSubProject.getName()+"'" ;
        targetProject.setDefinition(new CpsFlowDefinition(pipelineScript, true));




        //We trigger the build by Jenkins remote api
        OkHttpClient client = new OkHttpClient();
        RequestBody reqbody = RequestBody.create(null, new byte[0]);
        String traceParentHeader = "00-" + PARENT_TRACE_ID + "-" + PARENT_SPAN_ID + "-01";

        Request request = new Request.Builder()
            .url(targetProject.getAbsoluteUrl() + "build")
            .header("traceparent", traceParentHeader)
            .post(reqbody)
            .build();
        client.newCall(request).execute();


        await().atMost(30, SECONDS).untilAsserted(() ->
            {

                Run targetBuild = targetProject.getLastBuild();
                assertThat(targetProject.getName() + " should complete successfully",
                    targetBuild != null && Result.SUCCESS.equals(targetBuild.getResult()));

                Run targetSubBuild = targetSubProject.getLastBuild();
                assertThat(targetSubProject.getName() + " should complete successfully",
                    targetSubBuild != null && Result.SUCCESS.equals(targetSubBuild.getResult()));
            }

        );



        //We will wait all spans at most 30s.
        await().atMost(30, SECONDS).pollInterval(1, SECONDS).untilAsserted(() ->
        {
            Tree<SpanDataWrapper> spans = getGeneratedSpans(1); //Since we disable the remote span tracing, the first span is the Jenkins Web Environment. We pick the 2nd (index 1).
            String targetSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + targetProject.getName();
            Optional<Tree.Node<SpanDataWrapper>> targetJobSpan = spans.breadthFirstSearchNodes(node -> targetSpanName.equals(node.getData().spanData.getName()));
            assertThat("Should have target job span in the tree", targetJobSpan.isPresent());
            assertThat("Target job should have the traceId same as trace parent", targetJobSpan.get().getData().spanData.getTraceId(), not(equalTo(PARENT_TRACE_ID)));

            String targetSubSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + targetSubProject.getName();
            Optional<Tree.Node<SpanDataWrapper>> targetSubSpan = targetJobSpan.get().asTree()
                .breadthFirstSearchNodes(node -> targetSubSpanName.equals(node.getData().spanData.getName()));
            assertThat("Should have target sub job span in the tree", targetSubSpan.isPresent());
            assertThat("Target sub job should not have the traceId same as trace parent", targetSubSpan.get().getData().spanData.getTraceId(), not(equalTo(PARENT_TRACE_ID)));

        });


    }

}
