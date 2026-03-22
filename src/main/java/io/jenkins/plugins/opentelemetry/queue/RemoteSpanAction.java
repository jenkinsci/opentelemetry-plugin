package io.jenkins.plugins.opentelemetry.queue;

import hudson.model.Action;
import java.util.Map;

public class RemoteSpanAction implements Action {
    private final String traceId;
    private final String spanId;
    private final byte traceFlagsAsByte;
    private final Map<String, String> traceStateMap;

    /**
     * Creates a remote span action with W3C trace context fields.
     *
     * @param traceId trace ID
     * @param spanId span ID
     * @param traceFlagsAsByte trace flags byte
     * @param traceStateMap trace state key-value map
     */
    public RemoteSpanAction(String traceId, String spanId, byte traceFlagsAsByte, Map<String, String> traceStateMap) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.traceFlagsAsByte = traceFlagsAsByte;
        this.traceStateMap = traceStateMap;
    }

    /**
     * Returns trace ID.
     *
     * @return trace ID
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Returns span ID.
     *
     * @return span ID
     */
    public String getSpanId() {
        return spanId;
    }

    /**
     * Returns trace flags as a byte.
     *
     * @return trace flags byte
     */
    public byte getTraceFlagsAsByte() {
        return traceFlagsAsByte;
    }

    /**
     * Returns trace state key-value map.
     *
     * @return trace state map
     */
    public Map<String, String> getTraceStateMap() {
        return traceStateMap;
    }

    /**
     * Returns icon filename; {@code null} for no icon.
     *
     * @return icon filename
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * Returns display name.
     *
     * @return display name
     */
    @Override
    public String getDisplayName() {
        return "RemoteSpan";
    }

    /**
     * Returns URL name; {@code null} for no URL.
     *
     * @return URL name
     */
    @Override
    public String getUrlName() {
        return null;
    }
}
