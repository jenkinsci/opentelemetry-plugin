/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry;

import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractOtelComponent implements OtelComponent {

    private final static Logger logger = Logger.getLogger(AbstractOtelComponent.class.getName());

    private final OtelComponent.State state = new OtelComponent.State();

    protected void registerInstrument(ObservableLongCounter instrument) {
        state.registerInstrument(instrument);
    }

    protected void registerInstrument(ObservableLongGauge instrument) {
        state.registerInstrument(instrument);
    }

    protected void registerInstrument(ObservableLongUpDownCounter instrument) {
        state.registerInstrument(instrument);
    }

    protected void registerInstrument(ObservableDoubleGauge instrument) {
        state.registerInstrument(instrument);
    }

    @Override
    public void beforeSdkShutdown() {
        state.closeInstruments();
    }
}
