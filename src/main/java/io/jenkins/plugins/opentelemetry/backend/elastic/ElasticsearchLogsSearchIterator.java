/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import com.google.common.collect.Iterators;
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.Scope;
import net.sf.json.JSONArray;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * https://www.elastic.co/guide/en/elasticsearch/reference/7.17/point-in-time-api.html
 */
public class ElasticsearchLogsSearchIterator implements Iterator<String>, Closeable {
    private final static Logger logger = Logger.getLogger(ElasticsearchLogsSearchIterator.class.getName());

    public static final Time POINT_IN_TIME_KEEP_ALIVE = Time.of(builder -> builder.time("30s"));
    public static final int PAGE_SIZE = 200;
    public final static int MAX_LINES = 10_000;

    final String jobFullName;
    final int runNumber;
    @Nullable
    final String flowNodeId;
    final String traceId;
    final ElasticsearchClient esClient;
    final Tracer tracer;
    final ElasticsearchSearchContext context;
    String pointInTimeId;

    @VisibleForTesting
    final AtomicInteger queryCounter = new AtomicInteger();

    Iterator<String> delegate;
    boolean endOfStream;

    public ElasticsearchLogsSearchIterator(@NonNull String jobFullName, int runNumber, @NonNull String traceId, @Nullable ElasticsearchSearchContext context, @NonNull ElasticsearchClient esClient, @NonNull Tracer tracer) {
        this(jobFullName, runNumber, traceId, null, context, esClient, tracer);
    }

    public ElasticsearchLogsSearchIterator(@NonNull String jobFullName, int runNumber, @NonNull String traceId, @Nullable String flowNodeId, @Nullable ElasticsearchSearchContext context, @NonNull ElasticsearchClient esClient, @NonNull Tracer tracer) {
        this.tracer = tracer;
        this.jobFullName = jobFullName;
        this.runNumber = runNumber;
        this.traceId = traceId;
        this.flowNodeId = flowNodeId;
        if (context == null) {
            this.context = new ElasticsearchSearchContext();
        } else {
            this.context = context;
        }
        this.esClient = esClient;
    }

    String lazyLoadPointInTimeId() throws IOException {
        if (pointInTimeId == null) {
            Span esOpenPitSpan = tracer.spanBuilder("Elasticsearch.openPointInTime")
                .setAttribute("query.index", ElasticsearchFields.INDEX_TEMPLATE_PATTERNS)
                .setAttribute("query.keepAlive", POINT_IN_TIME_KEEP_ALIVE.time())
                .startSpan();
            try (Scope esOpenPitSpanScope = esOpenPitSpan.makeCurrent()) {
                pointInTimeId = esClient.openPointInTime(pit -> pit.index(ElasticsearchFields.INDEX_TEMPLATE_PATTERNS).keepAlive(POINT_IN_TIME_KEEP_ALIVE)).id();
                esOpenPitSpan.setAttribute("pitId", pointInTimeId);
            } finally {
                esOpenPitSpan.end();
            }
        }
        return pointInTimeId;
    }

