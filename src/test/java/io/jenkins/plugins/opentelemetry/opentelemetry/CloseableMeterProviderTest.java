/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.opentelemetry;

import io.opentelemetry.api.metrics.*;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class CloseableMeterProviderTest {


    @Test
    public void test() {
        CloseableMeterProvider meterProvider = new CloseableMeterProvider(MeterProvider.noop());
        testMeterMetricsWithCallback(meterProvider.get("test-meter-1"), meterProvider);

        testMeterMetricsWithCallback(meterProvider.meterBuilder("test-meter").setSchemaUrl("https://example.com").setInstrumentationVersion("123").build(), meterProvider);

        System.out.println("number of closeable meters: " + meterProvider.closeables.size());

        meterProvider.close();

    }

    private static void testMeterMetricsWithCallback(Meter meter, CloseableMeterProvider closeableMeterProvider) {
        assertThat(meter, instanceOf(CloseableMeterProvider.CloseableMeter.class));

        int before = closeableMeterProvider.closeables.size();
        ObservableLongCounter observableLongCounter = meter.counterBuilder("test-counter").setDescription("desc").setUnit("s").buildWithCallback(om -> om.record(1L));
        int after = closeableMeterProvider.closeables.size();
        assertThat(after, is(before + 1));

        before = closeableMeterProvider.closeables.size();
        meter.gaugeBuilder("test-double-gauge").setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1.0));
        after = closeableMeterProvider.closeables.size();
        assertThat(after, is(before + 1));

        before = closeableMeterProvider.closeables.size();
        meter.gaugeBuilder("test-long-gauge").ofLongs().setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1L));
        after = closeableMeterProvider.closeables.size();
        assertThat(after, is(before + 1));

        before = closeableMeterProvider.closeables.size();
        meter.upDownCounterBuilder("test-up-down-counter").setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1L));
        after = closeableMeterProvider.closeables.size();
        assertThat(after, is(before + 1));

        before = closeableMeterProvider.closeables.size();
        meter.upDownCounterBuilder("test-double-up-down-counter").ofDoubles().setDescription("desc").setUnit("ms").buildWithCallback(om -> om.record(1.0));
        after = closeableMeterProvider.closeables.size();
        assertThat(after, is(before + 1));

        before = closeableMeterProvider.closeables.size();
        final ObservableDoubleMeasurement observableDoubleMeasurement = meter.gaugeBuilder("another-gauge").setUnit("1").setDescription("desc").buildObserver();
        after = closeableMeterProvider.closeables.size();
        assertThat(after, is(before));
        meter.batchCallback(() -> observableDoubleMeasurement.record(1), observableDoubleMeasurement);
        after = closeableMeterProvider.closeables.size();
        assertThat(after, is(before + 1));
    }

}