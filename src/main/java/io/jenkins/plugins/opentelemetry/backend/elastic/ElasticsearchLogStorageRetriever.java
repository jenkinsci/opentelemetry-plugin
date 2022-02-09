/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryContext;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import jakarta.json.JsonObject;
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
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.LABELS;
import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.TRACE_ID;


/**
 * Retrieve the logs from Elasticsearch.
 * FIXME graceful shutdown
 */
public class ElasticsearchLogStorageRetriever implements LogStorageRetriever {
    public static final String TIMESTAMP = "@timestamp";
    public static final Time SCROLL_TTL = Time.of(builder -> builder.offset(30_000));
    public static final int PAGE_SIZE = 1000;

    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageRetriever.class.getName());

    @Nonnull
    private final String indexPattern;

    @Nonnull
    final transient ElasticsearchClient elasticsearchClient;


    /**
     * TODO verify unsername:password auth vs apiKey auth
     */
    public ElasticsearchLogStorageRetriever(String elasticsearchUrl, Credentials elasticsearchCredentials, String indexPattern) {
        if (StringUtils.isBlank(elasticsearchUrl)) {
            throw new IllegalArgumentException("Elasticsearch url cannot be blank");
        }
        if (StringUtils.isBlank(indexPattern)) {
            throw new IllegalArgumentException("Elasticsearch Index Pattern cannot be blank");
        }
        this.indexPattern = indexPattern;

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, elasticsearchCredentials);

        RestClient restClient = RestClient.builder(HttpHost.create(elasticsearchUrl))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        this.elasticsearchClient = new ElasticsearchClient(elasticsearchTransport);
    }

    @Nonnull
    @Override
    public LogsQueryResult overallLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable LogsQueryContext logsQueryContext) throws IOException {

        ByteBuffer byteBuffer = new ByteBuffer();
        String newScrollId;
        Charset charset = StandardCharsets.UTF_8;
        boolean complete;
        if (logsQueryContext == null) {
            SearchRequest searchRequest = new SearchRequest.Builder()
                .index(this.indexPattern)
                .scroll(SCROLL_TTL)
                .size(PAGE_SIZE)
                .sort(sortBuilder -> sortBuilder.field(fieldBuilder -> fieldBuilder.field(TIMESTAMP).order(SortOrder.Asc)))
                .query(queryBuilder ->
                    queryBuilder.match(
                        matchQueryBuilder -> matchQueryBuilder.field(TRACE_ID).query(
                            fieldValueBuilder -> fieldValueBuilder.stringValue(traceId))))
                // .fields() TODO narrow down the list fields to retrieve
                .build();
            SearchResponse<JsonObject> searchResponse = this.elasticsearchClient.search(searchRequest, JsonObject.class);
            newScrollId = searchResponse.scrollId();
            List<JsonObject> documents = searchResponse.documents();
            try (Writer w = new OutputStreamWriter(byteBuffer, charset)) {
                writeOutput(w, documents);
            }
            complete = documents.size() == 0;
        } else {
            ScrollRequest scrollRequest = new ScrollRequest.Builder()
                .scrollId(((ElasticsearchLogsQueryContext) logsQueryContext).scrollId)
                .build();
            ScrollResponse<JsonObject> scrollResponse = this.elasticsearchClient.scroll(scrollRequest, JsonObject.class);
            newScrollId = scrollResponse.scrollId();
            List<JsonObject> documents = scrollResponse.documents();
            complete = documents.size() == 0;
            try (Writer w = new OutputStreamWriter(byteBuffer, charset)) {
                writeOutput(w, documents);
            }
            complete = documents.size() == 0;
        }

        // FIXME when do we clear scroll

        return new LogsQueryResult(byteBuffer, charset, complete, new ElasticsearchLogsQueryContext(newScrollId));
    }

    /**
     * FIXME implement
     * @param traceId
     * @param spanId
     * @param logsQueryContext
     * @return
     * @throws IOException
     */
    @Nonnull
    @Override
    public LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable LogsQueryContext logsQueryContext) throws IOException {
        throw new UnsupportedOperationException("Not yet implemented");
    }


    private void writeOutput(Writer writer, List<JsonObject> documents) throws IOException {
        for (JsonObject document : documents) {
            JsonObject source = document.getJsonObject("_source");
            JsonObject labels = source.getJsonObject(LABELS);
            //Retrieve the label message and annotations to show the formatted message in Jenkins.
            String message;
            JSONArray annotations;

            if (labels == null) {
                message = Objects.toString(source.get(MESSAGE_KEY));
                annotations = null;
            } else if (labels.containsKey(MESSAGE_KEY) && labels.containsKey(ANNOTATIONS_KEY)) {
                message = labels.getString(MESSAGE_KEY);
                annotations = JSONArray.fromObject(labels.getString(ANNOTATIONS_KEY));
            } else {
                // FIXME why is labels[message] a wrong value when labels[annotations] is null
                message = source.getString(MESSAGE_KEY);
                annotations = null;
            }
            ConsoleNotes.write(writer, message, annotations);
        }
    }

    /**
     * check if the configured indexTemplate exists.
     * FIXME verify we check on IndexTemplate rather than IndexPattern in 8.0
     * @return true if the index exists.
     * @throws IOException
     */
    public boolean indexExists() throws IOException {
        if (StringUtils.isBlank(this.indexPattern)) {
            return false;
        } else {
            ElasticsearchIndicesClient indicesClient = this.elasticsearchClient.indices();
            return indicesClient.existsIndexTemplate(builder -> builder.name(indexPattern)).value();
        }
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