    @NonNull
    Iterator<String> getCurrentIterator() {
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
            if (context.from > MAX_LINES) {
                delegate = Iterators.concat(delegate, Collections.singleton("...").iterator());
                endOfStream = true;
            } else if (!delegate.hasNext()) {
                endOfStream = true;
            }
            return delegate;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        Tracer tracer = logger.isLoggable(Level.FINE) ? this.tracer : TracerProvider.noop().get("noop");
        SpanBuilder spanBuilder = tracer.spanBuilder("ElasticsearchLogsSearchIterator.close")
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_ID, jobFullName)
            .setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_NUMBER, (long) runNumber)
            .setAttribute("pointInTimeId", pointInTimeId);
        if (flowNodeId != null) {
            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.JENKINS_STEP_ID, flowNodeId);
        }
        Span closeSpan = spanBuilder.startSpan();
        try (Scope closeSpanScope = closeSpan.makeCurrent()) {
            if (pointInTimeId != null) {
                Span esClosePitSpan = this.tracer.spanBuilder("Elasticsearch.closePointInTime")
                    .setAttribute("query.pointInTimeId", pointInTimeId)
                    .startSpan();
                try (Scope scope = esClosePitSpan.makeCurrent()) {
                    esClient.closePointInTime(builder -> builder.id(pointInTimeId));
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
    public String next() {
        return getCurrentIterator().next();
    }

    protected Iterator<String> loadNextFormattedLogLines() throws IOException {
        String loadPointInTimeId = this.lazyLoadPointInTimeId();

        Span esSearchSpan = tracer.spanBuilder("Elasticsearch.search")
            .startSpan();
        try (Scope esSearchSpanScope = esSearchSpan.makeCurrent()) {
            esSearchSpan
                .setAttribute("query.pointInTimeId", lazyLoadPointInTimeId())
                .setAttribute("query.from", context.from)
                .setAttribute("query.size", PAGE_SIZE)
                .setAttribute("query.match.traceId", traceId)
                .setAttribute("query.match.jobFullName", jobFullName)
                .setAttribute("query.match.runNumber", runNumber);

            BoolQuery.Builder queryBuilder = QueryBuilders.bool()
                .must(
                    QueryBuilders.match().field(ElasticsearchFields.FIELD_TRACE_ID).query(FieldValue.of(traceId)).build()._toQuery(),
                    QueryBuilders.match().field(ElasticsearchFields.FIELD_CI_PIPELINE_ID).query(FieldValue.of(jobFullName)).build()._toQuery(),
                    QueryBuilders.match().field(ElasticsearchFields.FIELD_CI_PIPELINE_RUN_NUMBER).query(FieldValue.of(runNumber)).build()._toQuery()
                );
            if (flowNodeId != null) {
                esSearchSpan.setAttribute("query.match.flowNodeId", flowNodeId);
                queryBuilder.must(QueryBuilders.match().field(ElasticsearchFields.FIELD_JENKINS_STEP_ID).query(FieldValue.of(flowNodeId)).build()._toQuery());
            }
            Query query = queryBuilder.build()._toQuery();

            SearchRequest searchRequest = new SearchRequest.Builder()
                .pit(pit -> pit.id(loadPointInTimeId).keepAlive(POINT_IN_TIME_KEEP_ALIVE))
                .from(context.from)
                .size(PAGE_SIZE)
                .sort(s -> s.field(f -> f.field(ElasticsearchFields.FIELD_TIMESTAMP).order(SortOrder.Asc)))
                .query(query)
                .build();

            SearchResponse<ObjectNode> searchResponse = this.esClient.search(searchRequest, ObjectNode.class);

            List<Hit<ObjectNode>> hits = searchResponse.hits().hits();
            esSearchSpan.setAttribute("response.size", hits.size());
            context.from = context.from + hits.size();
            return hits.stream().map(new ElasticsearchHitToFormattedLogLine()).filter(Objects::nonNull).iterator();
        } finally {
            esSearchSpan.end();
            queryCounter.incrementAndGet();
        }
    }

    static class ElasticsearchHitToFormattedLogLine implements Function<Hit<ObjectNode>, String> {
        /**
         * Returns the formatted log line or {@code null} if the given Elasticsearch document doesn't contain a {@code message} field.
         */
        @Nullable
        @Override
        public String apply(Hit<ObjectNode> hit) {
            ObjectNode source = hit.source();
            JsonNode messageAsJsonNode = source.findValue(ElasticsearchFields.FIELD_MESSAGE);
            if (messageAsJsonNode == null) {
                logger.log(Level.FINE, () -> "Skip log with no message (document id: " + hit.id() + ")");
                return null;
            }
            ObjectNode labels = (ObjectNode) source.findValue("labels");

            String message = messageAsJsonNode.asText();
            JSONArray annotations;
            if (labels == null) {
                annotations = null;
            } else {
                JsonNode annotationsAsText = labels.get(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS.getKey());
                if (annotationsAsText == null) {
                    annotations = null;
                } else {
                    annotations = JSONArray.fromObject(annotationsAsText.asText());
                }
            }
            String formattedMessage = ConsoleNotes.readFormattedMessage(message, annotations);
            logger.log(Level.FINER, () -> "Write: " + formattedMessage + " for document.id: " + hit.id());
            return formattedMessage;
        }
    }
}
