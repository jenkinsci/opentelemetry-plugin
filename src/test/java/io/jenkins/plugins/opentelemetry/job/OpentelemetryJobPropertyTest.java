/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.model.FreeStyleProject;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class OpentelemetryJobPropertyTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testFreestyle() throws Exception {
        JenkinsRule.WebClient wc = j.createWebClient();
        // not configured
        {
            FreeStyleProject p = j.createFreeStyleProject();
            assertNull(p.getProperty(OpentelemetryJobProperty.class));

            j.submit(wc.getPage(p, "configure").getFormByName("config"));

            p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotNull(p);
            assertNull(p.getProperty(OpentelemetryJobProperty.class));
        }

        // configured
        {
            FreeStyleProject p = j.createFreeStyleProject();
            p.addProperty(new OpentelemetryJobProperty("my-metadata=123"));

            j.submit(wc.getPage(p, "configure").getFormByName("config"));

            p = j.jenkins.getItemByFullName(p.getFullName(), FreeStyleProject.class);
            assertNotNull(p);
            OpentelemetryJobProperty prop = p.getProperty(OpentelemetryJobProperty.class);
            assertNotNull(prop);
            assertEquals("my-metadata=123", prop.getMetadata());
        }
    }

    @Test
    public void testPipeline() throws Exception {
        WorkflowJob upstream = j.createProject(WorkflowJob.class, "upstream");
        upstream.setDefinition(new CpsFlowDefinition("properties([opentelemetry('my-metadata=123')]); echo 'hi'", true));
        j.buildAndAssertSuccess(upstream);
        OpentelemetryJobProperty prop = upstream.getProperty(OpentelemetryJobProperty.class);
        assertNotNull(prop);
        assertEquals("my-metadata=123", prop.getMetadata());
    }
}