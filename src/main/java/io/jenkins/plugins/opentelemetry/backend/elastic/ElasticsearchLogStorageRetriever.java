/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes;
import io.jenkins.plugins.opentelemetry.job.log.LogStorageRetriever;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.LABELS;

/**
 * Retrieve the logs from the logs backend.
 *
 */
public class ElasticsearchLogStorageRetriever implements LogStorageRetriever {

    private final static Logger logger = Logger.getLogger(ElasticsearchLogStorageRetriever.class.getName());

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
        JenkinsOpenTelemetryPluginConfiguration config = JenkinsOpenTelemetryPluginConfiguration.get();
        ElasticBackend elasticStackConfiguration = (ElasticBackend) config.getObservabilityBackends().stream().filter(it -> it instanceof ElasticBackend).findFirst().get();
        if (elasticStackConfiguration == null) {
            throw new IOException("The logs configuration is incorrect, check the OpenTelemetry plugin configuration");
        }
        UsernamePasswordCredentials creds = elasticStackConfiguration.getCredentials();
        String elasticsearchUrl = elasticStackConfiguration.getElasticsearchUrl();
        String indexPattern = elasticStackConfiguration.getIndexPattern();
        String username = creds.getUsername();
        String password = creds.getPassword().getPlainText();
        if (StringUtils.isBlank(elasticsearchUrl) || StringUtils.isBlank(indexPattern)) {
            throw new IOException("The logs configuration is incorrect, check the OpenTelemetry plugin configuration");
        }
        ElasticsearchRetriever elasticsearchRetriever = new ElasticsearchRetriever(
            elasticsearchUrl, username, password,
            indexPattern
        );
        if (!elasticsearchRetriever.indexExists()) {
            try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                w.write("The index pattern configured does not exists\n");
                w.flush();
            }
            return out;
        }
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            SearchResponse searchResponse = elasticsearchRetriever.search(traceId, spanId);
            String scrollId = searchResponse.getScrollId();
            SearchHit[] searchHits = searchResponse.getHits().getHits();
            writeOutput(w, searchHits);

            while (searchHits.length > 0) {
                searchResponse = elasticsearchRetriever.next(scrollId);
                scrollId = searchResponse.getScrollId();
                searchHits = searchResponse.getHits().getHits();
                writeOutput(w, searchHits);
            }

            if (searchResponse.getHits().getTotalHits().value != 0) {
                elasticsearchRetriever.clear(scrollId);
            }
            w.flush();
        }
        return out;
    }

    private void writeOutput(Writer w, SearchHit[] searchHits) throws IOException {
        for (SearchHit line : searchHits) {
            JSONObject json = JSONObject.fromObject(line.getSourceAsMap());
            //Retrieve the label message and annotations to show the formatted message in Jenkins.
            JSONObject labels = (JSONObject) json.get(LABELS);
            if(labels != null && labels.containsKey(ANNOTATIONS_KEY) && labels.containsKey(MESSAGE_KEY)){
                ConsoleNotes.write(w, labels);
            } else if (json.containsKey(MESSAGE_KEY)){
                ConsoleNotes.write(w, json);
            } else {
                logger.log(Level.FINE, () -> "Skip data with no 'message' field " + json.toString());
            }
        }
    }
}
