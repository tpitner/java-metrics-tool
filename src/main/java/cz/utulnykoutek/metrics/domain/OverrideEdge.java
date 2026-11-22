// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

public final class OverrideEdge {
    public final String overridingMemberId;
    public final String overriddenMemberId;
    public final SymbolRef overriddenDeclaringType;
    public final boolean resolved;
    public final String kind;

    public OverrideEdge(String overridingMemberId, String overriddenMemberId,
                        SymbolRef overriddenDeclaringType, boolean resolved, String kind) {
        this.overridingMemberId = overridingMemberId;
        this.overriddenMemberId = overriddenMemberId;
        this.overriddenDeclaringType = overriddenDeclaringType;
        this.resolved = resolved;
        this.kind = kind;
    }
}
