/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch.ilm.GetLifecycleResponse;
import co.elastic.clients.elasticsearch.ilm.Phases;
import co.elastic.clients.elasticsearch.ilm.get_lifecycle.Lifecycle;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.GetIndexTemplateResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexSettingsLifecycle;
import co.elastic.clients.elasticsearch.indices.IndexTemplate;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.backend.ObservabilityBackend;
import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

@Ignore
public class ElasticsearchRetrieverIT {

    @Test
    public void testIndexExist() throws IOException {
        InputStream envAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Assert.assertNotNull(".env file not found in classpath", envAsStream);
        Properties env = new Properties();
        env.load(envAsStream);

        String url = env.getProperty("elasticsearch.url");
        String username = env.getProperty("elasticsearch.username");
        String password = env.getProperty("elasticsearch.password");
        String kibanaBaseUrl = env.getProperty("kibana.baseUrl");

        try (ElasticsearchLogStorageRetriever elasticsearchLogStorageRetriever = new ElasticsearchLogStorageRetriever(
            url, false,
            new UsernamePasswordCredentials(username, password),
            ObservabilityBackend.ERROR_TEMPLATE /* TODO better URL template */,
            TemplateBindingsProvider.of(Collections.singletonMap("kibanaBaseUrl", kibanaBaseUrl)))) {

            FormValidation formValidation = FormValidation.aggregate(elasticsearchLogStorageRetriever.checkElasticsearchSetup());
            System.out.println(formValidation);
            Assert.assertEquals(formValidation.kind, FormValidation.Kind.OK);

            final int MAX = 10;
            int counter = 0;
            LogsQueryResult logsQueryResult;
            boolean complete = true;
            do {
                System.out.println("Request " + counter);
                logsQueryResult = elasticsearchLogStorageRetriever.overallLog("my-war/master", 136, "1253b77680aa4f5a709e76381e5523f1", "", complete, Instant.now(), null);
                complete = false;
                counter++;
            } while (!logsQueryResult.isComplete() && counter < MAX);
            Assert.assertTrue(counter < MAX);
        }
    }

    @Test
    public void testCheckStatus() throws IOException {
        InputStream envAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Assert.assertNotNull(".env file not found in classpath", envAsStream);
        Properties env = new Properties();
        env.load(envAsStream);

        String url = env.getProperty("elasticsearch.url");
        String username = env.getProperty("elasticsearch.username");
        String password = env.getProperty("elasticsearch.password");
        String kibanaBaseUrl = env.getProperty("kibana.baseUrl");

        try (ElasticsearchLogStorageRetriever elasticsearchLogStorageRetriever = new ElasticsearchLogStorageRetriever(
            url, false,
            new UsernamePasswordCredentials(username, password),
            ObservabilityBackend.ERROR_TEMPLATE /* TODO better URL template */,
            TemplateBindingsProvider.of(Collections.singletonMap("kibanaBaseUrl", kibanaBaseUrl)))) {
            final List<FormValidation> formValidations = elasticsearchLogStorageRetriever.checkElasticsearchSetup();
            for (FormValidation formValidation : formValidations) {
                System.out.println(formValidation);
            }
        }
    }

    @Ignore("Wait for https://github.com/jenkinsci/opentelemetry-plugin/issues/336")
    @Test
    public void testGetIndexTemplate() throws IOException {
        InputStream envAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Assert.assertNotNull(".env file not found in classpath", envAsStream);
        Properties env = new Properties();
        env.load(envAsStream);

        String url = env.getProperty("elasticsearch.url");
        String username = env.getProperty("elasticsearch.username");
        String password = env.getProperty("elasticsearch.password");

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        RestClient restClient = RestClient.builder(HttpHost.create(url))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient elasticsearchClient = new ElasticsearchClient(elasticsearchTransport);
        ElasticsearchIndicesClient elasticsearchIndicesClient = elasticsearchClient.indices();
        GetIndexTemplateResponse indexTemplatesResponse = elasticsearchIndicesClient.getIndexTemplate(b -> b.name("logs-apm.app"));
        IndexTemplate indexTemplate = indexTemplatesResponse.indexTemplates().stream().findFirst().get().indexTemplate();

        IndexSettings indexTemplateSettings = indexTemplate.template().settings().index();

        IndexSettingsLifecycle lifecycle = indexTemplateSettings.lifecycle();

        System.out.println(lifecycle);
        // TODO the rest
    }

    @Test
    public void testGetIndexLifecycle() throws IOException {
        InputStream envAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Assert.assertNotNull(".env file not found in classpath", envAsStream);
        Properties env = new Properties();
        env.load(envAsStream);

        String url = env.getProperty("elasticsearch.url");
        String username = env.getProperty("elasticsearch.username");
        String password = env.getProperty("elasticsearch.password");

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        RestClient restClient = RestClient.builder(HttpHost.create(url))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient elasticsearchClient = new ElasticsearchClient(elasticsearchTransport);
        GetLifecycleResponse getLifecycleResponse = elasticsearchClient.ilm().getLifecycle(b -> b.name("logs-apm.app_logs-default_policy"));
        Lifecycle lifecycle = getLifecycleResponse.get("logs-apm.app_logs-default_policy");
        Phases phases = lifecycle.policy().phases();
        // {"rollover":{"max_size":"50gb","max_age":"30d"},"set_priority":{"priority":100}}

        List<String> retentionPolicy = new ArrayList<>();

        retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.hot(), "hot"));
        retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.warm(), "warm"));
        retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.cold(), "cold"));
        retentionPolicy.add(ElasticsearchLogStorageRetriever.prettyPrintPhaseRetentionPolicy(phases.delete(), "delete"));

        System.out.println(retentionPolicy.stream().collect(Collectors.joining(", ")));
    }

    /**
     * See https://github.com/elastic/elasticsearch-java/issues/212
     */
    @Test
    public void testIndexExistsWithInvalidCredentials() throws Exception {
        InputStream envAsStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Assert.assertNotNull(".env file not found in classpath", envAsStream);
        Properties env = new Properties();
        env.load(envAsStream);

        String url = env.getProperty("elasticsearch.url");
        String username = env.getProperty("elasticsearch.username");
        String password = "INVALID PASSWORD";

        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        RestClient restClient = RestClient.builder(HttpHost.create(url))
            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider))
            .build();
        RestClientTransport elasticsearchTransport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        ElasticsearchClient elasticsearchClient = new ElasticsearchClient(elasticsearchTransport);
        try {
            boolean indexTemplateExists = elasticsearchClient.indices().existsIndexTemplate(b -> b.name("logs-apm.app")).value();
            Assert.fail("Expected ElasticsearchException with ElasticsearchException.type='security_exception' and ElasticsearchException.status=401. Didn't expect exists=" + indexTemplateExists);
        } catch (ElasticsearchException e) {
            ErrorCause errorCause = e.error();
            int status = e.status();
            if (ElasticsearchFields.ERROR_CAUSE_TYPE_SECURITY_EXCEPTION.equals(errorCause.type())) {
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    // OK it's the behaviour we want
                } else {
                    Assert.fail("Expected ElasticsearchException with ElasticsearchException.type='security_exception' and ElasticsearchException.status=401. Didn't expect " + e);
                }
            }
        }

    }
}
