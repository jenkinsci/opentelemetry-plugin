/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hudson.console.HyperlinkNote;
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import net.sf.json.JSONArray;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Retrieve the logs from Elasticsearch.
 * FIXME graceful shutdown
 */
public class ElasticsearchLogStorageRetriever implements LogStorageRetriever<ElasticsearchLogsQueryContext> {
    public static final String FIELD_TIMESTAMP = "@timestamp";
    public static final String FIELD_TRACE_ID = "trace.id";
    public static final Time POINT_IN_TIME_KEEP_ALIVE = Time.of(builder -> builder.time("30s"));
    public static final int PAGE_SIZE = 100; // FIXME
    public static final String INDEX_TEMPLATE_PATTERNS = "logs-apm.app-*";
    public static final String INDEX_TEMPLATE_NAME = "logs-apm.app";

    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageRetriever.class.getName());

    /**
     * Space aware kibana base URL
     */
    @Nonnull
    private final String kibanaBaseUrl;

    @Nonnull
    final transient ElasticsearchClient esClient;

    final transient Tracer tracer;

    /**
     * TODO verify unsername:password auth vs apiKey auth
     */
    public ElasticsearchLogStorageRetriever(String elasticsearchUrl, Credentials elasticsearchCredentials, String kibanaBaseUrl, Tracer tracer) {
        if (StringUtils.isBlank(elasticsearchUrl)) {
            throw new IllegalArgumentException("Elasticsearch url cannot be blank");
        }

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, elasticsearchCredentials);

        RestClient restClient = RestClient.builder(HttpHost.create(elasticsearchUrl))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.esClient = new ElasticsearchClient(elasticsearchTransport);
        this.tracer = tracer;

        this.kibanaBaseUrl = kibanaBaseUrl;
    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(@Nonnull String traceId, @Nonnull String spanId, boolean complete, @Nullable ElasticsearchLogsQueryContext context) throws IOException {
        // https://www.elastic.co/guide/en/elasticsearch/reference/7.17/point-in-time-api.html
        Span span = tracer.spanBuilder("elasticsearch.search")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {

            final Charset charset = StandardCharsets.UTF_8;
            final boolean completed;
            final List<Hit<ObjectNode>> hits;

            final String pitId;
            final int pageNo;

            if (context == null) {
                // Initial request: open a point in time to have consistent pagination results
                pitId = esClient.openPointInTime(pit -> pit.index(INDEX_TEMPLATE_PATTERNS).keepAlive(POINT_IN_TIME_KEEP_ALIVE)).id();
                pageNo = 0;
            } else if (context.pitId == null) { // FIXME verify this behaviour
                logger.log(Level.FINE, () -> "Reset Elasticsearch query for unexpected closed Point In Time");
                span.setAttribute("info", "Reset Elasticsearch query for unexpected closed Point In Time");
                pitId = esClient.openPointInTime(pit -> pit.index(INDEX_TEMPLATE_PATTERNS).keepAlive(POINT_IN_TIME_KEEP_ALIVE)).id();
                pageNo = 0;
            } else if (complete) {
                // FIXME check algorithm
                // Get PIT id from context but reset the page number because complete=true
                pitId = context.pitId;
                pageNo = 0;
            } else {
                // Get PIT id and page number from context
                pitId = context.pitId;
                pageNo = context.pageNo;
            }


            span.setAttribute("query.index", INDEX_TEMPLATE_PATTERNS)
                .setAttribute("query.size", PAGE_SIZE)
                .setAttribute("pitId", pitId)
                .setAttribute("ci.pipeline.run.traceId", traceId)
                .setAttribute("ci.pipeline.run.spanId", spanId)
                .setAttribute("query.from", pageNo * PAGE_SIZE);

            SearchRequest searchRequest = new SearchRequest.Builder()
                .pit(pit -> pit.id(pitId).keepAlive(POINT_IN_TIME_KEEP_ALIVE))
                .from(pageNo * PAGE_SIZE)
                .size(PAGE_SIZE)
                .sort(s -> s.field(f -> f.field(FIELD_TIMESTAMP).order(SortOrder.Asc)))
                .query(q -> q.match(m -> m.field(FIELD_TRACE_ID).query(FieldValue.of(traceId))))
                // .fields() TODO narrow down the list fields to retrieve - we probably have to look at a source filter
                .build();

            logger.log(Level.FINE, "Retrieve logs for traceId: " + traceId);
            SearchResponse<ObjectNode> searchResponse = this.esClient.search(searchRequest, ObjectNode.class);
            hits = searchResponse.hits().hits();
            span.setAttribute("results", hits.size());
            completed = hits.size() != PAGE_SIZE; // TODO is there smarter?

            if (completed) {
                logger.log(Level.FINE, () -> "Clear scrollId: " + pitId + " for trace: " + traceId + ", span: " + spanId);
                esClient.closePointInTime(p -> p.id(pitId));
            }

            ByteBuffer byteBuffer = new ByteBuffer();

            try (Writer w = new OutputStreamWriter(byteBuffer, charset)) {
                String logsVisualizationUrl = kibanaBaseUrl + "/app/logs/stream?" +
                    "logPosition=(end:now,start:now-1d,streamLive:!f)&" +
                    "logFilter=(language:kuery,query:%27trace.id:" + traceId + "%27)&";
                if (pageNo == 0) {
                    String logsInKibana = HyperlinkNote.encodeTo(logsVisualizationUrl, "View logs in Kibana");
                    w.write("View logs in Kibana: " + logsVisualizationUrl + "\n\n");
                }
                writeOutput(w, hits);
                if (!completed) {
                    w.write("\n\n... see rest of logs on Kibana " + logsVisualizationUrl);
                }
            }

            String newPitId = completed ? null : pitId;
            logger.log(Level.FINE, () -> "overallLog(completed: " + completed + ", page: " + pageNo + ", written.length: " + byteBuffer.length() + ", pit.hash: " + pitId.hashCode() + ")");
            return new LogsQueryResult(
                byteBuffer, charset, completed,
                new ElasticsearchLogsQueryContext(newPitId, pageNo + 1)
            );
        } catch (IOException | RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * FIXME implement
     *
     * @param traceId
     * @param spanId
     * @param logsQueryContext
     * @return
     * @throws IOException
     */
    @Nonnull
    @Override
    public LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable ElasticsearchLogsQueryContext logsQueryContext) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    private void writeOutput(Writer writer, List<Hit<ObjectNode>> hits) throws IOException {
        for (Hit<ObjectNode> hit : hits) {
            ObjectNode source = hit.source();
            ObjectNode labels = (ObjectNode) source.findValue("labels");
            //Retrieve the label message and annotations to show the formatted message in Jenkins.
            String message;
            JSONArray annotations;

            if (labels == null) {
                message = Objects.toString(source.get(MESSAGE_KEY));
                annotations = null;
            } else if (labels.findValue(MESSAGE_KEY) != null && labels.findValue(ANNOTATIONS_KEY) != null) {
                message = labels.get(MESSAGE_KEY).asText(null);
                annotations = JSONArray.fromObject(labels.get(ANNOTATIONS_KEY).asText());
            } else if (source.get(MESSAGE_KEY) != null) {
                // FIXME why is labels[message] a wrong value when labels[annotations] is null
                message = source.get(MESSAGE_KEY).asText();
                annotations = null;
            } else {
                // TODO WHY DO WE HAVE SUCH RESULT
                logger.log(Level.FINER, () -> "Skip document " + hit.index() + " - " + hit.id());
                continue;
            }
            logger.log(Level.FINER, () -> "Write: " + message + ", id: " + hit.id());
            ConsoleNotes.write(writer, message, annotations);
        }
    }

    /**
     * FIXME returns false when true is expected. How to test
     * check if the configured index template exists.
     *
     * @return true if the index template exists.
     * @throws IOException
     */
    public boolean indexTemplateExists() throws IOException {
         ElasticsearchIndicesClient indicesClient = this.esClient.indices();
        return indicesClient.existsIndexTemplate(b-> b.name(INDEX_TEMPLATE_NAME)).value();
    }

    /**
     * FIXME optimize search
     */
    public static Credentials getCredentials(String jenkinsCredentialsId) throws NoSuchElementException {
        final UsernamePasswordCredentials usernamePasswordCredentials = (UsernamePasswordCredentials) SystemCredentialsProvider.getInstance().getCredentials().stream()
            .filter(credentials ->
                (credentials instanceof UsernamePasswordCredentials)
                    && ((IdCredentials) credentials)
                    .getId().equals(jenkinsCredentialsId))
            .findAny().get();

        return new Credentials() {
            @Override
            public Principal getUserPrincipal() {
                return new BasicUserPrincipal(usernamePasswordCredentials.getUsername());
            }

            @Override
            public String getPassword() {
                return usernamePasswordCredentials.getPassword().getPlainText();
            }
        };
    }
}