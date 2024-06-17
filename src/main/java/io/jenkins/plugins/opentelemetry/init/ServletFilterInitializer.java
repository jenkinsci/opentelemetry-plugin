/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.util.PluginServletFilter;
import io.jenkins.plugins.opentelemetry.OtelComponent;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.jenkins.plugins.opentelemetry.servlet.StaplerInstrumentationServletFilter;
import io.jenkins.plugins.opentelemetry.servlet.TraceContextServletFilter;
import io.opentelemetry.api.incubator.events.EventLogger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO Register the {@link StaplerInstrumentationServletFilter} earlier in the chain of {@link Filter} of the Jenkins webapp,
 * register it before the {@link hudson.security.HudsonFilter} so that the {@link io.jenkins.plugins.opentelemetry.security.AuditingSecurityListener}
 * events can be associated to an HTTP trace.
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class ServletFilterInitializer implements OtelComponent {
    private static final Logger logger = Logger.getLogger(ServletFilterInitializer.class.getName());

    StaplerInstrumentationServletFilter staplerInstrumentationServletFilter;

    TraceContextServletFilter traceContextServletFilter;

    @Override
    public void afterSdkInitialized(Meter meter, LoggerProvider loggerProvider, EventLogger eventLogger, Tracer tracer, ConfigProperties configProperties) {

        boolean jenkinsRemoteSpanEnabled = Optional.ofNullable(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_REMOTE_SPAN_ENABLED)).orElse(false);

        if (jenkinsRemoteSpanEnabled) {
            traceContextServletFilter = new TraceContextServletFilter();
            addToPluginServletFilter(traceContextServletFilter);
        } else {
            logger.log(Level.INFO, () -> "Jenkins Remote Span disabled");
        }
        // TODO support live reload of the config flag
        boolean jenkinsWebInstrumentationEnabled = Optional.ofNullable(configProperties.getBoolean(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_JENKINS_WEB_ENABLED)).orElse(true);

        if (jenkinsWebInstrumentationEnabled) {
            List<String> capturedRequestParameters = configProperties.getList(JenkinsOtelSemanticAttributes.OTEL_INSTRUMENTATION_SERVLET_CAPTURE_REQUEST_PARAMETERS, Collections.emptyList());
            staplerInstrumentationServletFilter = new StaplerInstrumentationServletFilter(capturedRequestParameters, tracer);
            addToPluginServletFilter(staplerInstrumentationServletFilter);
        } else {
            logger.log(Level.INFO, () -> "Jenkins Web instrumentation disabled");
        }


    }

    private void addToPluginServletFilter(Filter filter) {
        if (PluginServletFilter.hasFilter(filter)) {
            logger.log(Level.INFO, () -> filter.getClass().getName() + " already enabled");
        } else {
            try {
                PluginServletFilter.addFilter(filter);
                logger.log(Level.FINE, () -> filter.getClass().getName() + " enabled");
            } catch (ServletException ex) {
                logger.log(Level.WARNING, "Failure to enable " + filter.getClass().getName(), ex);
            }
        }
    }

    @Override
    public void beforeSdkShutdown() {
        try {
            PluginServletFilter.removeFilter(staplerInstrumentationServletFilter);
            PluginServletFilter.removeFilter(traceContextServletFilter);
        } catch (ServletException e) {
            logger.log(Level.INFO, "Exception removing OpenTelemetryServletFilter", e);
        }
    }
}
