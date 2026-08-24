/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import hudson.util.FormValidation;
import jenkins.model.GlobalConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Regression tests for <a href="https://github.com/jenkinsci/opentelemetry-plugin/issues/1289">issue #1289</a>:
 * {@link JenkinsOpenTelemetryPluginConfiguration#doCheckIgnoredSteps(String)} rejected step function names
 * containing an underscore, even though {@code StepDescriptor#getFunctionName()} values are Java/Groovy
 * identifiers that may legitimately contain one (for example the OpenShift Client Plugin's "_OcAction").
 */
@WithJenkins
class JenkinsOpenTelemetryPluginConfigurationTest {

    private JenkinsOpenTelemetryPluginConfiguration configuration;

    @BeforeEach
    void beforeEach(JenkinsRule j) {
        configuration = GlobalConfiguration.all().get(JenkinsOpenTelemetryPluginConfiguration.class);
        assertNotNull(configuration);
    }

    @Test
    void acceptsStepFunctionNameWithUnderscore() {
        assertEquals(FormValidation.Kind.OK, configuration.doCheckIgnoredSteps("_OcAction").kind);
    }

    @Test
    void acceptsMultipleCommaSeparatedStepFunctionNamesWithUnderscores() {
        assertEquals(FormValidation.Kind.OK, configuration.doCheckIgnoredSteps("sh,_OcAction,echo").kind);
    }

    @Test
    void acceptsEmptyValue() {
        assertEquals(FormValidation.Kind.OK, configuration.doCheckIgnoredSteps("").kind);
    }

    @Test
    void rejectsInvalidCharacters() {
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckIgnoredSteps("sh,with space").kind);
        assertEquals(FormValidation.Kind.ERROR, configuration.doCheckIgnoredSteps("sh;echo").kind);
    }
}
