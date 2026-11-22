// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

public final class ImplementationEdge {
    public final String typeId;
    public final SymbolRef interfaceType;
    public final boolean resolved;

    public ImplementationEdge(String typeId, SymbolRef interfaceType) {
        this.typeId = typeId;
        this.interfaceType = interfaceType;
        this.resolved = interfaceType != null && interfaceType.resolved;
    }
}
