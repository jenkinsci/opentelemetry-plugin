/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Periodically fetches, for every configured {@link GrafanaBackend} that has a
 * {@link GrafanaBackend#getTenantMappingUrl()} set, a JSON mapping of Jenkins root folder name to
 * Grafana org id / Tempo datasource id -- so a multi-tenant Jenkins instance's build pages can link
 * to the correct Grafana org/datasource per folder instead of one fixed global one.
 */
@Extension
public class TenantGrafanaMappingFetcher extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(TenantGrafanaMappingFetcher.class.getName());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public TenantGrafanaMappingFetcher() {
        super("OpenTelemetry Grafana tenant mapping fetch");
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(1);
    }

    @Override
    protected void execute(TaskListener listener) {
        for (ObservabilityBackend backend :
                JenkinsOpenTelemetryPluginConfiguration.get().getObservabilityBackends()) {
            if (!(backend instanceof GrafanaBackend grafanaBackend)) {
                continue;
            }
            String url = grafanaBackend.getTenantMappingUrl();
            if (Strings.isNullOrEmpty(url)) {
                continue;
            }
            try {
                grafanaBackend.refreshTenantMapping(fetchMapping(url));
            } catch (Exception e) {
                // Keep the previous mapping on failure -- a transient hiccup shouldn't wipe out a
                // working one.
                listener.getLogger().println("Failed to refresh Grafana tenant mapping from " + url + ": " + e);
                LOGGER.log(Level.WARNING, "Failed to refresh Grafana tenant mapping from " + url, e);
            }
        }
    }

    private Map<String, GrafanaBackend.TenantGrafanaMapping> fetchMapping(String url)
            throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
        }
        Map<String, GrafanaBackend.TenantGrafanaMapping> mapping = new HashMap<>();
        for (JsonNode entry : OBJECT_MAPPER.readTree(response.body())) {
            mapping.put(
                    entry.get("jenkinsOrg").asText(),
                    new GrafanaBackend.TenantGrafanaMapping(
                            entry.get("grafanaOrgId").asText(), entry.get("tempoDatasourceUid").asText()));
        }
        return mapping;
    }
}
