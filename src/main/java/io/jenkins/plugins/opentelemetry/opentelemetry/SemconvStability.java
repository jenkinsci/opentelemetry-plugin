package io.jenkins.plugins.opentelemetry.opentelemetry;

import hudson.Extension;
import io.jenkins.plugins.opentelemetry.api.OpenTelemetryLifecycleListener;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import jenkins.YesNoMaybe;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension(dynamicLoadable = YesNoMaybe.MAYBE, optional = true)
public class SemconvStability implements OpenTelemetryLifecycleListener {

    private static final Logger logger = Logger.getLogger(SemconvStability.class.getName());

    private final AtomicInteger configurationCounter = new AtomicInteger(0);

    private boolean emitOldCicdSemconv = true;
    private boolean emitStableCicdSemconv = false;

    public boolean emitOldCicdSemconv() {
        return emitOldCicdSemconv;
    }

    public boolean emitStableCicdSemconv() {
        return emitStableCicdSemconv;
    }

    @Override
    public void afterConfiguration(ConfigProperties configProperties) {
        boolean oldCicd = true;
        boolean stableCicd = false;

        String value = configProperties.getString("otel.semconv-stability.opt-in");
        if (value != null) {
            Set<String> values = new HashSet<>(Arrays.asList(value.split(",")));
            if (values.contains("cicd")) {
                oldCicd = false;
                stableCicd = true;
            }
            // no else -- technically it's possible to set "cicd,cicd/dup", in which case we
            // should emit both sets of attributes
            if (values.contains("cicd/dup")) {
                oldCicd = true;
                stableCicd = true;
            }
        }
        if (configurationCounter.get() > 0 && (emitOldCicdSemconv != oldCicd || emitStableCicdSemconv != stableCicd)) {
            logger.log(Level.INFO, "SemconvStability: configuration changes from " +
                "emitOldCicdSemconv=" + emitOldCicdSemconv + " to " + oldCicd +
                ", emitStableCicdSemconv=" + emitStableCicdSemconv + " to " + stableCicd + " may not support hot reload and may require restart");
        }
        emitOldCicdSemconv = oldCicd;
        emitStableCicdSemconv = stableCicd;
        configurationCounter.incrementAndGet();
        logger.log(Level.FINE, () -> "SemconvStability: emitOldCicdSemconv=" + emitOldCicdSemconv + ", emitStableCicdSemconv=" + emitStableCicdSemconv);
    }

    @Override
    public int ordinal() {
        return Integer.MIN_VALUE;
    }
}
