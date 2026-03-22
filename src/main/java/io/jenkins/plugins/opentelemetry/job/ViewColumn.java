/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import io.jenkins.plugins.opentelemetry.Messages;
import java.util.List;
import java.util.stream.Collectors;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A Jenkins list-view column that renders observability backend links for the last completed build.
 */
public class ViewColumn extends ListViewColumn {

    /**
     * Creates the observability view column.
     */
    @DataBoundConstructor
    public ViewColumn() {
        super();
    }

    /**
     * Returns the observability backend links for the last completed build of the given job.
     *
     * @param job the Jenkins job
     * @return list of backend links, or {@code null} if no completed build exists
     */
    public List<MonitoringAction.ObservabilityBackendLink> getLinks(final Job<?, ?> job) {
        Run<?, ?> lastCompletedBuild = job.getLastCompletedBuild();
        if (lastCompletedBuild == null) {
            return null;
        }
        job.getLastCompletedBuild().getActions(MonitoringAction.class);
        return lastCompletedBuild.getActions(MonitoringAction.class).stream()
                .map(MonitoringAction::getLinks)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    /**
     * Descriptor for {@link ViewColumn}.
     */
    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {

        /**
         * Creates the descriptor.
         */
        public DescriptorImpl() {}

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.observabilityColumn();
        }

        /**
         * Returns {@code false} so the column is not shown in views by default.
         *
         * @return {@code false}
         */
        public boolean shownByDefault() {
            return false;
        }
    }
}
