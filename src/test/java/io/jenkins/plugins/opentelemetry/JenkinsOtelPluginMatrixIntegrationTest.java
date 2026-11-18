/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.github.rutledgepaulv.prune.Tree;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class JenkinsOtelPluginMatrixIntegrationTest extends BaseIntegrationTest {

    @Test
    public void testMatrixJob() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        // See
        // https://github.com/jenkinsci/matrix-project-plugin/blob/be0b18bcba0c4089b1ed9482863050de6aa65b32/src/test/java/hudson/matrix/MatrixProjectTest.java#L193-L202
        final String jobName = "test-matrix-" + jobNameSuffix.incrementAndGet();
        MatrixProject project = jenkinsRule.createProject(MatrixProject.class, jobName);
        AxisList axes = new AxisList();
        axes.add(new TextAxis("db", "mysql", "oracle"));
        axes.add(new TextAxis("direction", "north", "south"));
        project.setAxes(axes);
        MatrixBuild build = jenkinsRule.buildAndAssertSuccess(project);

        String rootSpanName = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getBuildTrace();

        // Baseline: 5 runs (1 parent + 4 sub-builds) × 4 spans each = 20. Each run also adds
        // queue phase spans: the parent gets Phase: Queue - Waiting only (no Buildable in the test JVM),
        // while each sub-build gets both Waiting and Buildable, giving 1 + 4×2 = 9 extra spans.
        MatcherAssert.assertThat(spans.cardinality(), Matchers.greaterThanOrEqualTo(20L));

        // Matrix parent traverses the Waiting queue state
        checkChainOfSpans(spans, ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_QUEUE_WAITING_NAME, rootSpanName);
        // Each sub-build traverses Waiting then Buildable; verify Buildable for at least one
        checkChainOfSpans(
                spans,
                ExtendedJenkinsAttributes.JENKINS_JOB_SPAN_PHASE_QUEUE_BUILDABLE_NAME,
                ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName + "/execution",
                rootSpanName);

        assertMatrixJobMetadata(build, spans);
        // TODO: maven multimodule contains the jobname and the maven goals.
        // assertNodeMetadata(spans, jobName, false);
    }
}
