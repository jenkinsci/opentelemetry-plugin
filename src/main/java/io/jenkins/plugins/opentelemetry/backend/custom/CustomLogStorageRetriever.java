/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.custom;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import groovy.text.Template;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.kohsuke.stapler.framework.io.ByteBuffer;

/**
 * Log storage retriever that only returns a logs visualization link and no inline log content.
 */
public class CustomLogStorageRetriever implements LogStorageRetriever {

    @NonNull
    private final Template buildLogsVisualizationUrlTemplate;

    @NonNull
    private final TemplateBindingsProvider templateBindingsProvider;

        /**
         * Creates a retriever backed by custom URL templates.
         *
         * @param buildLogsVisualizationUrlTemplate template that renders the logs visualization URL
         * @param templateBindingsProvider provider for additional template bindings
         */
    public CustomLogStorageRetriever(
            @NonNull Template buildLogsVisualizationUrlTemplate,
            @NonNull TemplateBindingsProvider templateBindingsProvider) {
        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;
    }

        /**
         * Returns the synthetic overall-log query result with generated visualization metadata.
         *
         * @param jobFullName Jenkins job full name
         * @param runNumber build number
         * @param traceId OpenTelemetry trace identifier
         * @param spanId OpenTelemetry span identifier
         * @param complete whether the build is complete
         * @param startTime build start time
         * @param endTime build end time, or {@code null}
         * @return synthetic query result containing only header/link metadata
         */
    @NonNull
    @Override
    public LogsQueryResult overallLog(
            @NonNull String jobFullName,
            int runNumber,
            @NonNull String traceId,
            @NonNull String spanId,
            boolean complete,
            @NonNull Instant startTime,
            Instant endTime) {
        return getLogsQueryResult(traceId, spanId, startTime, endTime);
    }

    @NonNull
    @Override
    public LogsQueryResult stepLog(
            @NonNull String jobFullName,
            int runNumber,
            @NonNull String flowNodeId,
            @NonNull String traceId,
            @NonNull String spanId,
            boolean complete,
            @NonNull Instant startTime,
            @Nullable Instant endTime) {
        return getLogsQueryResult(traceId, spanId, startTime, endTime);
    }

    @NonNull
    private LogsQueryResult getLogsQueryResult(
            @NonNull String traceId, @NonNull String spanId, @NonNull Instant startTime, @Nullable Instant endTime) {

        Map<String, Object> localBindings = new HashMap<>();
        localBindings.put(ObservabilityBackend.TemplateBindings.TRACE_ID, traceId);
        localBindings.put(ObservabilityBackend.TemplateBindings.SPAN_ID, spanId);
        localBindings.put(ObservabilityBackend.TemplateBindings.START_TIME, startTime);
        localBindings.put(
                ObservabilityBackend.TemplateBindings.END_TIME,
                Optional.ofNullable(endTime).orElseGet(Instant::now));

        Map<String, Object> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings)
                .getBindings();
        String logsVisualizationUrl =
                this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

        String backendName = Objects.toString(bindings.get(ObservabilityBackend.TemplateBindings.BACKEND_NAME));
        String backend24x24IconUrl =
                Objects.toString(bindings.get(ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL));
        return new LogsQueryResult(
                new ByteBuffer(),
                new LogsViewHeader(backendName, logsVisualizationUrl, backend24x24IconUrl),
                StandardCharsets.UTF_8,
                true);
    }

    @Override
    public String toString() {
        return "CustomLogStorageRetriever{" + "urlTemplate=" + buildLogsVisualizationUrlTemplate + '}';
    }

    @Override
    public void close() throws Exception {}
}
