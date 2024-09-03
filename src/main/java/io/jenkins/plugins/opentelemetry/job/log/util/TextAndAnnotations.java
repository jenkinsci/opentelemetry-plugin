/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.job.log.util;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import net.sf.json.JSONArray;

public class TextAndAnnotations {
    final String text;
    @CheckForNull
    final JSONArray annotations;

    public TextAndAnnotations(String text, @Nullable JSONArray annotations) {
        this.text = text;
        this.annotations = annotations;
    }

    public String getText() {
        return text;
    }

    @CheckForNull
    public JSONArray getAnnotations() {
        return annotations;
    }
}
