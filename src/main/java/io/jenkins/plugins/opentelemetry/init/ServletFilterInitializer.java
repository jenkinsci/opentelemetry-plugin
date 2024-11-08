/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.util.PluginServletFilter;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.jenkins.plugins.opentelemetry.servlet.StaplerInstrumentationServletFilter;
import io.jenkins.plugins.opentelemetry.servlet.TraceContextServletFilter;
import jenkins.YesNoMaybe;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO Register the {@link StaplerInstrumentationServletFilter} earlier in the chain of {@link Filter} of the Jenkins webapp,
 * register it before the {@link hudson.security.HudsonFilter} so that the {@link io.jenkins.plugins.opentelemetry.security.AuditingSecurityListener}
 * events can be associated to an HTTP trace.
 */
@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class ServletFilterInitializer implements OpenTelemetryLifecycleListener {
    private static final Logger logger = Logger.getLogger(ServletFilterInitializer.class.getName());

    @Inject
    TraceContextServletFilter traceContextServletFilter;

    @Inject
    StaplerInstrumentationServletFilter staplerInstrumentationServletFilter;

    @PostConstruct
    public void postConstruct() {
        addToPluginServletFilter(traceContextServletFilter);
        addToPluginServletFilter(staplerInstrumentationServletFilter);
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

}
