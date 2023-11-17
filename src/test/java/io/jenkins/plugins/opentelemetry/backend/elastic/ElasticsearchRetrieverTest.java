/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.backend.elastic;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.DockerComposeContainer;

import co.elastic.clients.elasticsearch.core.ClearScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;

import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.Credentials;

import java.io.IOException;

import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the class to retrieve the logs from Elasticsearch.
 */

public class ElasticsearchRetrieverTest {

    @ClassRule
    public static ElasticsearchContainer environment = new ElasticsearchContainer();

    @BeforeClass
    public static void requiresDocker() {
       assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @Test
    public void testRetrieve() throws IOException {
        // Credentials credentials = new UsernamePasswordCredentials(ElasticsearchContainer.USER_NAME,
        //    ElasticsearchContainer.PASSWORD);
        // ElasticsearchLogStorageRetriever elasticsearchRetriever = new ElasticsearchLogStorageRetriever(
        //    environment.getEsUrl(),
        //    credentials,
        //    ElasticsearchContainer.INDEX);
        // SearchResponse searchResponse = elasticsearchRetriever.search("foo", null);
        // String scrollId = searchResponse.getScrollId();
        // SearchHit[] searchHits = searchResponse.getHits().getHits();
        // int counter = searchHits.length;

        // while (searchHits != null && searchHits.length > 0) {
        //    searchResponse = elasticsearchRetriever.next(scrollId);
        //    scrollId = searchResponse.getScrollId();
        //    searchHits = searchResponse.getHits().getHits();
        //    counter += searchHits.length;
        // }

        // ClearScrollResponse clearScrollResponse = elasticsearchRetriever.clear(scrollId);
        // assertTrue(clearScrollResponse.isSucceeded());
        // assertEquals(counter, 100);
    }
}
