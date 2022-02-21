/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import groovy.text.Template;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
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
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.IndicesClient;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.ComposableIndexTemplateExistRequest;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * TODO implement streaming of the logs results. As long as we don't know how to stream the results, we just grab the first lines of logs.
 *
 * Use the old `org.elasticsearch.client:elasticsearch-rest-high-level-client` waiting for
 * `co.elastic.clients:elasticsearch-java` to fix https://github.com/elastic/elasticsearch-java/issues/163
 */
public class ElasticsearchLogStorageRetriever implements LogStorageRetriever<ElasticsearchLogsQueryContext>, Closeable {
    /**
     * Field used by the Elastic-Otel mapping to store the {@link io.opentelemetry.sdk.logs.LogBuilder#setBody(String)}
     */
    public static final String FIELD_MESSAGE = "message";
    public static final String FIELD_LABELS = "labels";
    /**
     * Mapping for `SpanContext#getTraceId()`
     */
    public static final String FIELD_TRACE_ID = "trace.id";
    public static final String FIELD_TIMESTAMP = "@timestamp";

    public static final int PAGE_SIZE = 100;
    public static final String INDEX_TEMPLATE_PATTERNS = "logs-apm.app-*";
    public static final String INDEX_TEMPLATE_NAME = "logs-apm.app";

    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageRetriever.class.getName());

    @Nonnull
    private final Template buildLogsVisualizationUrlTemplate;

    private final TemplateBindingsProvider templateBindingsProvider;

    @Nonnull
    private final RestHighLevelClient esClient;

    private final Tracer tracer;

    /**
     * TODO verify unsername:password auth vs apiKey auth
     */
    public ElasticsearchLogStorageRetriever(
        String elasticsearchUrl, Credentials elasticsearchCredentials,
        Template buildLogsVisualizationUrlTemplate, TemplateBindingsProvider templateBindingsProvider,
        Tracer tracer) {
        if (StringUtils.isBlank(elasticsearchUrl)) {
            throw new IllegalArgumentException("Elasticsearch url cannot be blank");
        }

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, elasticsearchCredentials);

        this.esClient = new RestHighLevelClient(RestClient
            .builder(HttpHost.create(elasticsearchUrl))
            .setHttpClientConfigCallback(httpClient -> httpClient.setDefaultCredentialsProvider(credentialsProvider)));
        this.tracer = tracer;

        this.buildLogsVisualizationUrlTemplate = buildLogsVisualizationUrlTemplate;
        this.templateBindingsProvider = templateBindingsProvider;
    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(@Nonnull String traceId, @Nonnull String spanId, boolean complete, @Nullable ElasticsearchLogsQueryContext logsQueryContext) throws IOException {
        Span span = tracer.spanBuilder("elasticsearch.search")
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Charset charset = StandardCharsets.UTF_8;

            SearchRequest searchRequest = new SearchRequest(ElasticsearchLogStorageRetriever.INDEX_TEMPLATE_PATTERNS)
                .source(new SearchSourceBuilder()
                    .size(PAGE_SIZE)
                    .sort(new FieldSortBuilder(FIELD_TIMESTAMP).order(SortOrder.ASC))
                    .query(QueryBuilders.matchQuery(FIELD_TRACE_ID, traceId)));

            SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchHit[] hits = searchResponse.getHits().getHits();

            ByteBuffer byteBuffer = new ByteBuffer();

            try (Writer w = new OutputStreamWriter(byteBuffer, charset)) {
                Map<String, String> bindings = TemplateBindingsProvider.compose(this.templateBindingsProvider, Collections.singletonMap("traceId", traceId)).getBindings();
                String logsVisualizationUrl = this.buildLogsVisualizationUrlTemplate.make(bindings).toString();
                w.write("View logs in Kibana: " + logsVisualizationUrl + "\n\n");

                writeOutput(w, hits);

                span.setAttribute("results", hits.length);
                boolean completed =  hits.length != PAGE_SIZE; // TODO is there smarter?
                if (completed) {
                    w.write("\n\nView logs on Kibana " + logsVisualizationUrl);
                } else {
                    w.write("...\n\nView the rest of logs on Kibana " + logsVisualizationUrl);
                }
            }

            logger.log(Level.FINE, () -> "overallLog(written.length: " + byteBuffer.length() + ")");
            return new LogsQueryResult(
                byteBuffer, charset, true,
                new ElasticsearchLogsQueryContext()
            );
        } catch (IOException | RuntimeException e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }

    @Nonnull
    @Override
    public LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable ElasticsearchLogsQueryContext logsQueryContext) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void writeOutput(Writer writer, SearchHit[] hits) throws IOException {
        for (SearchHit hit : hits) {
            Map<String, Object> source = hit.getSourceAsMap();
            Map<String, Object> labels = (Map<String, Object>) source.get(FIELD_LABELS);
            //Retrieve the label message and annotations to show the formatted message in Jenkins.
            String message = Optional.ofNullable(source.get(FIELD_MESSAGE)).map(m -> m.toString()).orElse(null);
            if (message == null) {
                logger.log(Level.FINE, () -> "Skip log with no message (document id: " + hit.getId() + ")");
                continue;
            }
            JSONArray annotations;
            if (labels == null) {
                annotations = null;
            } else {
                annotations = Optional
                    .ofNullable(labels.get(JenkinsOtelSemanticAttributes.JENKINS_ANSI_ANNOTATIONS.getKey()))
                    .map(a -> JSONArray.fromObject(a.toString())).orElse(null);
            }
            logger.log(Level.FINER, () -> "Write: " + message + ", id: " + hit.getId());
            ConsoleNotes.write(writer, message, annotations);
        }
    }

    /**
     * Check if the configured index template exists.
     * It's a good way to verify the Elastic setup is capable of receiving OpenTelemetry Logs
     *
     * @return true if the index template exists.
     */
    public boolean indexTemplateExists() throws IOException {
        IndicesClient indicesClient = this.esClient.indices();
        return indicesClient.existsIndexTemplate(new ComposableIndexTemplateExistRequest(INDEX_TEMPLATE_NAME), RequestOptions.DEFAULT);
    }

    @Override
    public void close() throws IOException {
        logger.log(Level.INFO, () -> "Shutdown Elasticsearch client...");
        this.esClient.close();
    }

    @Override
    public String toString() {
        return "ElasticsearchLogStorageRetriever{" +
            "buildLogsVisualizationUrlTemplate=" + buildLogsVisualizationUrlTemplate +
            ", templateBindingsProvider=" + templateBindingsProvider +
            '}';
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
