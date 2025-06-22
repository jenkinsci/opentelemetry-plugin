package io.jenkins.plugins.opentelemetry.queue;

import hudson.model.Action;
import java.util.Map;

public class RemoteSpanAction implements Action {
    private final String traceId;
    private final String spanId;
    private final byte traceFlagsAsByte;
    private final Map<String, String> traceStateMap;

    public RemoteSpanAction(String traceId, String spanId, byte traceFlagsAsByte, Map<String, String> traceStateMap) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.traceFlagsAsByte = traceFlagsAsByte;
        this.traceStateMap = traceStateMap;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public byte getTraceFlagsAsByte() {
        return traceFlagsAsByte;
    }

    public Map<String, String> getTraceStateMap() {
        return traceStateMap;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "RemoteSpan";
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
