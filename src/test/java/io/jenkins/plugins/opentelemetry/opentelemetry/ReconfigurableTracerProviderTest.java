/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.api.trace.TracerProvider;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class ReconfigurableTracerProviderTest {
    @org.junit.Test
    public void test() {
        ReconfigurableTracerProvider tracerProvider = new ReconfigurableTracerProvider();

        ReconfigurableTracerProviderTest.TracerProviderMock tracerProviderImpl_1 = new ReconfigurableTracerProviderTest.TracerProviderMock();
        tracerProvider.setDelegate(tracerProviderImpl_1);

        ReconfigurableTracerProvider.ReconfigurableTracer authenticationTracer = (ReconfigurableTracerProvider.ReconfigurableTracer) tracerProvider
            .tracerBuilder("io.jenkins.authentication")
            .setInstrumentationVersion("1.0.0")
            .build();

        ReconfigurableTracerProviderTest.TracerMock authenticationTracerImpl = (ReconfigurableTracerProviderTest.TracerMock) authenticationTracer.delegate;
        assertEquals("io.jenkins.authentication", authenticationTracerImpl.instrumentationScopeName);
        assertNull(authenticationTracerImpl.schemaUrl);
        assertEquals("1.0.0", authenticationTracerImpl.instrumentationVersion);
        assertEquals(tracerProviderImpl_1.id, authenticationTracerImpl.tracerProviderId);


        ReconfigurableTracerProvider.ReconfigurableTracer buildTracer = (ReconfigurableTracerProvider.ReconfigurableTracer) tracerProvider
            .tracerBuilder("io.jenkins.build")
            .setSchemaUrl("https://jenkins.io/build")
            .build();
        ReconfigurableTracerProviderTest.TracerMock buildTracerImpl = (ReconfigurableTracerProviderTest.TracerMock) buildTracer.delegate;
        assertEquals("io.jenkins.build", buildTracerImpl.instrumentationScopeName);
        assertEquals("https://jenkins.io/build", buildTracerImpl.schemaUrl);
        assertNull(buildTracerImpl.instrumentationVersion);
        assertEquals(tracerProviderImpl_1.id, buildTracerImpl.tracerProviderId);

        ReconfigurableTracerProvider.ReconfigurableTracer buildTracerShouldBeTheSameInstance = (ReconfigurableTracerProvider.ReconfigurableTracer) tracerProvider
            .tracerBuilder("io.jenkins.build")
            .setSchemaUrl("https://jenkins.io/build")
            .build();

        assertEquals(buildTracer, buildTracerShouldBeTheSameInstance);

        ReconfigurableTracerProviderTest.TracerProviderMock tracerProviderImpl_2 = new ReconfigurableTracerProviderTest.TracerProviderMock();
        assertNotEquals(tracerProviderImpl_1.id, tracerProviderImpl_2.id);

        // CHANGE THE IMPLEMENTATION OF THE EVENT TRACER PROVIDER
        tracerProvider.setDelegate(tracerProviderImpl_2);

        // VERIFY THE DELEGATE IMPL HAS CHANGED WHILE THE PARAMS REMAINS UNCHANGED
        ReconfigurableTracerProviderTest.TracerMock authenticationTracerImpl_2 = (ReconfigurableTracerProviderTest.TracerMock) authenticationTracer.delegate;
        assertEquals("io.jenkins.authentication", authenticationTracerImpl_2.instrumentationScopeName);
        assertNull(authenticationTracerImpl_2.schemaUrl);
        assertEquals("1.0.0", authenticationTracerImpl_2.instrumentationVersion);
        assertEquals(tracerProviderImpl_2.id, authenticationTracerImpl_2.tracerProviderId);

        ReconfigurableTracerProviderTest.TracerMock buildTracerImpl_2 = (ReconfigurableTracerProviderTest.TracerMock) buildTracer.delegate;

        assertEquals("io.jenkins.build", buildTracerImpl_2.instrumentationScopeName);
        assertEquals("https://jenkins.io/build", buildTracerImpl_2.schemaUrl);
        assertNull(buildTracerImpl_2.instrumentationVersion);
        assertEquals(tracerProviderImpl_2.id, buildTracerImpl_2.tracerProviderId);
    }


    static class TracerProviderMock implements TracerProvider {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;

        public TracerProviderMock() {
            this.id = "TracerProviderMock-" + ID_SOURCE.incrementAndGet();
        }

        @Override
        public TracerBuilder tracerBuilder(String instrumentationScopeName) {
            return new ReconfigurableTracerProviderTest.TracerBuilderMock(instrumentationScopeName, id);
        }

        @Override
        public Tracer get(String instrumentationScopeName) {
            return new ReconfigurableTracerProviderTest.TracerMock(instrumentationScopeName, id);
        }

        @Override
        public Tracer get(String instrumentationScopeName, String instrumentationScopeVersion) {
            return new ReconfigurableTracerProviderTest.TracerMock(instrumentationScopeName, instrumentationScopeVersion, id);
        }
    }

    static class TracerMock implements Tracer {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);

        final String instrumentationScopeName;
        final String tracerProviderId;
        final String id;

        final String schemaUrl;
        final String instrumentationVersion;

        public TracerMock(String instrumentationScopeName, String tracerProviderId) {
            this(instrumentationScopeName, tracerProviderId, null, null);
        }
        public TracerMock(String instrumentationScopeName, String instrumentationVersion, String tracerProviderId) {
            this(instrumentationScopeName, tracerProviderId, null, instrumentationVersion);
        }

        public TracerMock(String instrumentationScopeName, String tracerProviderId, @Nullable String schemaUrl, @Nullable String instrumentationVersion) {
            this.id = "TracerMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.tracerProviderId = tracerProviderId;
            this.schemaUrl = schemaUrl;
            this.instrumentationVersion = instrumentationVersion;
        }

        @Override
        public SpanBuilder spanBuilder(String spanName) {
            throw new UnsupportedOperationException();
        }
    }

    static class TracerBuilderMock implements TracerBuilder {
        static AtomicInteger ID_SOURCE = new AtomicInteger(0);
        final String id;
        final String instrumentationScopeName;
        final String tracerProviderId;
        String schemaUrl;
        String instrumentationVersion;


        public TracerBuilderMock(String instrumentationScopeName, String tracerProviderId) {
            this.id = "TracerBuilderMock-" + ID_SOURCE.incrementAndGet();
            this.instrumentationScopeName = instrumentationScopeName;
            this.tracerProviderId = tracerProviderId;
        }

        @Override
        public TracerBuilder setSchemaUrl(String schemaUrl) {
            this.schemaUrl = schemaUrl;
            return this;
        }

        @Override
        public TracerBuilder setInstrumentationVersion(String instrumentationVersion) {
            this.instrumentationVersion = instrumentationVersion;
            return this;
        }

        @Override
        public Tracer build() {
            return new ReconfigurableTracerProviderTest.TracerMock(instrumentationScopeName, tracerProviderId, schemaUrl, instrumentationVersion);
        }
    }
}