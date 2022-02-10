/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
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
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryContext;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
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
public class ElasticsearchLogStorageScrollingRetriever implements LogStorageRetriever {
    public static final String TIMESTAMP = "@timestamp";
    public static final Time POINT_IN_TIME_TTL = Time.of(builder -> builder.time("30s"));
    public static final int PAGE_SIZE = 100; // FIXME

    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageScrollingRetriever.class.getName());

    @Nonnull
    private final String indexPattern;

    @Nonnull
    final transient ElasticsearchClient elasticsearchClient;


    /**
     * TODO verify unsername:password auth vs apiKey auth
     */
    public ElasticsearchLogStorageScrollingRetriever(String elasticsearchUrl, Credentials elasticsearchCredentials, String indexPattern) {
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
        // https://www.elastic.co/guide/en/elasticsearch/reference/7.17/scroll-api.html

        Charset charset = StandardCharsets.UTF_8;
        boolean complete;
        String newScrollId;
        List<Hit<ObjectNode>> hits;
        if (logsQueryContext == null) {
            SearchRequest searchRequest = new SearchRequest.Builder()
                .scroll(builder -> builder.time("30s"))
                .size(PAGE_SIZE)
                .sort(sortBuilder -> sortBuilder.field(fieldBuilder -> fieldBuilder.field(TIMESTAMP).order(SortOrder.Asc)))
                .query(queryBuilder ->
                    queryBuilder.match(
                        matchQueryBuilder -> matchQueryBuilder.field("trace.id").query(
                            fieldValueBuilder -> fieldValueBuilder.stringValue(traceId))))
                // .fields() TODO narrow down the list fields to retrieve - we probably have to look at a source filter
                .build();
            logger.log(Level.INFO, "Retrieve logs for traceId: " + traceId);
            SearchResponse<ObjectNode> searchResponse = this.elasticsearchClient.search(searchRequest, ObjectNode.class);
            hits = searchResponse.hits().hits();
            newScrollId = searchResponse.scrollId();
        } else {
            ElasticsearchLogsQueryScrollingContext context = (ElasticsearchLogsQueryScrollingContext) logsQueryContext;
            logger.log(Level.INFO, "Retrieve logs with scrollId: " + context.scrollId + " for traceId: " + traceId);
            ScrollRequest scrollRequest = ScrollRequest.of(builder -> builder.scrollId(context.scrollId));
            ScrollResponse<ObjectNode> scrollResponse = this.elasticsearchClient.scroll(scrollRequest, ObjectNode.class);
            hits = scrollResponse.hits().hits();
            newScrollId = context.scrollId; // TODO why doesn't the scroll response hold a new scrollId? scrollResponse.scrollId();
        }
        complete = hits.size() != PAGE_SIZE; // TODO is there smarter?

        if (complete) {
            logger.log(Level.INFO, () -> "Clear scrollId: " + newScrollId + " for trace: " + traceId + ", span: " + spanId);
            ClearScrollRequest clearScrollRequest = ClearScrollRequest.of(builder -> builder.scrollId(newScrollId));
            elasticsearchClient.clearScroll(clearScrollRequest);
        }


        ByteBuffer byteBuffer = new ByteBuffer();
        try (Writer w = new OutputStreamWriter(byteBuffer, charset)) {
            writeOutput(w, hits);
        }

        return new LogsQueryResult(byteBuffer, charset, complete, new ElasticsearchLogsQueryScrollingContext(newScrollId));
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
    public LogsQueryResult stepLog(@Nonnull String traceId, @Nonnull String spanId, @Nullable LogsQueryContext logsQueryContext) throws IOException {
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
            } else if (source.get(MESSAGE_KEY) != null){
                // FIXME why is labels[message] a wrong value when labels[annotations] is null
                message = source.get(MESSAGE_KEY).asText();
                annotations = null;
            } else {
                // TODO WHY DO WE HAVE SUCH RESULT
                logger.log(Level.INFO, () -> "Skip document " + hit.index() + " - " + hit.id());
                continue;
            }
            logger.log(Level.INFO, "Add " + message);
            ConsoleNotes.write(writer, message, annotations);
        }
    }

    /**
     * check if the configured indexTemplate exists.
     * FIXME verify we check on IndexTemplate rather than IndexPattern in 8.0
     *
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
