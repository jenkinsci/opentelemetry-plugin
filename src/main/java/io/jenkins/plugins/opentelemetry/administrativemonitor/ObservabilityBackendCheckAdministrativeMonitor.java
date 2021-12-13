/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.administrativemonitor;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import java.io.IOException;

@Extension
public class ObservabilityBackendCheckAdministrativeMonitor extends AdministrativeMonitor {

    JenkinsOpenTelemetryPluginConfiguration pluginConfiguration;

    @Override
    public boolean isActivated() {
        boolean pluginConfiguredToPublishData = StringUtils.isNotBlank(pluginConfiguration.getEndpoint());
        return pluginConfiguredToPublishData && pluginConfiguration.getObservabilityBackends().isEmpty();
    }

    @Override
    public String getDisplayName() {
        return "OpenTelemetry - No Observability Backend Defined";
    }

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        this.pluginConfiguration = jenkinsOpenTelemetryPluginConfiguration;
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @RequirePOST
    public HttpResponse doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.hasParameter("no")) {
            disable(true);
            return HttpResponses.redirectToDot();
        } else {
            return HttpResponses.redirectViaContextPath("/configure");
        }
    }
}
