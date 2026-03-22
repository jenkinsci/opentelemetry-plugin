/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;

/**
 * OpenTelemetry attribute keys and metric names for GitHub API instrumentation.
 */
public final class GitHubAttributes {

    /** OTel attribute key for the GitHub App installation identifier. */
    public static final AttributeKey<Long> GITHUB_APP_ID = AttributeKey.longKey("github.app.id");

    /** OTel attribute key for the GitHub App name. */
    public static final AttributeKey<String> GITHUB_APP_NAME = AttributeKey.stringKey("github.app.name");
    /** OTel attribute key for the GitHub App owner (organization or user). */
    public static final AttributeKey<String> GITHUB_APP_OWNER = AttributeKey.stringKey("github.app.owner");
    /** OTel attribute key describing the authentication method used for GitHub API calls. */
    public static final AttributeKey<String> GITHUB_AUTHENTICATION = AttributeKey.stringKey("github.authentication");
    /** OTel attribute key for the GitHub API base URL. */
    public static final AttributeKey<String> GITHUB_API_URL = AttributeKey.stringKey("github.api.url");

    /** Metric name for the number of remaining GitHub API rate-limit requests. */
    public static final String GITHUB_API_RATE_LIMIT_REMAINING_REQUESTS = "github.api.rate_limit.remaining_requests";
}
