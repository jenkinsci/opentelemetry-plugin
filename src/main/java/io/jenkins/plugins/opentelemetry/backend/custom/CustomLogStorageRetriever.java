/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.custom;

import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.text.Template;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CustomLogStorageRetriever implements LogStorageRetriever {

    @NonNull
    private final Template buildLogsVisualizationUrlTemplate;

    private final TemplateBindingsProvider templateBindingsProvider;

    public CustomLogStorageRetriever(Template buildLogsVisualizationUrlTemplate, TemplateBindingsProvider templateBindingsProvider) {
        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;
    }

    @NonNull
    @Override
    public LogsQueryResult overallLog(@NonNull String jobFullName, @NonNull int runNumber, @NonNull String traceId, @NonNull String spanId, boolean complete) throws IOException {
        return getLogsQueryResult(traceId, spanId);
    }

    @NonNull
    @Override
    public LogsQueryResult stepLog(@NonNull String jobFullName, @NonNull int runNumber, @NonNull String flowNodeId, @NonNull String traceId, @NonNull String spanId, boolean complete) throws IOException {
        return getLogsQueryResult(traceId, spanId);
    }

    @NonNull
    private LogsQueryResult getLogsQueryResult(@NonNull String traceId, @NonNull String spanId) {

        Map<String, Object> localBindings = new HashMap<>();
        localBindings.put(ObservabilityBackend.TemplateBindings.TRACE_ID, traceId);
        localBindings.put(ObservabilityBackend.TemplateBindings.SPAN_ID, spanId);
        localBindings.put(ObservabilityBackend.TemplateBindings.START_TIME, ZonedDateTime.now().minusMonths(1).toInstant()); // FIXME implement
        localBindings.put(ObservabilityBackend.TemplateBindings.END_TIME, ZonedDateTime.now().toInstant()); // FIXME implement

        Map<String, Object> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
        String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

        String backendName = Objects.toString(bindings.get(ObservabilityBackend.TemplateBindings.BACKEND_NAME));
        String backend24x24IconUrl = Objects.toString(bindings.get(ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL));
        return new LogsQueryResult(
            new ByteBuffer(),
            new LogsViewHeader(
                backendName,
                logsVisualizationUrl,
                backend24x24IconUrl),
            StandardCharsets.UTF_8,
            true);
    }

    @Override
    public String toString() {
        return "CustomLogStorageRetriever{" +
            "urlTemplate=" + buildLogsVisualizationUrlTemplate +
            '}';
    }

    @Override
    public void close() throws Exception {

    }
}
