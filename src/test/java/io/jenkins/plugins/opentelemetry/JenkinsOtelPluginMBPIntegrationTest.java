/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import hudson.Functions;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsSemanticMetrics;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JenkinsOtelPluginMBPIntegrationTest extends BaseIntegrationTest {

    @Test
    public void testMultibranchPipelineStep() throws Exception {
        String pipelineScript = "def xsh(cmd) {if (isUnix()) {sh cmd} else {bat cmd}};\n" +
                "node() {\n" +
                "    stage('ze-parallel-stage') {\n" +
                "        parallel parallelBranch1: {\n" +
                "            xsh (label: 'shell-1', script: 'echo this-is-the-parallel-branch-1')\n" +
                "        } ,parallelBranch2: {\n" +
                "            xsh (label: 'shell-2', script: 'echo this-is-the-parallel-branch-2')\n" +
                "        } ,parallelBranch3: {\n" +
                "            xsh (label: 'shell-3', script: 'echo this-is-the-parallel-branch-3')\n" +
                "        }\n" +
                "    }\n" +
                "}";
        final Node node = jenkinsRule.createOnlineSlave();

        WorkflowJob pipeline = jenkinsRule.createProject(WorkflowJob.class, "test-pipeline-with-parallel-step" + jobNameSuffix.incrementAndGet());
        pipeline.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        WorkflowRun build = jenkinsRule.assertBuildStatus(Result.SUCCESS, pipeline.scheduleBuild2(0));

        final Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "shell-1", "Parallel branch: parallelBranch1", "Stage: ze-parallel-stage", "Node", "Phase: Run");
        checkChainOfSpans(spans, "shell-2", "Parallel branch: parallelBranch2", "Stage: ze-parallel-stage", "Node", "Phase: Run");
        checkChainOfSpans(spans, "shell-3", "Parallel branch: parallelBranch3", "Stage: ze-parallel-stage", "Node", "Phase: Run");
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(13L));
    }

}
