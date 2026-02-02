/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.administrativemonitor;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import java.io.IOException;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.interceptor.RequirePOST;

@Extension
public class ElasticBackendDashboardNotEnabledAdministrativeMonitor extends AdministrativeMonitor {

    private JenkinsOpenTelemetryPluginConfiguration pluginConfiguration;

    @Override
    public String getDisplayName() {
        return "OpenTelemetry - Elastic - Kibana dashboard link not enabled";
    }

    @Override
    public boolean isActivated() {
        return pluginConfiguration.getObservabilityBackends().stream()
                .filter(backend -> backend instanceof ElasticBackend)
                .map(backend -> (ElasticBackend) backend)
                .filter(backend -> StringUtils.isNotEmpty(backend.getKibanaBaseUrl()))
                .anyMatch(elasticBackend -> !elasticBackend.isDisplayKibanaDashboardLink());
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @RequirePOST
    public HttpResponse doAct(StaplerRequest2 req, StaplerResponse2 rsp) throws IOException {
        if (req.hasParameter("no")) {
            disable(true);
        }
        return HttpResponses.redirectViaContextPath("/configure");
    }

    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(
            JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        this.pluginConfiguration = jenkinsOpenTelemetryPluginConfiguration;
    }
}
