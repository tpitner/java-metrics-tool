// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A package compound (root container for top-level types).
 */
public class PackageCompound extends Compound {
    public final List<InheritanceEdge> inheritanceEdges = new ArrayList<>();
    public final List<ImplementationEdge> implementationEdges = new ArrayList<>();
    public final List<OverrideEdge> overrideEdges = new ArrayList<>();
    public final List<DependencyEdge> dependencyEdges = new ArrayList<>();
    public final Set<Path> sourceFiles = new LinkedHashSet<>();
    public final BindingCoverage coverage;

    public PackageCompound(String id, String symbolId, String name, String qualifiedName, boolean resolved) {
        super(id, symbolId, CompoundKind.PACKAGE, name, qualifiedName, null, resolved);
        this.coverage = new BindingCoverage("PACKAGE", this.symbolId);
    }

    /** Compatibility constructor. */
    public PackageCompound(String id, String name, String qualifiedName) {
        this(id, "p:" + qualifiedName, name, qualifiedName, true);
    }
}
