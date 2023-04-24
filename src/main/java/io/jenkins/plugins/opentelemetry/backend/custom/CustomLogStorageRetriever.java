/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.custom;

import groovy.text.Template;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.job.log.LogsViewHeader;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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
    private LogsQueryResult getLogsQueryResult(@NonNull String traceId, @NonNull String spanId) throws IOException {

        Map<String, String> localBindings = new HashMap<>();
        localBindings.put("traceId", traceId);
        localBindings.put("spanId", spanId);

        Map<String, String> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, localBindings).getBindings();
        String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();

        String backendName = bindings.get(ObservabilityBackend.TemplateBindings.BACKEND_NAME);
        String backend24x24IconUrl = bindings.get(ObservabilityBackend.TemplateBindings.BACKEND_24_24_ICON_URL);
        return new LogsQueryResult(new ByteBuffer(), new LogsViewHeader(backendName, logsVisualizationUrl, backend24x24IconUrl), StandardCharsets.UTF_8, true);
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
