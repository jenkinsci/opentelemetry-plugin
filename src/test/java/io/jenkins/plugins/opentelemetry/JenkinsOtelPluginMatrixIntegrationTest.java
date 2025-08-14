/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.github.rutledgepaulv.prune.Tree;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

class JenkinsOtelPluginMatrixIntegrationTest extends BaseIntegrationTest {

    @Test
    void testMatrixJob() throws Exception {
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

        assertThat(spans.cardinality(), is(20L));
        // TODO deeper checkChainOfSpans
        checkChainOfSpans(
                spans,
                ExtendedJenkinsAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + "test-matrix-1/execution",
                rootSpanName);

        assertMatrixJobMetadata(build, spans);
        // TODO: maven multimodule contains the jobname and the maven goals.
        // assertNodeMetadata(spans, jobName, false);
    }
}
