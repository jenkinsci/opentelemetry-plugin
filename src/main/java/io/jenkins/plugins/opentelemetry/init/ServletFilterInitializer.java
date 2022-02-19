/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.servlet.OpenTelemetryServletFilter;
import jenkins.YesNoMaybe;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class ServletFilterInitializer extends OpenTelemetryPluginAbstractInitializer {
    private static final Logger logger = Logger.getLogger(ServletFilterInitializer.class.getName());

    /**
     * TODO better dependency handling
     * Don't start just after `PLUGINS_STARTED` because it creates an initialization problem
     */
    @Initializer(after = InitMilestone.JOB_LOADED)
    public void setUpFilter() {
        // TODO support live reload of the config flag
        boolean jenkinsWebInstrumentationEnabled = Optional.ofNullable(this.openTelemetrySdkProvider.getConfig().getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_WEB_ENABLED)).orElse(true);

        if (jenkinsWebInstrumentationEnabled) {
            Filter filter = new OpenTelemetryServletFilter(this.openTelemetrySdkProvider.getTracer());
            if (PluginServletFilter.hasFilter(filter)) {
                logger.log(Level.INFO, () -> "Jenkins Web instrumentation already enabled");
            } else {
                try {
                    PluginServletFilter.addFilter(filter);
                    logger.log(Level.INFO, () -> "Jenkins Web instrumentation enabled");
                } catch (ServletException ex) {
                    logger.log(Level.WARNING, "Failure to enable Jenkins Web instrumentation", ex);
                }
            }
        } else {
            logger.log(Level.INFO, () -> "Jenkins Web instrumentation disabled");
        }
    }
}
