/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.queue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import hudson.model.Queue;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.FullControlOnceLoggedInAuthorizationStrategy;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MonitoringQueueListenerTest {

    private JenkinsRule jenkinsRule;

    @BeforeEach
    void beforeEach(JenkinsRule jenkinsRule) {
        this.jenkinsRule = jenkinsRule;
    }

    @Test
    @Issue("https://github.com/jenkinsci/opentelemetry-plugin/issues/1174")
    void queueMetricsReadItemsWithSystemAuthentication() throws Exception {
        jenkinsRule.jenkins.setSecurityRealm(jenkinsRule.createDummySecurityRealm());
        FullControlOnceLoggedInAuthorizationStrategy authorizationStrategy =
                new FullControlOnceLoggedInAuthorizationStrategy();
        authorizationStrategy.setAllowAnonymousRead(false);
        jenkinsRule.jenkins.setAuthorizationStrategy(authorizationStrategy);

        int originalNumExecutors = jenkinsRule.jenkins.getNumExecutors();
        FreeStyleProject project = jenkinsRule.createFreeStyleProject("queued-project");
        try {
            jenkinsRule.jenkins.setNumExecutors(0);
            project.scheduleBuild2(0);

            await().atMost(15, SECONDS).until(() -> {
                try (ACLContext ignored = ACL.as2(ACL.SYSTEM2)) {
                    return jenkinsRule.jenkins.getQueue().getItems().length > 0;
                }
            });

            Queue.Item[] anonymousVisibleItems;
            try (ACLContext ignored = ACL.as2(Jenkins.ANONYMOUS2)) {
                anonymousVisibleItems = jenkinsRule.jenkins.getQueue().getItems();
            }
            assertThat(anonymousVisibleItems.length, is(0));

            MonitoringQueueListener listener = ExtensionList.lookupSingleton(MonitoringQueueListener.class);
            Queue.Item[] metricsVisibleItems = listener.getQueueItemsForMetrics();
            assertThat(metricsVisibleItems.length, greaterThanOrEqualTo(1));
        } finally {
            jenkinsRule.jenkins.getQueue().cancel(project);
            jenkinsRule.jenkins.setNumExecutors(originalNumExecutors);
        }
    }
}
