// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

public final class InheritanceEdge {
    public final String subtypeId;
    public final SymbolRef supertype;
    public final boolean resolved;

    public InheritanceEdge(String subtypeId, SymbolRef supertype) {
        this.subtypeId = subtypeId;
        this.supertype = supertype;
        this.resolved = supertype != null && supertype.resolved;
    }
}
