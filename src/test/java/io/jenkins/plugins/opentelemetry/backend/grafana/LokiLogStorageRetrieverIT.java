/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.junit.Test;

import groovy.text.GStringTemplateEngine;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.opentelemetry.sdk.internal.JavaVersionSpecific;

public class LokiLogStorageRetrieverIT {

    @Test
    public void test_checkLokiSetup() throws Exception {
        System.out.println("OTel Java Specific Version: " + JavaVersionSpecific.get());

        InputStream env = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Properties properties = new Properties();
        properties.load(env);
        String lokiUser = properties.getProperty("loki.user");
        String lokiPassword = properties.getProperty("loki.apiKey");
        Optional<Credentials> lokiCredentials = Optional.of(new UsernamePasswordCredentials(lokiUser, lokiPassword.toCharArray()));

        String lokiUrl = properties.getProperty("loki.url");

        System.out.println(lokiUrl);
        System.out.println(lokiUser);

        try (LokiLogStorageRetriever lokiLogStorageRetriever = new LokiLogStorageRetriever(
            lokiUrl,
            false,
            lokiCredentials,
            Optional.empty(),
            new GStringTemplateEngine().createTemplate("mock"),
            TemplateBindingsProvider.empty(),
            "jenkins",
             Optional.of("jenkins")
        )) {
            List<FormValidation> formValidations = lokiLogStorageRetriever.checkLokiSetup();
            System.out.println(formValidations);
        }
    }
}