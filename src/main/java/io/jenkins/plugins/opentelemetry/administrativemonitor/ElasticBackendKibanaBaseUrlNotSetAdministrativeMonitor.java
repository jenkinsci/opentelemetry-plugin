/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.administrativemonitor;

import hudson.Extension;
import hudson.model.AdministrativeMonitor;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import javax.inject.Inject;
import java.io.IOException;

@Extension
public class ElasticBackendKibanaBaseUrlNotSetAdministrativeMonitor extends AdministrativeMonitor {

    private JenkinsOpenTelemetryPluginConfiguration pluginConfiguration;

    @Override
    public String getDisplayName() {
        return "OpenTelemetry - Elastic - Kibana base URL not set";
    }

    @Override
    public boolean isActivated() {
        return pluginConfiguration.getObservabilityBackends().stream().
            filter(backend -> backend instanceof ElasticBackend).
            map(backend -> (ElasticBackend) backend)
            .anyMatch(backend -> StringUtils.isEmpty(backend.getKibanaBaseUrl()));
    }

    /**
     * Depending on whether the user said "yes" or "no", send him to the right place.
     */
    @RequirePOST
    public HttpResponse doAct(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.hasParameter("no")) {
            disable(true);
        }
        return HttpResponses.redirectViaContextPath("/configure");
    }


    @Inject
    public void setJenkinsOpenTelemetryPluginConfiguration(JenkinsOpenTelemetryPluginConfiguration jenkinsOpenTelemetryPluginConfiguration) {
        this.pluginConfiguration = jenkinsOpenTelemetryPluginConfiguration;
    }
}
