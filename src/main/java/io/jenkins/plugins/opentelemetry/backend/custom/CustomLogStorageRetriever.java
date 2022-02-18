/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.custom;

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

public class CustomLogStorageRetriever implements LogStorageRetriever<CustomLogStorageRetriever.CustomLogsQueryContext> {

    private final Template messageGTemplate;
    private final Template urlGTemplate;

    public CustomLogStorageRetriever(String messageTemplate, String urlTemplate) {
        GStringTemplateEngine gStringTemplateEngine = new GStringTemplateEngine();
        try {
            this.messageGTemplate = gStringTemplateEngine.createTemplate(messageTemplate);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
        try {
            this.urlGTemplate = gStringTemplateEngine.createTemplate(urlTemplate);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
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
        ByteBuffer byteBuffer = new ByteBuffer();

        Map<String, Object> bindings = new HashMap<>();
        bindings.put("traceId", traceId);
        bindings.put("spanId", spanId);
        String out =  messageGTemplate.make(bindings).toString() + " " + urlGTemplate.make(bindings).toString();
        byteBuffer.write(out.getBytes(StandardCharsets.UTF_8));

        return new LogsQueryResult(byteBuffer, StandardCharsets.UTF_8, true, new CustomLogsQueryContext());
    }


    static class CustomLogsQueryContext implements LogsQueryContext {

    }
}
