/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.BasicUserPrincipal;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.*;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;

/**
 * Retrieve the logs from the logs backend.
 */
public class ElasticsearchLogStorageRetriever implements LogStorageRetriever {
    public static final String TIMESTAMP = "@timestamp";
    public static final TimeValue DEFAULT_TIMEVALUE = TimeValue.timeValueSeconds(30);
    public static final int PAGE_SIZE = 1000;

    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageRetriever.class.getName());

    @Nonnull
    private final CredentialsProvider credentialsProvider;
    @Nonnull
    private final String elasticsearchUrl;
    @Nonnull
    private final String indexPattern;


    /**
     * TODO verify unsername:password auth vs apiKey auth
     */
    public ElasticsearchLogStorageRetriever(String elasticsearchUrl, Credentials elasticsearchCredentials, String indexPattern) {
        if (StringUtils.isBlank(elasticsearchUrl)) {
            throw new IllegalArgumentException("Elasticsearch url cannot be blank");
        }
        this.elasticsearchUrl = elasticsearchUrl;
        if (StringUtils.isBlank(indexPattern)) {
            throw new IllegalArgumentException("Elasticsearch Index Pattern cannot be blank");
        }
        this.indexPattern = indexPattern;

        this.credentialsProvider = new BasicCredentialsProvider();
        this.credentialsProvider.setCredentials(AuthScope.ANY, elasticsearchCredentials);
    }

    @Nonnull
    @Override
    public ByteBuffer overallLog(@Nonnull String traceId, @Nonnull String spanId) throws IOException {
        return retrieveTraceLogs(traceId, spanId);
    }

    @Nonnull
    @Override
    public ByteBuffer stepLog(@Nonnull String traceId, @Nonnull String spanId) throws IOException {
        return retrieveTraceLogs(traceId, spanId);
    }

    /**
     * Gather the log text for one node or the entire build.
     */
    private ByteBuffer retrieveTraceLogs(String traceId, String spanId) throws IOException {
        ByteBuffer out = new ByteBuffer();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            SearchResponse searchResponse = search(traceId, spanId);
            String scrollId = searchResponse.getScrollId();
            SearchHit[] searchHits = searchResponse.getHits().getHits();
            writeOutput(w, searchHits);

            while (searchHits.length > 0) {
                searchResponse = next(scrollId);
                scrollId = searchResponse.getScrollId();
                searchHits = searchResponse.getHits().getHits();
                writeOutput(w, searchHits);
            }

            if (searchResponse.getHits().getTotalHits().value != 0) {
                clear(scrollId); // FIXME cyrille: why do we ignore the returned value?
            }
            w.flush();
        }
        return out;
    }

    private void writeOutput(Writer writer, SearchHit[] searchHits) throws IOException {
        String previousDocumentId = null;
        for (SearchHit line : searchHits) {
            Map<String, Object> fields = line.getSourceAsMap();
            Map<String, Object> labels = (Map<String, Object>) fields.get(LABELS);

            //Retrieve the label message and annotations to show the formatted message in Jenkins.
            String message;
            JSONArray annotations;

            if (labels == null) {
                message = Objects.toString(fields.get(MESSAGE_KEY));
                annotations = null;
            } else if (labels.containsKey(MESSAGE_KEY) && labels.containsKey(ANNOTATIONS_KEY)) {
                message = Objects.toString(labels.get(MESSAGE_KEY));
                annotations = JSONArray.fromObject(labels.get(ANNOTATIONS_KEY));
            } else {
                // FIXME why is labels[message] a wrong value when labels[annotations] is null
                message = Objects.toString(fields.get(MESSAGE_KEY));
                annotations = null;
            }
            ConsoleNotes.write(writer, message, annotations);
        }
    }

    /**
     * @return the RestClientBuilder to create the Elasticsearch REST client.
     */
    private RestClientBuilder getBuilder() {
        RestClientBuilder builder = RestClient.builder(HttpHost.create(this.elasticsearchUrl));
        builder.setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
        return builder;
    }

    /**
     * Search the log lines of a build ID and Node ID.
     *
     * @param traceId build ID to search for the logs.
     * @param spanId  A page with log lines the results of the search.
     * @return A page with log lines the results of the search. The object contains the scrollID to use in {@link #next(String)} requests.
     * @throws IOException
     */
    public SearchResponse search(@Nonnull String traceId, @CheckForNull String spanId) throws IOException {
        try (RestHighLevelClient client = new RestHighLevelClient(getBuilder())) {
            final Scroll scroll = new Scroll(DEFAULT_TIMEVALUE);
            SearchRequest searchRequest = new SearchRequest(this.indexPattern);
            searchRequest.scroll(scroll);
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.size(PAGE_SIZE);
            searchSourceBuilder.sort(new FieldSortBuilder(TIMESTAMP).order(SortOrder.ASC));
            if (StringUtils.isBlank(spanId)) {
                searchSourceBuilder.query(matchQuery(SPAN_ID, traceId));
            } else {
                searchSourceBuilder.query(boolQuery().must(matchQuery(TRACE_ID, traceId)).must(matchQuery(SPAN_ID, spanId)));
            }
            searchRequest.source(searchSourceBuilder);

            return client.search(searchRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * Request the next page of a scroll search.
     *
     * @param scrollId Scroll ID to request the next page of log lines. see {@link #search(String, String)}
     * @return A page with log lines the results of the search. The object contains the scrollID to use in {@link #next(String)} requests.
     * @throws IOException
     */
    public SearchResponse next(@Nonnull String scrollId) throws IOException {
        return next(scrollId, DEFAULT_TIMEVALUE);
    }

    /**
     * Request the next page of a scroll search.
     *
     * @param scrollId         Scroll ID to request the next page of log lines. see {@link #search(String, String)}
     * @param timeValueSeconds seconds that will control how long to keep the scrolling resources open.
     * @return A page with log lines the results of the search. The object contains the scrollID to use in {@link #next(String)} requests.
     * @throws IOException
     */
    public SearchResponse next(@Nonnull String scrollId, @Nonnull TimeValue timeValueSeconds) throws IOException {
        SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
        scrollRequest.scroll(timeValueSeconds);
        try (RestHighLevelClient client = new RestHighLevelClient(getBuilder())) {
            return client.scroll(scrollRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * Clears one or more scroll ids using the Clear Scroll API.
     *
     * @param scrollId Scroll ID to request the next page of log lines. see {@link #search(String, String)}
     * @return the object with the result.
     * @throws IOException
     */
    public ClearScrollResponse clear(@Nonnull String scrollId) throws IOException {
        ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
        clearScrollRequest.addScrollId(scrollId);
        try (RestHighLevelClient client = new RestHighLevelClient(getBuilder())) {
            return client.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
        }
    }

    /**
     * check if an index exists.
     *
     * @return true if the index exists.
     * @throws IOException
     */
    public boolean indexExists() throws IOException {
        boolean ret = false;
        if (StringUtils.isNotBlank(this.indexPattern)) {
            try (RestHighLevelClient client = new RestHighLevelClient(getBuilder())) {
                GetIndexRequest request = new GetIndexRequest(this.indexPattern);
                ret = client.indices().exists(request, RequestOptions.DEFAULT);
            }
        }
        return ret;
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
