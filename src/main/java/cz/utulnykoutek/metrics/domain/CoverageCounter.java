// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

public final class CoverageCounter {
    public int total;
    public int resolved;
    public int unresolved;
    public int partial;

    public void addAttempt(boolean isResolved, boolean isPartial) {
        total++;
        if (isPartial) partial++;
        if (isResolved) resolved++; else unresolved++;
    }
}
