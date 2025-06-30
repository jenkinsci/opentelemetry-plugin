/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import hudson.ExtensionList;
import java.util.List;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution.SynchronousNonBlockingStepExecutorServiceAugmentor;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class StepExecutionInstrumentationInitializerTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void test_executorServiceAugmentor() throws Exception {
        List<SynchronousNonBlockingStepExecutorServiceAugmentor> extensions =
                ExtensionList.lookup(SynchronousNonBlockingStepExecutorServiceAugmentor.class);
        assertThat(extensions.size(), is(1));
        assertThat(extensions.get(0), instanceOf(StepExecutionInstrumentationInitializer.class));
    }
}
