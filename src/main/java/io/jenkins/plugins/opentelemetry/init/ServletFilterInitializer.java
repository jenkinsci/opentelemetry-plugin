/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.init;

import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.PluginServletFilter;
import io.jenkins.plugins.opentelemetry.servlet.OpenTelemetryServletFilter;
import jenkins.YesNoMaybe;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class ServletFilterInitializer extends OpenTelemetryPluginAbstractInitializer {
    private static Logger LOGGER = Logger.getLogger(ServletFilterInitializer.class.getName());

    /**
     * TODO better dependency handling
     * Don't start just after `PLUGINS_STARTED` because it creates an initialization problem
     */
    @Initializer(after = InitMilestone.JOB_LOADED)
    public void setUpFilter() {
        Filter filter = new OpenTelemetryServletFilter(this.openTelemetrySdkProvider.getTracer());
        if (!PluginServletFilter.hasFilter(filter)) {
            try {
                PluginServletFilter.addFilter(filter);
            } catch (ServletException ex) {
                LOGGER.log(Level.WARNING, "Failed to set up languages servlet filter", ex);
            }
        }
    }
}
