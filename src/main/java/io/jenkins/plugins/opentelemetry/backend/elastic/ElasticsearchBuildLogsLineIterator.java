/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.job.log.LogLine;
import io.jenkins.plugins.opentelemetry.job.log.util.LogLineIterator;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;

/**
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.17/point-in-time-api.html
 */
public class ElasticsearchBuildLogsLineIterator implements LogLineIterator<Long>, Closeable {
    private static final Logger logger = Logger.getLogger(ElasticsearchBuildLogsLineIterator.class.getName());

    public static final Time POINT_IN_TIME_KEEP_ALIVE = Time.of(builder -> builder.time("30s"));
    public static final int PAGE_SIZE = 200;
    public static final int MAX_LINES_PAGINATED = 10_000;

    final String jobFullName;
    final int runNumber;
    long lineNumber;

    @Nullable
    final String flowNodeId;

    final String traceId;
    final ElasticsearchClient esClient;
    final Tracer tracer;
    String pointInTimeId;
    boolean enableEDOT;

    @VisibleForTesting
    int queryCounter;

    Iterator<LogLine<Long>> delegate;
    boolean endOfStream;

    List<FieldValue> lastSortValues;

    public ElasticsearchBuildLogsLineIterator(
            @NonNull String jobFullName,
            int runNumber,
            @NonNull String traceId,
            @NonNull ElasticsearchClient esClient,
            @NonNull Tracer tracer) {
        this(jobFullName, runNumber, traceId, null, esClient, tracer);
        setEDOTMode();
    }

    public ElasticsearchBuildLogsLineIterator(
            @NonNull String jobFullName,
            int runNumber,
            @NonNull String traceId,
            @Nullable String flowNodeId,
            @NonNull ElasticsearchClient esClient,
            @NonNull Tracer tracer) {
        this.tracer = tracer;
        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.traceId = traceId;
        this.flowNodeId = flowNodeId;
        this.esClient = esClient;
        setEDOTMode();
    }

    /**
     * Set the EDOT mode based on the backend configuration.
     */
    private void setEDOTMode() {
        if (ElasticBackend.get().isPresent()) {
            this.enableEDOT = ElasticBackend.get().get().isEnableEDOT();
        } else {
            this.enableEDOT = false;
        }
    }

    String lazyLoadPointInTimeId() throws IOException {
        if (pointInTimeId == null) {
            Span esOpenPitSpan = tracer.spanBuilder("ElasticsearchLogsSearchIterator.openPointInTime")
                    .setAttribute("query.index", ElasticsearchFields.INDEX_TEMPLATE_PATTERNS)
                    .setAttribute("query.keepAlive", POINT_IN_TIME_KEEP_ALIVE.time())
                    .startSpan();
            try (Scope ignored = esOpenPitSpan.makeCurrent()) {
                pointInTimeId = esClient.openPointInTime(pit -> pit.index(ElasticsearchFields.INDEX_TEMPLATE_PATTERNS)
                                .keepAlive(POINT_IN_TIME_KEEP_ALIVE))
                        .id();
                esOpenPitSpan.setAttribute("pitId", pointInTimeId);
            } finally {
                esOpenPitSpan.end();
            }
        }
        return pointInTimeId;
    }

