/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.console.AnnotatedLargeText;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticsearchRetriever;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.stapler.framework.io.ByteBuffer;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes.ANNOTATIONS_KEY;
import static io.jenkins.plugins.opentelemetry.job.log.ConsoleNotes.MESSAGE_KEY;
import static io.jenkins.plugins.opentelemetry.semconv.OpenTelemetryTracesSemanticConventions.LABELS;

/**
 * Retrieve the logs from the logs backend.
 *
 * TODO extract Elasticsearch specific code and add abstraction layer for a backend agnostic Log Retriever
 */
public class OtelLogRetriever {

    @Nonnull
    private final BuildInfo buildInfo;

    public OtelLogRetriever(@Nonnull BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    AnnotatedLargeText<FlowExecutionOwner.Executable> overallLog(FlowExecutionOwner.Executable build, boolean completed)
        throws IOException {
        ByteBuffer buf = new ByteBuffer();
        stream(buf, null);
        return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, completed, build);
    }

    AnnotatedLargeText<FlowNode> stepLog(FlowNode node, boolean completed) throws IOException {
        ByteBuffer buf = new ByteBuffer();
        stream(buf, node.getId());
        return new AnnotatedLargeText<>(buf, StandardCharsets.UTF_8, completed, node);
    }

    /**
     * Gather the log text for one node or the entire build.
     *
     * @param os     where to send output
     * @param nodeId if defined, limit output to that coming from this node
     */
    private void stream(@Nonnull OutputStream os, @CheckForNull String nodeId) throws IOException {
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
            try (Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
                w.write("The index pattern configured does not exists\n");
                w.flush();
            }
            return;
        }
        try (Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            SearchResponse searchResponse = elasticsearchRetriever.search(buildInfo.getTraceId(), buildInfo.getSpanId());
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
    }

    private void writeOutput(Writer w, SearchHit[] searchHits) throws IOException {
        for (SearchHit line : searchHits) {
            JSONObject json = JSONObject.fromObject(line.getSourceAsMap());
            //Retrieve the label message and annotations to show the formatted message in Jenkins.
            JSONObject labels = (JSONObject) json.get(LABELS);
            if(labels != null && labels.get(ANNOTATIONS_KEY) != null && labels.get(MESSAGE_KEY) != null){
                ConsoleNotes.write(w, labels);
            } else {
                ConsoleNotes.write(w, json);
            }
        }
    }
}
