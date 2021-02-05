/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import com.google.common.collect.Maps;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.SpanBuilderMock;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.Map;

public class ScmStepHandlerTest {

    @Test
    public void testHttpsGithubUrlParsing() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://github.com/open-telemetry/opentelemetry-java");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();
        MatcherAssert.assertThat(attributes.get(SemanticAttributes.NET_PEER_NAME), Matchers.equalTo("github.com"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));

    }
    @Test
    public void testHttpsGithubUrlWithSuffixParsing() throws Exception {
        SpanBuilderMock spanBuilder = testGithubUrl("https://github.com/open-telemetry/opentelemetry-java.git");
        Map<AttributeKey, Object> attributes = spanBuilder.getAttributes();
        MatcherAssert.assertThat(attributes.get(SemanticAttributes.NET_PEER_NAME), Matchers.equalTo("github.com"));
        MatcherAssert.assertThat(attributes.get(JenkinsOtelSemanticAttributes.GIT_REPOSITORY), Matchers.equalTo("open-telemetry/opentelemetry-java"));

    }

    private SpanBuilderMock testGithubUrl(String githubUrl) throws Exception {
        SpanBuilderMock spanBuilder = new SpanBuilderMock("git");
        ScmStepHandler handler = new ScmStepHandler();
        Map<String, Object> arguments = Maps.newHashMap();
        arguments.put("url", githubUrl);

        handler.handle(arguments, spanBuilder);
        return spanBuilder;
    }
}
