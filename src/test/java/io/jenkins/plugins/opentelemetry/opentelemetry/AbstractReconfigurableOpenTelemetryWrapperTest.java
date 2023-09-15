/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.api.metrics.*;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.Test;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class AbstractReconfigurableOpenTelemetryWrapperTest {


    @Test
    public void test() {
        AbstractReconfigurableOpenTelemetryWrapper abstractReconfigurableOpenTelemetryWrapper =  new AbstractReconfigurableOpenTelemetryWrapper() {
            @Override
            protected void postOpenTelemetrySdkConfiguration() {

            }
        };

        abstractReconfigurableOpenTelemetryWrapper.initialize(Collections.emptyMap(), Resource.empty());

        testMeterProvider(abstractReconfigurableOpenTelemetryWrapper.getMeterProvider(), abstractReconfigurableOpenTelemetryWrapper);
        testMeter(abstractReconfigurableOpenTelemetryWrapper.getMeter("test"), abstractReconfigurableOpenTelemetryWrapper);

        Meter meter = abstractReconfigurableOpenTelemetryWrapper.meterBuilder("test-meter").setSchemaUrl("https://example.com").setInstrumentationVersion("123").build();
        testMeter(meter, abstractReconfigurableOpenTelemetryWrapper);

        System.out.println("number of tests: " + abstractReconfigurableOpenTelemetryWrapper.closeables.size());

    }

    private static void testMeterProvider(MeterProvider meterProvider, AbstractReconfigurableOpenTelemetryWrapper abstractReconfigurableOpenTelemetryWrapper) {
        assertThat(meterProvider, instanceOf(AbstractReconfigurableOpenTelemetryWrapper.ClosingMeterProvider.class));


        MeterBuilder meterBuilder = meterProvider.meterBuilder("test");

        assertThat(meterBuilder, instanceOf(AbstractReconfigurableOpenTelemetryWrapper.ClosingMeterBuilder.class));

        testMeter(meterBuilder.setInstrumentationVersion("123").setSchemaUrl("https://example.com").build(), abstractReconfigurableOpenTelemetryWrapper);

        testMeter(meterProvider.get("test"), abstractReconfigurableOpenTelemetryWrapper);
    }

    private static void testMeter(Meter meter, AbstractReconfigurableOpenTelemetryWrapper abstractReconfigurableOpenTelemetryWrapper) {
        assertThat(meter, instanceOf(AbstractReconfigurableOpenTelemetryWrapper.ClosingMeter.class));

        int before = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        ObservableLongCounter observableLongCounter = meter.counterBuilder("test-counter").setDescription("desc").setUnit("s").buildWithCallback(om -> om.record(1L));
        int after = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        assertThat(after, is(before + 1));

        before = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        meter.gaugeBuilder("test-double-gauge").setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1.0));
        after = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        assertThat(after, is(before + 1));

        before = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        meter.gaugeBuilder("test-long-gauge").ofLongs().setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1L));
        after = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        assertThat(after, is(before + 1));

        before = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        meter.upDownCounterBuilder("test-up-down-counter").setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1L));
        after = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        assertThat(after, is(before + 1));

        before = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        meter.upDownCounterBuilder("test-double-up-down-counter").ofDoubles().setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1.0));
        after = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        assertThat(after, is(before + 1));

        before = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        final ObservableDoubleMeasurement observableDoubleMeasurement = meter.gaugeBuilder("another-gauge").setUnit("1").setDescription("desc").buildObserver();
        after = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        assertThat(after, is(before));
        meter.batchCallback(() -> observableDoubleMeasurement.record(1), observableDoubleMeasurement);
        after = abstractReconfigurableOpenTelemetryWrapper.closeables.size();
        assertThat(after, is(before+1));
    }

}