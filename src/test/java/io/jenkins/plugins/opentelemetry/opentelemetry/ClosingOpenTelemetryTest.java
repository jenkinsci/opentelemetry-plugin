/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.*;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class ClosingOpenTelemetryTest {


    @Test
    public void test() {
        ClosingOpenTelemetry closingOpenTelemetry = new ClosingOpenTelemetry(OpenTelemetry.noop());

        testMeterProvider(closingOpenTelemetry.getMeterProvider(), closingOpenTelemetry);
        testMeter(closingOpenTelemetry.getMeter("test"), closingOpenTelemetry);

        Meter meter = closingOpenTelemetry.meterBuilder("test-meter").setSchemaUrl("https://example.com").setInstrumentationVersion("123").build();
        testMeter(meter, closingOpenTelemetry);

        System.out.println("number of tests: " + closingOpenTelemetry.closeables.size());

    }

    private static void testMeterProvider(MeterProvider meterProvider, ClosingOpenTelemetry closingOpenTelemetry) {
        assertThat(meterProvider, instanceOf(ClosingOpenTelemetry.ClosingMeterProvider.class));


        MeterBuilder meterBuilder = meterProvider.meterBuilder("test");

        assertThat(meterBuilder, instanceOf(ClosingOpenTelemetry.ClosingMeterBuilder.class));

        testMeter(meterBuilder.setInstrumentationVersion("123").setSchemaUrl("https://example.com").build(), closingOpenTelemetry);

        testMeter(meterProvider.get("test"), closingOpenTelemetry);
    }

    private static void testMeter(Meter meter, ClosingOpenTelemetry closingOpenTelemetry) {
        assertThat(meter, instanceOf(ClosingOpenTelemetry.ClosingMeter.class));

        int before = closingOpenTelemetry.closeables.size();
        ObservableLongCounter observableLongCounter = meter.counterBuilder("test-counter").setDescription("desc").setUnit("s").buildWithCallback(om -> om.record(1L));
        int after = closingOpenTelemetry.closeables.size();
        assertThat(after, is(before + 1));

        before = closingOpenTelemetry.closeables.size();
        meter.gaugeBuilder("test-double-gauge").setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1.0));
        after = closingOpenTelemetry.closeables.size();
        assertThat(after, is(before + 1));

        before = closingOpenTelemetry.closeables.size();
        meter.gaugeBuilder("test-long-gauge").ofLongs().setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1L));
        after = closingOpenTelemetry.closeables.size();
        assertThat(after, is(before + 1));

        before = closingOpenTelemetry.closeables.size();
        meter.upDownCounterBuilder("test-up-down-counter").setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1L));
        after = closingOpenTelemetry.closeables.size();
        assertThat(after, is(before + 1));

        before = closingOpenTelemetry.closeables.size();
        meter.upDownCounterBuilder("test-double-up-down-counter").ofDoubles().setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1.0));
        after = closingOpenTelemetry.closeables.size();
        assertThat(after, is(before + 1));

        before = closingOpenTelemetry.closeables.size();
        final ObservableDoubleMeasurement observableDoubleMeasurement = meter.gaugeBuilder("another-gauge").setUnit("1").setDescription("desc").buildObserver();
        after = closingOpenTelemetry.closeables.size();
        assertThat(after, is(before));
        meter.batchCallback(() -> observableDoubleMeasurement.record(1), observableDoubleMeasurement);
        after = closingOpenTelemetry.closeables.size();
        assertThat(after, is(before+1));
    }

}