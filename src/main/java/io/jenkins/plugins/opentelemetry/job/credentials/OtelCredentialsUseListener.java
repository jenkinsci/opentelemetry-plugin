/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.credentials;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsUseListener;
import com.cloudbees.plugins.credentials.common.IdCredentials;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.Node;
import hudson.model.Run;
import io.jenkins.plugins.opentelemetry.JenkinsOpenTelemetryPluginConfiguration;
import io.jenkins.plugins.opentelemetry.job.OtelTraceService;
import io.jenkins.plugins.opentelemetry.semconv.ExtendedJenkinsAttributes;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Records each use of a credential by a build as a span event
 * {@value ExtendedJenkinsAttributes#EVENT_NAME_CREDENTIALS_USE}, when the administrator turned "Record credential use
 * as span events" on.
 *
 * <p>The event has the type, the class and the scope of the credential, and its ID only when "Include credential IDs"
 * is on. It never has a secret, a user name or a description.
 *
 * <p>The listener runs on the thread that uses the credential (the core calls the listeners one after the other, as
 * SYSTEM, and logs a warning for each exception): it does no I/O, takes no lock for long and catches everything.
 *
 * <p>Only the uses by a {@link Run} are recorded. The uses by a {@link Node} (agent launch) and by an {@link Item}
 * (branch scan, lightweight checkout) have no run and no useful span.
 */
@Extension
public class OtelCredentialsUseListener implements CredentialsUseListener {
    private static final Logger LOGGER = Logger.getLogger(OtelCredentialsUseListener.class.getName());

    /** Maximum number of {@value ExtendedJenkinsAttributes#EVENT_NAME_CREDENTIALS_USE} events on one span. */
    static final int MAX_EVENTS_PER_SPAN = 32;

    /** Number of spans for which the events are counted. Old entries are dropped: the spans have ended. */
    private static final int TRACKED_SPANS = 2_000;

    private final BooleanSupplier recordEvents;
    private final BooleanSupplier includeIds;
    private final Supplier<OtelTraceService> traceService;
    private final Cache<String, Set<String>> eventsPerSpan = CacheBuilder.newBuilder()
            .maximumSize(TRACKED_SPANS)
            .expireAfterAccess(6, TimeUnit.HOURS)
            .build();

    public OtelCredentialsUseListener() {
        this(
                () -> JenkinsOpenTelemetryPluginConfiguration.get().isRecordCredentialUseEvents(),
                () -> JenkinsOpenTelemetryPluginConfiguration.get().isIncludeCredentialIds(),
                () -> ExtensionList.lookupSingleton(OtelTraceService.class));
    }

    OtelCredentialsUseListener(
            BooleanSupplier recordEvents, BooleanSupplier includeIds, Supplier<OtelTraceService> traceService) {
        this.recordEvents = recordEvents;
        this.includeIds = includeIds;
        this.traceService = traceService;
    }

    @Override
    public void onUse(Credentials c, Run run) {
        try {
            if (!recordEvents.getAsBoolean()) {
                return;
            }
            OtelTraceService service = traceService.get();
            Span span = chooseSpan(service, run);
            if (!span.getSpanContext().isValid()) {
                LOGGER.log(Level.FINE, () -> "No span for the use of a credential by " + run);
                return;
            }
            if (!markUse(span, c)) {
                return;
            }
            span.addEvent(ExtendedJenkinsAttributes.EVENT_NAME_CREDENTIALS_USE, attributes(c));
        } catch (RuntimeException e) {
            // The core logs a WARNING for each exception of a listener
            LOGGER.log(Level.FINE, e, () -> "Could not record the use of a credential");
        }
    }

    /** Nothing: a use by an agent launch has no run and no useful span. */
    @Override
    public void onUse(Credentials c, Node node) {}

    /** Nothing: a use by an item (branch scan, lightweight checkout) has no run and no useful span. */
    @Override
    public void onUse(Credentials c, Item item) {}

    /**
     * The current span if it is recording and in the trace of the run (a thread of a pool may carry the context of
     * another build, issue 1312), else the span of the run phase.
     */
    @NonNull
    private Span chooseSpan(OtelTraceService service, Run<?, ?> run) {
        Span current = Span.current();
        if (current.getSpanContext().isValid() && current.isRecording()) {
            Span root = service.getPipelineRootSpan(run);
            if (root.getSpanContext().isValid()
                    && root.getSpanContext()
                            .getTraceId()
                            .equals(current.getSpanContext().getTraceId())) {
                return current;
            }
        }
        return service.getSpan(run);
    }

    /** @return true if an event for this credential may be added to the span: not a duplicate, below the cap */
    private boolean markUse(Span span, Credentials c) {
        String spanKey =
                span.getSpanContext().getTraceId() + span.getSpanContext().getSpanId();
        Set<String> seen;
        try {
            seen = eventsPerSpan.get(spanKey, HashSet::new);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e);
        }
        String credentialKey = c instanceof IdCredentials idCredentials
                ? idCredentials.getId()
                : c.getClass().getName();
        // The cap and the add must be one atomic step: two threads can use credentials of the same span.
        synchronized (seen) {
            return seen.size() < MAX_EVENTS_PER_SPAN && seen.add(credentialKey);
        }
    }

    private Attributes attributes(Credentials c) {
        CredentialsDescriptor descriptor = c.getDescriptor();
        CredentialsScope scope = c.getScope();
        AttributesBuilder attributes = Attributes.builder()
                .put(
                        ExtendedJenkinsAttributes.JENKINS_CREDENTIALS_CLASS,
                        c.getClass().getName());
        if (descriptor != null) {
            attributes.put(ExtendedJenkinsAttributes.JENKINS_CREDENTIALS_TYPE, descriptor.getDisplayName());
        }
        if (scope != null) {
            attributes.put(ExtendedJenkinsAttributes.JENKINS_CREDENTIALS_SCOPE, scope.name());
        }
        if (c instanceof IdCredentials idCredentials && includeIds.getAsBoolean()) {
            attributes.put(ExtendedJenkinsAttributes.JENKINS_CREDENTIALS_ID, idCredentials.getId());
        }
        return attributes.build();
    }
}
