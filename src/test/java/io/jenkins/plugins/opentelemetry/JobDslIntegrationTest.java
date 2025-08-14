/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import hudson.model.FreeStyleProject;
import javaposse.jobdsl.plugin.ExecuteDslScripts;
import org.apache.commons.lang3.SystemUtils;
import org.junit.jupiter.api.Test;

class JobDslIntegrationTest extends BaseIntegrationTest {

    @Test
    void testJobDslJob() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);
        String seedJobName = "test-job-dsl-seed-" + jobNameSuffix.incrementAndGet();
        String generatedJobName = "test-job-dsl-generated-job-" + jobNameSuffix.incrementAndGet();
        FreeStyleProject seedProject = jenkinsRule.createFreeStyleProject(seedJobName);
        ExecuteDslScripts executeDslScripts = new ExecuteDslScripts();
        executeDslScripts.setScriptText("job('" + generatedJobName + "') { steps { shell('echo Hello World') } }");
        seedProject.getBuildersList().add(executeDslScripts);
        jenkinsRule.buildAndAssertSuccess(seedProject);

        FreeStyleProject generatedJob = (FreeStyleProject) jenkinsRule.jenkins.getItemByFullName(generatedJobName);
        assertThat(generatedJob, notNullValue());

        jenkinsRule.buildAndAssertSuccess(generatedJob);
        getBuildTrace();
    }
}
