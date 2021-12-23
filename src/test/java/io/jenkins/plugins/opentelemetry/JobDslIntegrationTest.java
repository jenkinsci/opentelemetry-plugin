/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import com.github.rutledgepaulv.prune.Tree;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

import static org.junit.Assume.assumeFalse;

public class JobDslIntegrationTest extends BaseIntegrationTest {
    @Test
    public void testJobDslJob() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String seedJobName = "test-job-dsl-seed-" + jobNameSuffix.incrementAndGet();
        String generatedJobName = "test-job-dsl-generated-job-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject seedProject = jenkinsRule.createFreeStyleProject(seedJobName);
        ExecuteDslScripts executeDslScripts = new ExecuteDslScripts();
        executeDslScripts.setScriptText("job('" + generatedJobName + "') { steps { shell('echo Hello World') } }");
        seedProject.getBuildersList().add(executeDslScripts);
        FreeStyleBuild seedBuild = jenkinsRule.buildAndAssertSuccess(seedProject);

        FreeStyleProject generatedJob = (FreeStyleProject) jenkinsRule.jenkins.getItemByFullName(generatedJobName);
        MatcherAssert.assertThat(generatedJob, CoreMatchers.notNullValue());

        jenkinsRule.buildAndAssertSuccess(generatedJob);
        Tree<SpanDataWrapper> spans = getGeneratedSpans();

    }
}
