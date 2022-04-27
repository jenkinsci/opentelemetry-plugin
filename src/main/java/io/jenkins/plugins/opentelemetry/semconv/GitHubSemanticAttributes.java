/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;

public final class GitHubSemanticAttributes {

    public static final AttributeKey<String> GITHUB_APP_ID = AttributeKey.stringKey("github.app.id");

    public static final AttributeKey<String> GITHUB_APP_NAME = AttributeKey.stringKey("github.app.name");
    public static final AttributeKey<String> GITHUB_APP_OWNER = AttributeKey.stringKey("github.app.owner");
    public static final AttributeKey<String> GITHUB_AUTHENTICATION = AttributeKey.stringKey("github.authentication");
    public static final AttributeKey<String> GITHUB_API_URL = AttributeKey.stringKey("github.api.url");

}
