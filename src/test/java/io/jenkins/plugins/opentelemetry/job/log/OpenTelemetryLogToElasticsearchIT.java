/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.jenkins.plugins.opentelemetry.backend.elastic.ElasticsearchFields;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.*;

public class OpenTelemetryLogToElasticsearchIT {
    private final static Random RANDOM = new Random();

    @Test
    public void test() throws Exception {
        InputStream envAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Assert.assertNotNull(".env file not found in classpath", envAsStream);
        Properties env = new Properties();
        env.load(envAsStream);
        Map<String, String> configuration = new HashMap<>();
        env.forEach((k, v) -> configuration.put(k.toString(), v.toString()));
        configuration.put("otel.traces.exporter", "otlp");
        configuration.put("otel.metrics.exporter", "otlp");
        configuration.put("otel.logs.exporter", "otlp");

        final int LOG_MESSAGE_COUNT = 100;
        String traceId;
        // PRODUCE OPEN TELEMETRY LOG MESSAGES
        {
            AutoConfiguredOpenTelemetrySdk autoConfiguredOpenTelemetrySdk = AutoConfiguredOpenTelemetrySdk.builder()
                .addPropertiesSupplier(() -> configuration).build();
            try {
                OpenTelemetrySdk sdk = autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk();
                Tracer tracer = sdk.getTracer("test");
                Logger otelLogger = sdk.getSdkLoggerProvider().get("test");
                Span span = tracer.spanBuilder("my-test-pipeline").startSpan();
                try (Scope scope = span.makeCurrent()) {
                    for (int i = 0; i < LOG_MESSAGE_COUNT; i++) {
                        otelLogger
                            .logRecordBuilder()
                            .setContext(Context.current())
                            .setBody("Log Message " + i)
                            .setAllAttributes(
                                Attributes.of(
                                    AttributeKey.stringKey("myStringAttribute"), "Value " + i,
                                    AttributeKey.longKey("myNumericAttribute"), (long) i))
                            .emit();
                        Thread.sleep(RANDOM.nextInt(100));
                    }
                } finally {
                    span.end();
                }
                traceId = span.getSpanContext().getTraceId();
            } finally {
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkLoggerProvider().close();
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkTracerProvider().close();
            }
        }

        Thread.sleep(3_000);
        // VERIFY LOG MESSAGES IN ELASTICSEARCH
        {
            String elasticsearchUrl = configuration.get("elasticsearch.url");
            String elasticsearchUsername = configuration.get("elasticsearch.username");
            String elasticsearchPassword = configuration.get("elasticsearch.password");

            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));

            RestClient restClient = RestClient.builder(HttpHost.create(elasticsearchUrl))
                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
                .build();
            RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
            ElasticsearchClient elasticsearchClient = new ElasticsearchClient(elasticsearchTransport);

            SearchRequest searchRequest = new SearchRequest.Builder()
                .index(ElasticsearchFields.INDEX_TEMPLATE_PATTERNS)
                .size(500)
                .sort(s -> s.field(f -> f.field(ElasticsearchFields.FIELD_TIMESTAMP).order(SortOrder.Asc)))
                .query(q -> q.match(m -> m.field(ElasticsearchFields.FIELD_TRACE_ID).query(FieldValue.of(traceId))))
                .build();
            SearchResponse<ObjectNode> searchResponse = elasticsearchClient.search(searchRequest, ObjectNode.class);
            List<Hit<ObjectNode>> hits = searchResponse.hits().hits();
            if (hits.size() != LOG_MESSAGE_COUNT) {
                System.err.println("Invalid number of log messages: actual: " + hits.size() + ", expected: " + LOG_MESSAGE_COUNT);
            }

            for (Hit<ObjectNode> hit : hits) {

                ObjectNode source = hit.source();
                Assert.assertNull(source);

                ObjectNode labels = (ObjectNode) source.findValue("labels");
                ObjectNode numericLabels = (ObjectNode) source.findValue("numeric_labels");

                try {
                    String message = source.findValue("message").asText();
                    String myStringAttribute = labels.findValue("myStringAttribute").asText();
                    long myNumericAttribute = numericLabels.findValue("myNumericAttribute").longValue();
                    System.out.println(hit.id() + "\tmessage:'" + message + "', \tmyStringAttribute: '" + myStringAttribute + "', myNumericAttribute: " + myNumericAttribute);
                } catch (Exception e) {
                    System.err.println("Error parsing " + source);
                }
            }
        }
    }
}
