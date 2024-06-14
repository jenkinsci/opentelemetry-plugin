/*
 * Copyright The Original Author or Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.jenkins.plugins.opentelemetry.backend.grafana;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;

public class LokiTenantHeader extends BasicHeader implements Header {
    public LokiTenantHeader(String tenantId) {
        super("X-Scope-OrgID", tenantId);
    }
}
