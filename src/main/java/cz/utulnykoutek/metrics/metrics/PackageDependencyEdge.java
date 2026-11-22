// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.DependencyUsage;
import cz.utulnykoutek.metrics.domain.Origin;
import cz.utulnykoutek.metrics.domain.PackageCompound;

import java.util.Objects;
import java.util.Set;

/** One deduplicated directed package dependency with auditable source evidence. */
public record PackageDependencyEdge(
        PackageCompound source,
        String targetPackageName,
        PackageCompound internalTarget,
        Origin origin,
        Set<DependencyUsage> usages,
        int typeDependencyCount) {

    public PackageDependencyEdge {
        Objects.requireNonNull(source, "source");
        if (targetPackageName == null || targetPackageName.isBlank()) {
            throw new IllegalArgumentException("Target package name must not be blank");
        }
        Objects.requireNonNull(origin, "origin");
        usages = Set.copyOf(Objects.requireNonNull(usages, "usages"));
        if (usages.isEmpty()) throw new IllegalArgumentException("Package edge must have a usage");
        if (typeDependencyCount <= 0) {
            throw new IllegalArgumentException("typeDependencyCount must be positive");
        }
        if ((origin == Origin.INTERNAL) != (internalTarget != null)) {
            throw new IllegalArgumentException("Only internal edges have an internal target");
        }
        if (origin == Origin.UNRESOLVED) {
            throw new IllegalArgumentException("Unresolved targets are graph diagnostics, not edges");
        }
    }
}
