/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend;

import groovy.text.GStringTemplateEngine;
import groovy.text.Template;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryContext;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class CustomLogStorageRetriever implements LogStorageRetriever<CustomLogStorageRetriever.CustomLogsQueryContext> {

    private final Template pipelineLogsVisualizationUrlTemplate;
    private final String visualizationTitle;

    public CustomLogStorageRetriever(String pipelineLogsVisualizationUrlTemplate, String visualizationTitle) {
        GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
        try {
            this.pipelineLogsVisualizationUrlTemplate = gStringTemplateEngine.createTemplate(pipelineLogsVisualizationUrlTemplate);
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        this.visualizationTitle = visualizationTitle;
    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable CustomLogStorageRetriever.CustomLogsQueryContext logsQueryContext) throws IOException {
        return getLogsQueryResult(traceId, spanId);
    }

    @Nonnull
    @Override
    public LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable CustomLogStorageRetriever.CustomLogsQueryContext logsQueryContext) throws IOException {
        return getLogsQueryResult(traceId, spanId);
    }

    @Nonnull
    private LogsQueryResult getLogsQueryResult(@Nonnull String traceId, @Nonnull String spanId) throws IOException {
        ByteBuffer byteBuffer = new ByteBuffer();

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("traceId", traceId);
        bindings.put("spanId", spanId);

        String url = pipelineLogsVisualizationUrlTemplate.make(bindings).toString();
        String out = "[view in <a href=\"" + url + "\" target=\"_blank\">" + visualizationTitle + "</a>\n";
        byteBuffer.write(out.getBytes(StandardCharsets.UTF_8));

        return new LogsQueryResult(byteBuffer, StandardCharsets.UTF_8, true, new CustomLogsQueryContext());
    }


    public static class CustomLogsQueryContext implements LogsQueryContext {

    }
}
