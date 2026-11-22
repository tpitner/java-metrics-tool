// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

public final class UnresolvedSample {
    public final String category;
    public final String ownerId;
    public final String display;
    public final String usage;
    public final String reasonHint;

    public UnresolvedSample(String category, String ownerId, String display, String usage, String reasonHint) {
        this.category = category;
        this.ownerId = ownerId;
        this.display = display;
        this.usage = usage;
        this.reasonHint = reasonHint;
    }
}
