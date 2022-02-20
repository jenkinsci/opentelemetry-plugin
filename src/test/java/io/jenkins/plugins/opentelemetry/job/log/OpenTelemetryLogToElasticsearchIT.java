/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.logs.LogEmitter;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

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
                LogEmitter logEmitter = sdk.getSdkLogEmitterProvider().get("test");
                Span span = tracer.spanBuilder("my-test-pipeline").startSpan();
                try (Scope scope = span.makeCurrent()) {
                    for (int i = 0; i < LOG_MESSAGE_COUNT; i++) {
                        logEmitter
                            .logBuilder()
                            .setContext(Context.current())
                            .setBody("Log Message " + i)
                            .setAttributes(
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
                autoConfiguredOpenTelemetrySdk.getOpenTelemetrySdk().getSdkLogEmitterProvider().close();
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

            RestHighLevelClient esClient = new RestHighLevelClient(RestClient
                .builder(HttpHost.create(elasticsearchUrl))
                .setHttpClientConfigCallback(httpClient -> httpClient.setDefaultCredentialsProvider(credentialsProvider)));

            SearchRequest searchRequest = new SearchRequest("logs-apm.app-*")
                .source(new SearchSourceBuilder()
                    .size(100)
                    .sort(new FieldSortBuilder("@timestamp").order(SortOrder.ASC))
                    .query(QueryBuilders.matchQuery("trace.id", traceId)));

            SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
            SearchHits hits = searchResponse.getHits();


            if (hits.getTotalHits().value != LOG_MESSAGE_COUNT) {
                System.err.println("Invalid number of log messages: actual: " + hits.getTotalHits().value + ", expected: " + LOG_MESSAGE_COUNT);
            }

            for (SearchHit hit : hits.getHits()) {

                Map<String, Object> source = hit.getSourceAsMap();
                Map<String, Object> labels = (Map<String, Object>) source.get("labels");
                Map<String, Object> numericLabels = (Map<String, Object>) source.get("numeric_labels");

                try {
                    Object message = source.get("message");
                    Object myStringAttribute = labels.get("myStringAttribute");
                    Object myNumericAttribute = numericLabels.get("myNumericAttribute");
                    System.out.println(hit.getId() + "\tmessage:'" + message + "', \tmyStringAttribute: '" + myStringAttribute + "', myNumericAttribute: " + myNumericAttribute);
                } catch (Exception e) {
                    System.err.println("Error parsing " + source);
                }
            }
        }
    }
}
