/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.custom;

import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryContext;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CustomLogStorageRetriever implements LogStorageRetriever<CustomLogStorageRetriever.CustomLogsQueryContext> {

    @Nonnull
    private final Template buildLogsVisualizationUrlTemplate;

    private final TemplateBindingsProvider templateBindingsProvider;

    public CustomLogStorageRetriever(Template buildLogsVisualizationUrlTemplate, TemplateBindingsProvider templateBindingsProvider) {
        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;
    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(@Nonnull String traceId, @Nonnull String spanId, boolean complete, @Nullable CustomLogsQueryContext logsQueryContext) throws IOException {
        return getLogsQueryResult(traceId, spanId);
    }

    @Nonnull
    @Override
    public LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable CustomLogStorageRetriever.CustomLogsQueryContext logsQueryContext) throws IOException {
        return getLogsQueryResult(traceId, spanId);
    }

    @Nonnull
    private LogsQueryResult getLogsQueryResult(@Nonnull String traceId, @Nonnull String spanId) throws IOException {

        Map<String, String> localBindings = new HashMap<>();
        localBindings.put("traceId", traceId);
        localBindings.put("spanId", spanId);

        Map<String, String> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, Collections.singletonMap("traceId", traceId)).getBindings();
        String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

        String backendName = bindings.get(ObservabilityBackend.TemplateBindings.BACKEND_NAME);
        String backend24x24IconUrl = bindings.get(ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL);
        return new LogsQueryResult(new ByteBuffer(), new LogsViewHeader(backendName, logsVisualizationUrl, backend24x24IconUrl), StandardCharsets.UTF_8, true, new CustomLogsQueryContext());
    }

    @Override
    public String toString() {
        return "CustomLogStorageRetriever{" +
            "urlTemplate=" + buildLogsVisualizationUrlTemplate +
            '}';
    }

    static class CustomLogsQueryContext implements LogsQueryContext {

    }
}
