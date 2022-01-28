/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import hudson.ExtensionList;
import hudson.console.AnnotatedLargeText;
import io.jenkins.plugins.opentelemetry.backend.ElasticBackend;
import io.jenkins.plugins.opentelemetry.job.log.es.ElasticsearchRetriever;
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

/**
 * Retrieve the logs from Elasticsearch.
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
        ElasticBackend elasticStackConfiguration = ExtensionList.lookupSingleton(ElasticBackend.class);
        UsernamePasswordCredentials creds = elasticStackConfiguration.getCredentials();
        String elasticsearchUrl = elasticStackConfiguration.getElasticsearchUrl();
        String indexPattern = elasticStackConfiguration.getIndexPattern();
        String username = creds.getUsername();
        String password = creds.getPassword().getPlainText();
        if (StringUtils.isBlank(elasticsearchUrl) || StringUtils.isBlank(indexPattern)) {
            throw new IOException("some configuration parameters are incorrect, check the plugin configuration");
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
            // FIXME check context values to search for
            SearchResponse searchResponse = elasticsearchRetriever.search(buildInfo.getContext().get("KEY"), nodeId);
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
            ConsoleNotes.write(w, json);
        }
    }
}
