/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.runhandler;


import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import javaposse.jobdsl.plugin.actions.SeedJobAction;
import javaposse.jobdsl.plugin.actions.SeedJobTransientActionFactory;
import jenkins.YesNoMaybe;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Optional;


@Extension(optional = true, dynamicLoadable = YesNoMaybe.YES)
public class JobDslRunHandler implements RunHandler {

    private SeedJobTransientActionFactory seedJobTransientActionFactory;

    private boolean collapseJobName;

    public JobDslRunHandler() throws ClassNotFoundException {
        // verify the class is available to force the contract `@Extension(optional = true)`
        Class.forName(SeedJobAction.class.getName());
    }

    @Override
    public boolean matches(@NonNull Run<?, ?> run) {
        Job<?, ?> job = run.getParent();
        // perf optimization: directly lookup up in the SeedJobTransientActionFactory over `job.getAction(SeedJobAction.class)`
        Collection<? extends Action> actions = seedJobTransientActionFactory.createFor(job);
        return !actions.isEmpty();
    }


    @NonNull
    @Override
    public String getPipelineShortName(@NonNull Run<?, ?> run) {
        Job<?, ?> job = run.getParent();
        // perf optimization: directly lookup up in the SeedJobTransientActionFactory over `job.getAction(SeedJobAction.class)`
        Collection<? extends Action> actions = seedJobTransientActionFactory.createFor(job);

        SeedJobAction seedJobAction = (SeedJobAction) actions.stream().filter(action -> action instanceof SeedJobAction).findFirst().orElseThrow(IllegalStateException::new);

        // TODO understand the difference between seedJobAction.getTemplateJob() and seedJobAction.getSeedJob()
        Optional<Item> seedJob = Optional.ofNullable(seedJobAction.getSeedJob());

        return collapseJobName? job.getFullName() : seedJob.map(Item::getFullName).map(fn -> "Job from seed '" + fn + "'").orElse(job.getFullName());
    }

    @Override
    public void enrichPipelineRunSpan(@NonNull Run<?, ?> run, @NonNull SpanBuilder spanBuilder) {
        Job<?, ?> job = run.getParent();
        // perf optimization: directly lookup up in the SeedJobTransientActionFactory over `job.getAction(SeedJobAction.class)`
        Collection<? extends Action> actions = seedJobTransientActionFactory.createFor(job);

        SeedJobAction seedJobAction = (SeedJobAction) actions.stream().filter(action -> action instanceof SeedJobAction).findFirst().orElseThrow(IllegalStateException::new);

        // TODO understand the difference between seedJobAction.getTemplateJob() and seedJobAction.getSeedJob()
        Optional<Item> seedJob = Optional.ofNullable(seedJobAction.getSeedJob());

        seedJob.map(Item::getFullName).ifPresent(templateFullName -> spanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_TEMPLATE_ID, templateFullName));
        seedJob.map(Item::getUrl).ifPresent(templateUrl -> spanBuilder.setAttribute(ExtendedJenkinsAttributes.CI_PIPELINE_TEMPLATE_URL, templateUrl));

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
