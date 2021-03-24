/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import org.junit.Assert;
import org.junit.Test;

public class SpanNamingStrategyTest {

    // org.jenkinsci.plugins.github_branch_source.PullRequestGHEventSubscriber
    // jenkins.scm.api.mixin.ChangeRequestCheckoutStrategy
    // e.g. "my-war/PR-2", "my-war/PR-2-merge", "my-war/PR-2-head"
    // io.jenkins.plugins.gitlabbranchsource.GitLabSCMSource
    // e.g. "my-war/MR-2", "my-war/MR-2-merge", "my-war/MR-2-head"

    SpanNamingStrategy spanNamingStrategy = new SpanNamingStrategy();

    /**
     * GitHub and BitBucket Pull Requests
     */
    @Test
    public void test_pull_request() {
        verifyRootSpanName("my-war/PR-2", "my-war/PR-{number}");
    }

    /**
     * GitHub and BitBucket Pull Requests
     */
    @Test
    public void test_pull_request_with_head_checkout_strategy() {
        verifyRootSpanName("my-war/PR-2-head", "my-war/PR-{number}");
    }

    /**
     * GitHub and BitBucket Pull Requests
     */
    @Test
    public void test_pull_request_with_merge_checkout_strategy() {
        verifyRootSpanName("my-war/PR-2-merge", "my-war/PR-{number}");
    }

    /**
     * GitLab Merge Requests
     */
    @Test
    public void test_merge_request() {
        verifyRootSpanName("my-war/MR-2", "my-war/MR-{number}");
    }

    /**
     * GitLab Merge Requests
     */
    @Test
    public void test_merge_request_with_head_checkout_strategy() {
        verifyRootSpanName("my-war/MR-2-head", "my-war/MR-{number}");
    }

    /**
     * GitLab Merge Requests
     */
    @Test
    public void test_merge_request_with_merge_checkout_strategy() {
        verifyRootSpanName("my-war/MR-2-merge", "my-war/MR-{number}");
    }

    private void verifyRootSpanName(String jobFullName, String expected) {
        final String actual = spanNamingStrategy.getChangeRequestRootSpanName(jobFullName);
        Assert.assertEquals(expected, actual);
    }
}