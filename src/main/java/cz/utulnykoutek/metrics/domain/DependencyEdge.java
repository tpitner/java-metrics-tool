// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

public final class DependencyEdge {
    public final String fromTypeId;
    public final SymbolRef to;
    public final DependencyUsage usage;
    public final Origin origin;
    public final boolean resolved;

    public DependencyEdge(String fromTypeId, SymbolRef to, DependencyUsage usage) {
        this.fromTypeId = fromTypeId;
        this.to = to;
        this.usage = usage;
        this.origin = to == null ? Origin.UNRESOLVED : to.origin;
        this.resolved = to != null && to.resolved;
    }
}
