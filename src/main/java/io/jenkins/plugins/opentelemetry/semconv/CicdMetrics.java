package io.jenkins.plugins.opentelemetry.semconv;

import hudson.model.Result;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounterBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.semconv.ErrorAttributes;
import io.opentelemetry.semconv.incubating.CicdIncubatingAttributes;
import java.util.List;

public class CicdMetrics {
    // FIXME WHAT ARE THE SPECS FOR THESE BUCKETS?
    // TODO support configurability of these histogram buckets. Note that the conversion from a string to a list of
    //  doubles will require boilerplate so we are interested in getting user feedback before implementing this.
    static final List<Double> DURATION_SECONDS_BUCKETS =
            List.of(1D, 2D, 4D, 8D, 16D, 32D, 64D, 128D, 256D, 512D, 1024D, 2048D, 4096D, 8192D);

    public static DoubleHistogram newCiCdPipelineRunDurationHistogram(Meter meter) {
        DoubleHistogramBuilder cicdPipelineRunDurationHistogramBuilder = meter.histogramBuilder(
                        "cicd.pipeline.run.duration")
                .setUnit("s")
                .setDescription("Duration of a pipeline run grouped by pipeline, state and result.")
                .setExplicitBucketBoundariesAdvice(DURATION_SECONDS_BUCKETS);
        if (cicdPipelineRunDurationHistogramBuilder
                instanceof ExtendedDoubleHistogramBuilder extendedDoubleHistogramBuilder) {
            extendedDoubleHistogramBuilder.setAttributesAdvice(List.of(
                    CicdIncubatingAttributes.CICD_PIPELINE_NAME,
                    CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE,
                    CicdIncubatingAttributes.CICD_PIPELINE_RESULT,
                    ErrorAttributes.ERROR_TYPE));
        }

        return cicdPipelineRunDurationHistogramBuilder.build();
    }

    public static LongUpDownCounter newCiCdPipelineRunActiveCounter(Meter meter) {
        LongUpDownCounterBuilder cicdPipelineRunActiveCounterBuilder = meter.upDownCounterBuilder(
                        "cicd.pipeline.run.active")
                .setUnit("{run}")
                .setDescription("Number of active pipeline runs grouped by pipeline and state.");
        if (cicdPipelineRunActiveCounterBuilder
                instanceof ExtendedDoubleUpDownCounterBuilder pipelineRunActiveCounterBuilder) {
            pipelineRunActiveCounterBuilder.setAttributesAdvice(List.of(
                    CicdIncubatingAttributes.CICD_PIPELINE_NAME, CicdIncubatingAttributes.CICD_PIPELINE_RUN_STATE));
        }

        return cicdPipelineRunActiveCounterBuilder.build();
    }

    /**
     * FIXME shouldn't it be a gauge rather than an upDownCounter?
     */
    public static ObservableLongMeasurement newCiCdWorkerCounter(Meter meter) {
        LongUpDownCounterBuilder cicdWorkerCountBuilder = meter.upDownCounterBuilder("cicd.worker.count")
                .setUnit("{worker}")
                .setDescription("The number of workers on the CICD system by state.");
        if (cicdWorkerCountBuilder instanceof ExtendedDoubleUpDownCounterBuilder cicdWorkerCountExtendedBuilder) {
            cicdWorkerCountExtendedBuilder.setAttributesAdvice(List.of(CicdIncubatingAttributes.CICD_WORKER_STATE));
        }

        return cicdWorkerCountBuilder.buildObserver();
    }

    public static LongCounter newCiCdPipelineRunErrorsCounter(Meter meter) {
        LongCounterBuilder cicdPipelineRunErrorsBuilder = meter.counterBuilder("cicd.pipeline.run.errors")
                .setUnit("{error}")
                .setDescription("Number of errors in a pipeline run grouped by pipeline and error type.");
        if (cicdPipelineRunErrorsBuilder instanceof ExtendedLongCounterBuilder cicdPipelineRunErrorsExtendedBuilder) {
            cicdPipelineRunErrorsExtendedBuilder.setAttributesAdvice(
                    List.of(CicdIncubatingAttributes.CICD_PIPELINE_NAME, ErrorAttributes.ERROR_TYPE));
        }

        return cicdPipelineRunErrorsBuilder.build();
    }

    public static LongCounter newCiCdSystemErrorsCounter(Meter meter) {
        LongCounterBuilder cicdSystemErrorsBuilder = meter.counterBuilder("cicd.system.errors")
                .setUnit("{error}")
                .setDescription("Number of errors in the CICD system grouped by component and error type.");
        if (cicdSystemErrorsBuilder instanceof ExtendedLongCounterBuilder cicdSystemErrorsExtendedBuilder) {
            cicdSystemErrorsExtendedBuilder.setAttributesAdvice(
                    List.of(CicdIncubatingAttributes.CICD_SYSTEM_COMPONENT, ErrorAttributes.ERROR_TYPE));
        }
        return cicdSystemErrorsBuilder.build();
    }

    /**
     * Convert a Jenkins {@link Result} to a an OpenTelemetry
     * {@link CicdIncubatingAttributes#CICD_PIPELINE_RESULT} according to the
     * <a href="https://opentelemetry.io/docs/specs/semconv/cicd/cicd-metrics/">OpenTelemetry
     * CICD Semantic Conventions</a>
     */
    public static String fromJenkinsResultToOtelCicdPipelineResult(Result result) {
        if (result == null) {
            return "#null#";
        }
        if (result.equals(Result.ABORTED)) {
            return CicdIncubatingAttributes.CicdPipelineResultIncubatingValues.CANCELLATION;
        } else if (result.equals(Result.FAILURE)) {
            return CicdIncubatingAttributes.CicdPipelineResultIncubatingValues.FAILURE;
        } else if (result.equals(Result.NOT_BUILT)) {
            return "not_built";
        } else if (result.equals(Result.SUCCESS)) {
            return CicdIncubatingAttributes.CicdPipelineResultIncubatingValues.SUCCESS;
        } else if (result.equals(Result.UNSTABLE)) {
            return "unstable";
        }
        return result.toString().toLowerCase();
    }
}
