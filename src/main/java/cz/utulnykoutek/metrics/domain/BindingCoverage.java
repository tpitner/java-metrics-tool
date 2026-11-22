// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BindingCoverage {
    public final String scope;
    public final String scopeId;
    public boolean complete;
    public final Map<String, CoverageCounter> counters = new LinkedHashMap<>();
    public final List<UnresolvedSample> unresolvedSamples = new ArrayList<>();

    public BindingCoverage(String scope, String scopeId) {
        this.scope = scope;
        this.scopeId = scopeId;
    }

    public CoverageCounter counter(String name) {
        return counters.computeIfAbsent(name, k -> new CoverageCounter());
    }

    public void recomputeComplete() {
        boolean ok = true;
        for (CoverageCounter c : counters.values()) {
            if (c.unresolved > 0 || c.partial > 0) { ok = false; break; }
        }
        this.complete = ok && !counters.isEmpty();
    }
}
