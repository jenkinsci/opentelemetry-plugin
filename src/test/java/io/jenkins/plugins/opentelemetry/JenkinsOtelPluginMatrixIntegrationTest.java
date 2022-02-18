/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import hudson.matrix.AxisList;
import hudson.matrix.MatrixBuild;
import hudson.matrix.MatrixProject;
import hudson.matrix.TextAxis;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.util.logging.Logger;

import static org.junit.Assume.assumeFalse;

public class JenkinsOtelPluginMatrixIntegrationTest extends BaseIntegrationTest {
    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    @Test
    public void testMatrixJob() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        // See https://github.com/jenkinsci/matrix-project-plugin/blob/be0b18bcba0c4089b1ed9482863050de6aa65b32/src/test/java/hudson/matrix/MatrixProjectTest.java#L193-L202
        final String jobName = "test-matrix-" + jobNameSuffix.incrementAndGet();
        MatrixProject project = jenkinsRule.createProject(MatrixProject.class, jobName);
        AxisList axes = new AxisList();
        axes.add(new TextAxis("db","mysql", "oracle"));
        axes.add(new TextAxis("direction","north", "south"));
        project.setAxes(axes);
        MatrixBuild build = jenkinsRule.buildAndAssertSuccess(project);

        String rootSpanName = JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + jobName;

        Tree<SpanDataWrapper> spans = getGeneratedSpans();

        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(20L));
        // TODO deeper checkChainOfSpans
        checkChainOfSpans(spans, JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + "test-matrix-1/execution", rootSpanName);

        assertMatrixJobMetadata(build, spans);
        // TODO: maven multimodule contains the jobname and the maven goals.
        //assertNodeMetadata(spans, jobName, false);
    }
}