    @NonNull
    Iterator<LogLine<Long>> getCurrentIterator() {
        try {
            if (endOfStream) {
                // don't try to load more
                return delegate;
            }
            if (delegate == null) {
                delegate = loadNextFormattedLogLines();
            }
            if (delegate.hasNext()) {
                return delegate;
            }
            delegate = loadNextFormattedLogLines();
            if (!delegate.hasNext()) {
                endOfStream = true;
            }
            return delegate;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE)
                ? this.tracer
                : TracerProvider.noop().get("noop");
        SpanBuilder spanBuilder = tracer.spanBuilder("ElasticsearchBuildLogsLineIterator.close")
                .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, jobFullName)
                .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
                .setAttribute("pointInTimeId", pointInTimeId);
        if (flowNodeId != null) {
            spanBuilder.setAttribute(ExtendedJenkinsAttributes.JENKINS_STEP_ID, flowNodeId);
        }
        Span closeSpan = spanBuilder.startSpan();
        try (Scope ignored = closeSpan.makeCurrent()) {
            if (pointInTimeId != null) {
                Span esClosePitSpan = this.tracer
                        .spanBuilder("Elasticsearch.closePointInTime")
                        .setAttribute("query.pointInTimeId", pointInTimeId)
                        .startSpan();
                try (Scope ignored2 = esClosePitSpan.makeCurrent()) {
                    esClient.closePointInTime(builder -> builder.id(pointInTimeId));
                    lastSortValues = null;
                } finally {
                    esClosePitSpan.end();
                    pointInTimeId = null;
                }
            }
        } finally {
            closeSpan.end();
        }
    }

    @Override
    public boolean hasNext() {
        return getCurrentIterator().hasNext();
    }

    @Override
    public LogLine<Long> next() {
        return getCurrentIterator().next();
    }

    protected Iterator<LogLine<Long>> loadNextFormattedLogLines() throws IOException {
        if (queryCounter == Integer.MAX_VALUE) {
            logger.log(Level.INFO, () -> "Skip more than Integer.MAX_VALUE pages, return empty result");
            return Collections.emptyIterator();
        }
        String loadPointInTimeId = this.lazyLoadPointInTimeId();

        Span esSearchSpan =
                tracer.spanBuilder("ElasticsearchLogsSearchIterator.search").startSpan();
        try (Scope ignoredEsSearchSpanScope = esSearchSpan.makeCurrent()) {
            esSearchSpan
                    .setAttribute("query.pointInTimeId", lazyLoadPointInTimeId())
                    .setAttribute("query.from", queryCounter)
                    .setAttribute("query.size", PAGE_SIZE)
                    .setAttribute("query.match.traceId", traceId)
                    .setAttribute("query.match.jobFullName", jobFullName)
                    .setAttribute("query.match.runNumber", runNumber);

            Query query = getQuery(esSearchSpan);

            SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
                    .pit(pit -> pit.id(loadPointInTimeId).keepAlive(POINT_IN_TIME_KEEP_ALIVE))
                    .size(PAGE_SIZE)
                    .sort(s -> s.field(
                            f -> f.field(ElasticsearchFields.FIELD_TIMESTAMP).order(SortOrder.Asc)))
                    .query(query);

            if (lastSortValues != null) {
                searchRequestBuilder.searchAfter(lastSortValues);
            }
            SearchRequest searchRequest = searchRequestBuilder.build();

            SearchResponse<ObjectNode> searchResponse = this.esClient.search(searchRequest, ObjectNode.class);

            List<Hit<ObjectNode>> hits = searchResponse.hits().hits();
            esSearchSpan.setAttribute("response.size", hits.size());
            if (!hits.isEmpty()) {
                this.lineNumber += hits.size();
                this.lastSortValues = hits.get(hits.size() - 1).sort();
            } else {
                endOfStream = true;
            }
            return hits.stream()
                    .map(new ElasticsearchHitToFormattedLogLine(getAttributesField()))
                    .filter(Objects::nonNull)
                    .iterator();
        } catch (ElasticsearchException e) {
            esSearchSpan.recordException(e);
            throw e;
        } finally {
            esSearchSpan.end();
            queryCounter++;
        }
    }

    private String getAttributesField() {
        return this.enableEDOT ? "attributes" : "labels";
    }

    private Query getQuery(Span esSearchSpan) {
        String fieldTraceID = ElasticsearchFields.FIELD_TRACE_ID;
        String fieldJobFullName = ExtendedJenkinsAttributes.CI_PIPELINE_ID.getKey();
        String fieldRunNumber = ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER.getKey();
        String fieldFlowNodeId = ExtendedJenkinsAttributes.JENKINS_STEP_ID.getKey();
        // Legacy APM ingestion
        if (!enableEDOT) {
            fieldJobFullName = ElasticsearchFields.LEGACY_FIELD_CI_PIPELINE_ID;
            fieldRunNumber = ElasticsearchFields.LEGACY_FIELD_CI_PIPELINE_RUN_NUMBER;
            fieldFlowNodeId = ElasticsearchFields.LEGACY_FIELD_JENKINS_STEP_ID;
        }
        BoolQuery.Builder queryBuilder = QueryBuilders.bool()
                .must(
                        QueryBuilders.match()
                                .field(fieldTraceID)
                                .query(FieldValue.of(traceId))
                                .build()
                                ._toQuery(),
                        QueryBuilders.match()
                                .field(fieldJobFullName)
                                .query(FieldValue.of(jobFullName))
                                .build()
                                ._toQuery(),
                        QueryBuilders.match()
                                .field(fieldRunNumber)
                                .query(FieldValue.of(runNumber))
                                .build()
                                ._toQuery());
        if (flowNodeId != null) {
            esSearchSpan.setAttribute("query.match.flowNodeId", flowNodeId);
            queryBuilder.must(QueryBuilders.match()
                    .field(fieldFlowNodeId)
                    .query(FieldValue.of(flowNodeId))
                    .build()
                    ._toQuery());
        }
        Query query = queryBuilder.build()._toQuery();
        return query;
    }

    static class ElasticsearchHitToFormattedLogLine implements Function<Hit<ObjectNode>, LogLine<Long>> {
        private String annotationsField;

        public ElasticsearchHitToFormattedLogLine(String annotationsField) {
            this.annotationsField = annotationsField;
        }

        /**
         * Returns the formatted log line or {@code null} if the given Elasticsearch
         * document doesn't contain a {@code message} field.
         */
        @Nullable
        @Override
        public LogLine<Long> apply(Hit<ObjectNode> hit) {
            ObjectNode source = hit.source();
            if (source == null) {
                logger.log(Level.FINE, () -> "Skip log with no source (document id: " + hit.id() + ")");
                return null;
            }
            String message = extractMessage(source);
            ObjectNode labels = (ObjectNode) source.findValue(this.annotationsField);
            String annotatedMessage = composeAnnotatedMessage(message, labels);
            JsonNode timestampAsJsonNode = source.findValue(ElasticsearchFields.FIELD_TIMESTAMP);
            if (timestampAsJsonNode == null) {
                logger.log(Level.FINE, () -> "Skip log with no timestamp (document id: " + hit.id() + ")");
                return null;
            }
            long timestamp =
                    java.time.Instant.parse(timestampAsJsonNode.asText()).toEpochMilli();
            LogLine<Long> logLine = new LogLine<Long>(timestamp, annotatedMessage);
            logger.log(Level.FINEST, () -> "Write: " + logLine + " for document.id: " + hit.id());
            return logLine;
        }

        /**
         * Compose the message with the Jenkins annotations.
         * @param message
         * @param labels
         * @return the message with the Jenkins annotations
         */
        private String composeAnnotatedMessage(@NonNull String message, @Nullable ObjectNode labels) {
            JSONArray annotations;
            if (labels == null) {
                annotations = null;
            } else {
                JsonNode annotationsAsText = labels.get(ExtendedJenkinsAttributes.JENKINS_ANSI_ANNOTATIONS.getKey());
                if (annotationsAsText == null) {
                    annotations = null;
                } else {
                    annotations = JSONArray.fromObject(annotationsAsText.asText());
                }
            }
            String annotatedMessage = ConsoleNotes.readFormattedMessage(message, annotations);
            return annotatedMessage;
        }

        /**
         * Extracts the message from the given Elasticsearch document.
         *
         * @param source the Elasticsearch document
         * @return the message or {@code null} if not found
         */
        @Nullable
        private String extractMessage(ObjectNode source) {
            JsonNode messageAsJsonNode = source.findValue(ElasticsearchFields.FIELD_MESSAGE);
            String msg = null;
            // Legacy APM ingestion
            if (messageAsJsonNode == null) {
                messageAsJsonNode = source.findValue("message");
            } else {
                messageAsJsonNode = messageAsJsonNode.get("text");
            }
            if (messageAsJsonNode != null) {
                msg = messageAsJsonNode.asText();
            } else {
                logger.log(
                        Level.FINE,
                        () -> "Skip log with no " + ElasticsearchFields.FIELD_MESSAGE + " (document : " + source + ")");
            }
            return msg;
        }
    }

    @Override
    public void skipLines(Long skipLines) {
        Tracer tracer = logger.isLoggable(Level.FINE)
                ? this.tracer
                : TracerProvider.noop().get("noop");
        SpanBuilder spanBuilder = tracer.spanBuilder("ElasticsearchBuildLogsLineIterator.skip")
                .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_ID, jobFullName)
                .setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
                .setAttribute("pointInTimeId", pointInTimeId)
                .setAttribute("skipLines", skipLines);
        Span span = spanBuilder.startSpan();
        try {
            this.lineNumber = skipLines;
            if (this.delegate == null) {
                span.setAttribute("skippedLines", -1);
            } else {
                /*
                 * Happens when invoked by:
                 * GET /job/:jobFullName/:runNumber/consoleText
                 * |- org.jenkinsci.plugins.workflow.job.WorkflowRun.doConsoleText
                 * |- io.jenkins.plugins.opentelemetry.job.log.OverallLog.writeLogTo(long,
                 * java.io.OutputStream)
                 * |- org.kohsuke.stapler.framework.io.LargeText.writeLogTo(long,
                 * java.io.OutputStream)
                 * GET
                 * /blue/rest/organizations/:organization/pipelines/:pipeline/branches/:branch/
                 * runs/:runNumber/log?start=0
                 *
                 * When invoked by "/job/:jobFullName/:runNumber/consoleText", it's the second
                 * call to LargeText.writeLogTo() and it's EOF
                 */
                int counter = 0;
                for (int i = 0; i < skipLines; i++) {
                    if (delegate.hasNext()) {
                        delegate.next();
                        counter++;
                    } else {
                        break;
                    }
                }
                span.setAttribute("skippedLines", counter);
            }
        } finally {
            span.end();
        }
    }
}
