/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import static org.junit.Assume.assumeFalse;

import org.apache.commons.lang3.SystemUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.ToolInstallations;

import com.github.rutledgepaulv.prune.Tree;

import hudson.maven.MavenModuleSet;
import hudson.maven.MavenModuleSetBuild;
import hudson.tasks.Maven;
import jenkins.mvn.DefaultSettingsProvider;

public class JenkinsOtelPluginMavenIntegrationTest extends BaseIntegrationTest {

    @Test
    public void testMavenJob() throws Exception {
        assumeFalse(SystemUtils.IS_OS_WINDOWS);

        // See https://github.com/jenkinsci/maven-plugin/blob/master/src/test/java/hudson/maven/MavenProjectTest.java
        final String jobName = "test-maven-" + jobNameSuffix.incrementAndGet();

        MavenModuleSet project = createSimpleProject(jobName);
        project.setGoals("validate");
        MavenModuleSetBuild build = jenkinsRule.buildAndAssertSuccess(project);

        Tree<SpanDataWrapper> spans = getBuildTrace();
        // TODO: decide whether to support the maven modules under the same maven build
        MatcherAssert.assertThat(spans.cardinality(), CoreMatchers.is(3L));

        assertMavenJobMetadata(build, spans);
        // TODO: maven multimodule contains the jobname and the maven goals.
        // assertNodeMetadata(spans, jobName, false);
    }

    private MavenModuleSet createSimpleProject(final String jobName) throws Exception {
        return createProject(jobName, "/simple-projects.zip");
    }

    private MavenModuleSet createProject(final String jobName, final String scmResource) throws Exception {
        MavenModuleSet project = jenkinsRule.createProject(MavenModuleSet.class, jobName);
        Maven.MavenInstallation mi = ToolInstallations.configureMaven35();
        project.setScm(new ExtractResourceSCM(getClass().getResource(
            scmResource)));
        project.setMaven(mi.getName());
        // we don't want to download internet again for unit tests
        // so local repo from user settings
        project.setSettings( new DefaultSettingsProvider() );
        return project;
    }
}
