/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import groovy.text.GStringTemplateEngine;
import hudson.util.FormValidation;
import io.jenkins.plugins.opentelemetry.TemplateBindingsProvider;
import io.jenkins.plugins.opentelemetry.jenkins.HttpAuthHeaderFactory;
import io.opentelemetry.sdk.internal.JavaVersionSpecific;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class LokiLogStorageRetrieverIT {

    @Test
    void test_checkLokiSetup() throws Exception {
        System.out.println("OTel Java Specific Version: " + JavaVersionSpecific.get());

        InputStream env = Thread.currentThread().getContextClassLoader().getResourceAsStream(".env");
        Properties properties = new Properties();
        properties.load(env);
        String lokiUser = properties.getProperty("loki.user");
        String lokiPassword = properties.getProperty("loki.apiKey");
        String lokiUrl = properties.getProperty("loki.url");

        System.out.println(lokiUrl);
        System.out.println(lokiUser);

        try (LokiLogStorageRetriever lokiLogStorageRetriever = new LokiLogStorageRetriever(
                lokiUrl,
                false,
                HttpAuthHeaderFactory.createFactoryUsernamePassword(lokiUser, lokiPassword),
                Optional.empty(),
                new GStringTemplateEngine().createTemplate("mock"),
                TemplateBindingsProvider.empty(),
                "jenkins",
                Optional.of("jenkins"))) {
            List<FormValidation> formValidations = lokiLogStorageRetriever.checkLokiSetup();
            System.out.println(formValidations);
        }
    }
}
