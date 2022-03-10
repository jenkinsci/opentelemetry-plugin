/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.runhandler;

import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.semconv.JenkinsOtelSemanticAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javaposse.jobdsl.plugin.actions.SeedJobAction;
import javaposse.jobdsl.plugin.actions.SeedJobTransientActionFactory;
import jenkins.YesNoMaybe;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;


@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class JobDslRunHandler implements RunHandler {

    private SeedJobTransientActionFactory seedJobTransientActionFactory;

    private boolean collapseJobName;

    public JobDslRunHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(SeedJobAction.class.getName());
    }

    @Override
    public boolean canCreateSpanBuilder(@NonNull Run run) {
        Job job = run.getParent();
        // perf optimization: directly lookup up in the SeedJobTransientActionFactory over `job.getAction(SeedJobAction.class)`
        Collection<? extends Action> actions = seedJobTransientActionFactory.createFor(job);
        return !actions.isEmpty();
    }

    @NonNull
    @Override
    public SpanBuilder createSpanBuilder(@NonNull Run run, @NonNull Tracer tracer) {
        Job job = run.getParent();
        // perf optimization: directly lookup up in the SeedJobTransientActionFactory over `job.getAction(SeedJobAction.class)`
        Collection<? extends Action> actions = seedJobTransientActionFactory.createFor(job);

        SeedJobAction seedJobAction = (SeedJobAction) actions.stream().filter(action -> action instanceof SeedJobAction).findFirst().orElseThrow(() -> new IllegalStateException());

        // TODO understand the difference between seedJobAction.getTemplateJob() and seedJobAction.getSeedJob()
        Item seedJob = seedJobAction.getSeedJob();

        String templateFullName;
        String templateUrl;
        String spanName;
        if (seedJob == null) {
            templateFullName = null;
            templateUrl = null;
            spanName = job.getFullName();
        } else {
            templateFullName = seedJob.getFullName();
            templateUrl = seedJob.getUrl();
            spanName = collapseJobName ? "Job from seed '" + templateFullName + "'" : job.getFullName();
        }

        SpanBuilder spanBuilder = tracer.spanBuilder(JenkinsOtelSemanticAttributes.CI_PIPELINE_RUN_ROOT_SPAN_NAME_PREFIX + spanName);
        if (templateFullName != null) {
            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_TEMPLATE_ID, templateFullName);
        }
        if (templateUrl != null) {
            spanBuilder.setAttribute(JenkinsOtelSemanticAttributes.CI_PIPELINE_TEMPLATE_URL, templateUrl);
        }
        return spanBuilder;
    }

    @Override
    public void configure(ConfigProperties config) {
        collapseJobName = Boolean.TRUE.equals(config.getBoolean("otel.instrumentation.jenkins.job.dsl.collapse.job.name"));
    }

    @Override
    public int ordinal() {
        return Integer.MAX_VALUE - 1;
    }

    @Inject
    public void setSeedJobTransientActionFactory(@NonNull SeedJobTransientActionFactory seedJobTransientActionFactory) {
        this.seedJobTransientActionFactory = seedJobTransientActionFactory;
    }
}
