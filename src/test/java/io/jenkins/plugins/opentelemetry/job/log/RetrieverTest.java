/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import io.jenkins.plugins.opentelemetry.job.log.es.ElasticsearchRetriever;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Test the class to retrieve the logs from Elasticsearch.
 */
public class RetrieverTest {

    @Rule
    public ElasticsearchContainer esContainer = new ElasticsearchContainer();

    @BeforeClass
    public static void requiresDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    @Before
    public void setUp() throws Exception {
        esContainer.createLogIndex();
    }

    @Test
    public void testRetrieve() throws IOException {
        ElasticsearchRetriever elasticsearchRetriever = new ElasticsearchRetriever(esContainer.getUrl(), ElasticsearchContainer.USER_NAME,
            ElasticsearchContainer.PASSWORD, ElasticsearchContainer.INDEX
        );
        SearchResponse searchResponse = elasticsearchRetriever.search("foo");
        String scrollId = searchResponse.getScrollId();
        SearchHit[] searchHits = searchResponse.getHits().getHits();
        int counter = searchHits.length;

        while (searchHits != null && searchHits.length > 0) {
            searchResponse = elasticsearchRetriever.next(scrollId);
            scrollId = searchResponse.getScrollId();
            searchHits = searchResponse.getHits().getHits();
            counter += searchHits.length;
        }

        ClearScrollResponse clearScrollResponse = elasticsearchRetriever.clear(scrollId);
        assertTrue(clearScrollResponse.isSucceeded());
        assertEquals(counter, 100);
    }
}
