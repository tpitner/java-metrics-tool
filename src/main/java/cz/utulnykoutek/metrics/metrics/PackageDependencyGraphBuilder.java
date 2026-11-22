// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.*;

import java.util.*;

/** Projects type dependency evidence onto deduplicated directed package edges. */
public final class PackageDependencyGraphBuilder {
    private PackageDependencyGraphBuilder() {}

    public static PackageDependencyGraph build(List<PackageCompound> inputPackages) {
        Objects.requireNonNull(inputPackages, "inputPackages");
        List<PackageCompound> packages = inputPackages.stream()
                .peek(pkg -> Objects.requireNonNull(pkg, "inputPackages contains null"))
                .sorted(Comparator.comparing(PackageCompound::getQualifiedName))
                .toList();

        Map<String, PackageCompound> packageByTypeId = new HashMap<>();
        Map<String, PackageCompound> packageByTypeName = new HashMap<>();
        for (PackageCompound pkg : packages) {
            for (Compound child : pkg.getChildren()) {
                indexTypes(child, pkg, packageByTypeId, packageByTypeName);
            }
        }

        Map<EdgeKey, MutableEdge> aggregated = new LinkedHashMap<>();
        Map<PackageCompound, Integer> unresolved = new IdentityHashMap<>();
        for (PackageCompound source : packages) {
            for (DependencyEdge dependency : source.dependencyEdges) {
                PackageCompound actualSource = packageByTypeId.get(dependency.fromTypeId);
                if (actualSource == null) actualSource = source;
                add(dependency, actualSource, packageByTypeId, packageByTypeName,
                        aggregated, unresolved);
            }
        }

        List<PackageDependencyEdge> edges = aggregated.values().stream()
                .map(MutableEdge::freeze)
                .sorted(Comparator.comparing((PackageDependencyEdge edge) ->
                                edge.source().getQualifiedName())
                        .thenComparing(edge -> edge.origin().name())
                        .thenComparing(PackageDependencyEdge::targetPackageName))
                .toList();
        return new PackageDependencyGraph(packages, edges, unresolved);
    }

    private static void add(
            DependencyEdge dependency,
            PackageCompound source,
            Map<String, PackageCompound> packageByTypeId,
            Map<String, PackageCompound> packageByTypeName,
            Map<EdgeKey, MutableEdge> aggregated,
            Map<PackageCompound, Integer> unresolved) {
        if (dependency.to == null || dependency.to.kindHint == TypeKindHint.PRIMITIVE
                || dependency.to.kindHint == TypeKindHint.TYPE_PARAMETER) return;
        PackageCompound internalTarget = packageByTypeId.get(dependency.to.id);
        if (internalTarget == null && dependency.to.qualifiedName != null) {
            internalTarget = packageByTypeName.get(dependency.to.qualifiedName);
        }
        if (internalTarget != null) {
            if (internalTarget == source) return;
            aggregate(dependency, source, internalTarget.getQualifiedName(), internalTarget,
                    Origin.INTERNAL, aggregated);
            return;
        }
        if (!dependency.resolved || dependency.to.origin == Origin.UNRESOLVED) {
            unresolved.merge(source, 1, Integer::sum);
            return;
        }
        String packageName = dependency.to.packageName;
        if (packageName == null) {
            unresolved.merge(source, 1, Integer::sum);
            return;
        }
        String normalized = normalizePackageName(packageName);
        aggregate(dependency, source, normalized, null, dependency.to.origin, aggregated);
    }

    private static void aggregate(
            DependencyEdge dependency,
            PackageCompound source,
            String targetName,
            PackageCompound internalTarget,
            Origin origin,
            Map<EdgeKey, MutableEdge> aggregated) {
        EdgeKey key = new EdgeKey(source.symbolId, targetName, origin);
        MutableEdge edge = aggregated.computeIfAbsent(key,
                ignored -> new MutableEdge(source, targetName, internalTarget, origin));
        edge.usages.add(dependency.usage);
        String targetId = dependency.to.id == null
                ? dependency.to.qualifiedName : dependency.to.id;
        edge.typeDependencies.add(dependency.fromTypeId + "->" + targetId);
    }

    private static void indexTypes(
            Compound compound,
            PackageCompound pkg,
            Map<String, PackageCompound> byId,
            Map<String, PackageCompound> byName) {
        if (compound instanceof TypeCompound) {
            byId.put(compound.symbolId, pkg);
            if (compound.getQualifiedName() != null) byName.put(compound.getQualifiedName(), pkg);
        }
        compound.getChildren().forEach(child -> indexTypes(child, pkg, byId, byName));
    }

    private static String normalizePackageName(String packageName) {
        return packageName.isBlank() ? PackageDependencyGraph.DEFAULT_PACKAGE : packageName;
    }

    private record EdgeKey(String sourceId, String targetName, Origin origin) {}

    private static final class MutableEdge {
        private final PackageCompound source;
        private final String targetName;
        private final PackageCompound internalTarget;
        private final Origin origin;
        private final EnumSet<DependencyUsage> usages = EnumSet.noneOf(DependencyUsage.class);
        private final Set<String> typeDependencies = new HashSet<>();

        private MutableEdge(PackageCompound source, String targetName,
                            PackageCompound internalTarget, Origin origin) {
            this.source = source;
            this.targetName = targetName;
            this.internalTarget = internalTarget;
            this.origin = origin;
        }

        private PackageDependencyEdge freeze() {
            return new PackageDependencyEdge(source, normalizePackageName(targetName),
                    internalTarget, origin, usages, typeDependencies.size());
        }
    }
}
