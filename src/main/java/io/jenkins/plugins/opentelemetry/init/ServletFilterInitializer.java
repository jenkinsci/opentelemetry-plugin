/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.util.PluginServletFilter;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.servlet.OpenTelemetryServletFilter;
import io.opentelemetry.api.events.EventEmitter;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO Register the {@link OpenTelemetryServletFilter} earlier in the chain of {@link Filter} of the Jenkins webapp,
 * register it before the {@link hudson.security.HudsonFilter} so that the {@link io.jenkins.plugins.opentelemetry.security.AuditingSecurityListener}
 * events can be associated to an HTTP trace.
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class ServletFilterInitializer implements OtelComponent {
    private static final Logger logger = Logger.getLogger(ServletFilterInitializer.class.getName());

    OpenTelemetryServletFilter openTelemetryServletFilter;
    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventEmitter eventEmitter, Tracer tracer, ConfigProperties configProperties) {

        // TODO support live reload of the config flag
        boolean jenkinsWebInstrumentationEnabled = Optional.ofNullable(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_WEB_ENABLED)).orElse(true);

        if (jenkinsWebInstrumentationEnabled) {
            openTelemetryServletFilter = new OpenTelemetryServletFilter(tracer);
            if (PluginServletFilter.hasFilter(openTelemetryServletFilter)) {
                logger.log(Level.INFO, () -> "Jenkins Web instrumentation already enabled");
            } else {
                try {
                    PluginServletFilter.addFilter(openTelemetryServletFilter);
                    logger.log(Level.FINE, () -> "Jenkins Web instrumentation enabled");
                } catch (ServletException ex) {
                    logger.log(Level.WARNING, "Failure to enable Jenkins Web instrumentation", ex);
                }
            }
        } else {
            logger.log(Level.INFO, () -> "Jenkins Web instrumentation disabled");
        }
    }

    @Override
    public void beforeSdkShutdown() {
        try {
            PluginServletFilter.removeFilter(openTelemetryServletFilter);
        } catch (ServletException e) {
            logger.log(Level.INFO, "Exception removing OpenTelemetryServletFilter", e);
        }
    }
}
