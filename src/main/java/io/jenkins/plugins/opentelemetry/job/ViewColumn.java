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
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;
import java.util.stream.Collectors;

public class ViewColumn extends ListViewColumn {

    @DataBoundConstructor
    public ViewColumn() {
        super();
    }

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

    @Extension
    public static class DescriptorImpl extends ListViewColumnDescriptor {

        public DescriptorImpl() {
        }

        @Override
        public String getDisplayName() {
            return Messages.observabilityColumn();
        }

        public boolean shownByDefault() {
            return false;
        }
    }
}
