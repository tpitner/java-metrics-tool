// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.Origin;
import cz.utulnykoutek.metrics.domain.PackageCompound;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable package-level projection of type dependency edges. */
public final class PackageDependencyGraph {
    public static final String DEFAULT_PACKAGE = "<default>";

    private final List<PackageCompound> packages;
    private final List<PackageDependencyEdge> edges;
    private final Map<PackageCompound, Integer> unresolvedOutgoingCounts;

    PackageDependencyGraph(
            List<PackageCompound> packages,
            List<PackageDependencyEdge> edges,
            Map<PackageCompound, Integer> unresolvedOutgoingCounts) {
        this.packages = List.copyOf(packages);
        this.edges = List.copyOf(edges);
        this.unresolvedOutgoingCounts = Map.copyOf(unresolvedOutgoingCounts);
    }

    public List<PackageCompound> packages() {
        return packages;
    }

    public List<PackageDependencyEdge> edges() {
        return edges;
    }

    public List<PackageDependencyEdge> outgoing(PackageCompound source) {
        Objects.requireNonNull(source, "source");
        return edges.stream().filter(edge -> edge.source() == source).toList();
    }

    public List<PackageDependencyEdge> outgoing(PackageCompound source, Origin origin) {
        Objects.requireNonNull(origin, "origin");
        return outgoing(source).stream().filter(edge -> edge.origin() == origin).toList();
    }

    public List<PackageDependencyEdge> incomingInternal(PackageCompound target) {
        Objects.requireNonNull(target, "target");
        return edges.stream().filter(edge -> edge.internalTarget() == target).toList();
    }

    public int unresolvedOutgoingCount(PackageCompound source) {
        Objects.requireNonNull(source, "source");
        return unresolvedOutgoingCounts.getOrDefault(source, 0);
    }
}
