/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.Shell;
import hudson.util.LogTaskListener;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.Attributes;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assume.assumeFalse;

public class JenkinsOtelPluginFreestyleIntegrationTest extends BaseIntegrationTest {
    private static final Logger LOGGER = Logger.getLogger(Run.class.getName());

    @Test
    public void testFreestyleJob() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"x\""));
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(5L));

        assertFreestyleJobMetadata(build, spans);
    }

    @Test
    public void testFreestyleJob_with_multiple_builders() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && touch \"x\""));
        project.getBuildersList().add(new Shell("set -u && touch \"y\""));
        FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        // TODO: implementation should have two siblings Shell steps.
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(6L));

        assertFreestyleJobMetadata(build, spans);
    }

    @Test
    public void testFreestyleJobFailed() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        final String jobName = "test-freestyle-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject(jobName);
        project.getBuildersList().add(new Shell("set -u && exit 1"));
        FreeStyleBuild build = jenkinsRule.buildAndAssertStatus(Result.FAILURE, project);

        Tree<SpanDataWrapper> spans = getGeneratedSpans();
        checkChainOfSpans(spans, "Phase: Start", jobName);
        checkChainOfSpans(spans, "shell", "Phase: Run", jobName);
        checkChainOfSpans(spans, "Phase: Finalise", jobName);
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(5L));

        assertFreestyleJobMetadata(build, spans);
    }

    private void assertFreestyleJobMetadata(FreeStyleBuild build, Tree<SpanDataWrapper> spans) throws Exception {
        List<SpanDataWrapper> root = spans.byDepth().get(0);
        Attributes attributes = root.get(0).spanData.getAttributes();
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_TYPE), CoreMatchers.is(OtelUtils.FREESTYLE));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.CI_PIPELINE_MULTIBRANCH_TYPE), CoreMatchers.nullValue());

        // Environment variables are populated
        EnvVars environment = build.getEnvironment(new LogTaskListener(LOGGER, Level.INFO));
        assertEnvironmentVariables(environment);
    }
}
