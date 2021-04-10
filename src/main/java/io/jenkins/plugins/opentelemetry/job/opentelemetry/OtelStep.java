package io.jenkins.plugins.opentelemetry.job.opentelemetry;

import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * {@link Step} that enables the span attributes
 */
public abstract class OtelStep extends Step {

    private static final Logger LOGGER = Logger.getLogger(OtelStep.class.getName());
    private Map<String,String> apm = new LinkedHashMap<>();

    @DataBoundSetter
    public void setApm(Map<String,String> apm) {
        this.apm = apm;
    }

    public Map<String,String> getApm() {
        return apm;
    }
}
