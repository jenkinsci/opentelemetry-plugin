/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.elastic;

import io.jenkins.plugins.opentelemetry.job.log.LogsQueryResult;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.junit.Test;

import java.io.IOException;

public class ElasticsearchRetrieverIT {

    @Test
    public void test() throws IOException {
        String url = "https://my-deployment-76ea19.es.europe-west1.gcp.cloud.es.io:9243";
        String username = "jenkins";
        String password = "W0&7282TMPgS4Qk";
        String indexPattern = "logs-apm.app-*";

        ElasticsearchLogStorageRetriever elasticsearchLogStorageRetriever = new ElasticsearchLogStorageRetriever(
            url,
            new UsernamePasswordCredentials(username, password),
            indexPattern);
        LogsQueryResult logsQueryResult = elasticsearchLogStorageRetriever.overallLog("ed4e940f4a2f817e9449ed2d3d7248cb", "", null);
    }
}
