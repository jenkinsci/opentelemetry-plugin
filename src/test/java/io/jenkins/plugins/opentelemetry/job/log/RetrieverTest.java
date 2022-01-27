/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.jenkins.plugins.opentelemetry.job.log;

import io.jenkins.plugins.opentelemetry.job.log.es.Retriever;
import org.elasticsearch.action.search.ClearScrollResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test the class to retrieve the logs from Elasticsearch.
 */
public class RetrieverTest {

    @Rule
    public ElasticsearchContainer esContainer = new ElasticsearchContainer();

    @Before
    public void setUp() throws Exception {
        esContainer.createLogIndex();
    }

    @Test
    public void testRetrieve() throws IOException {
        Retriever retriever = new Retriever(esContainer.getUrl(), ElasticsearchContainer.USER_NAME,
            ElasticsearchContainer.PASSWORD, ElasticsearchContainer.INDEX
        );
        //FIXME check the search string is correct.
        SearchResponse searchResponse = retriever.search(new BuildInfo("foo", 0, null).getContext().get("KEY"));
        String scrollId = searchResponse.getScrollId();
        SearchHit[] searchHits = searchResponse.getHits().getHits();
        int counter = searchHits.length;

        while (searchHits != null && searchHits.length > 0) {
            searchResponse = retriever.next(scrollId);
            scrollId = searchResponse.getScrollId();
            searchHits = searchResponse.getHits().getHits();
            counter += searchHits.length;
        }

        ClearScrollResponse clearScrollResponse = retriever.clear(scrollId);
        assertTrue(clearScrollResponse.isSucceeded());
        assertEquals(counter, 100);
    }

    @Test
    public void testRetrieveNodeId() throws IOException {
        Retriever retriever = new Retriever(esContainer.getUrl(), ElasticsearchContainer.USER_NAME,
            ElasticsearchContainer.PASSWORD, ElasticsearchContainer.INDEX
        );
        //FIXME set the correct search string
        SearchResponse searchResponse = retriever.search(new BuildInfo("foo", 0, null).getContext().get("KEY"));
        String scrollId = searchResponse.getScrollId();
        SearchHit[] searchHits = searchResponse.getHits().getHits();
        int counter = searchHits.length;

        while (searchHits != null && searchHits.length > 0) {
            searchResponse = retriever.next(scrollId);
            scrollId = searchResponse.getScrollId();
            searchHits = searchResponse.getHits().getHits();
            counter += searchHits.length;
        }

        ClearScrollResponse clearScrollResponse = retriever.clear(scrollId);
        assertTrue(clearScrollResponse.isSucceeded());
        assertEquals(counter, 50);
    }

}
