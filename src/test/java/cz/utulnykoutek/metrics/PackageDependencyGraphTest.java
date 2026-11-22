// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.domain.*;
import cz.utulnykoutek.metrics.metrics.PackageDependencyEdge;
import cz.utulnykoutek.metrics.metrics.PackageDependencyGraph;
import cz.utulnykoutek.metrics.metrics.PackageDependencyGraphBuilder;
import cz.utulnykoutek.metrics.parser.JdtParser;
import cz.utulnykoutek.metrics.parser.PackageMerger;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PackageDependencyGraphTest {
    @Test
    void deduplicatesClassEdgesAndClassifiesTargets() {
        PackageCompound alpha = pkg("sample.alpha");
        PackageCompound beta = pkg("sample.beta");
        TypeCompound alphaOne = type(alpha, "AlphaOne");
        TypeCompound alphaTwo = type(alpha, "AlphaTwo");
        TypeCompound betaType = type(beta, "Beta");

        add(alpha, alphaOne, ref(betaType, Origin.EXTERNAL), DependencyUsage.FIELD);
        add(alpha, alphaOne, ref(betaType, Origin.EXTERNAL), DependencyUsage.METHOD_RETURN);
        add(alpha, alphaTwo, ref(betaType, Origin.EXTERNAL), DependencyUsage.METHOD_PARAM);
        add(alpha, alphaOne, ref(alphaOne, Origin.INTERNAL), DependencyUsage.FIELD);
        add(beta, betaType, ref(alphaOne, Origin.EXTERNAL), DependencyUsage.EXTENDS);
        add(alpha, alphaOne, new SymbolRef("jdk:string", null, "java.lang.String",
                "java.lang", "String", Origin.JDK, true, TypeKindHint.CLASS),
                DependencyUsage.METHOD_RETURN);
        add(alpha, alphaOne, new SymbolRef("ext:widget", null, "vendor.api.Widget",
                "vendor.api", "Widget", Origin.EXTERNAL, true, TypeKindHint.CLASS),
                DependencyUsage.FIELD);
        add(alpha, alphaOne, new SymbolRef("ext:default", null, "Legacy",
                "", "Legacy", Origin.EXTERNAL, true, TypeKindHint.CLASS),
                DependencyUsage.FIELD);
        add(alpha, alphaOne, SymbolRef.unresolved("missing", "Missing"),
                DependencyUsage.METHOD_PARAM);
        add(alpha, alphaOne, new SymbolRef("primitive:int", null, "int", "int",
                Origin.JDK, true, TypeKindHint.PRIMITIVE), DependencyUsage.FIELD);

        PackageDependencyGraph graph = PackageDependencyGraphBuilder.build(List.of(beta, alpha));

        assertEquals(List.of(alpha, beta), graph.packages());
        assertEquals(5, graph.edges().size());
        PackageDependencyEdge alphaToBeta = graph.outgoing(alpha, Origin.INTERNAL).getFirst();
        assertSame(beta, alphaToBeta.internalTarget());
        assertEquals(2, alphaToBeta.typeDependencyCount());
        assertEquals(Set.of(DependencyUsage.FIELD, DependencyUsage.METHOD_RETURN,
                DependencyUsage.METHOD_PARAM), alphaToBeta.usages());
        assertEquals(1, graph.incomingInternal(beta).size());
        assertEquals(1, graph.incomingInternal(alpha).size());
        assertEquals(1, graph.outgoing(alpha, Origin.JDK).size());
        assertEquals(Set.of("vendor.api", PackageDependencyGraph.DEFAULT_PACKAGE),
                graph.outgoing(alpha, Origin.EXTERNAL).stream()
                        .map(PackageDependencyEdge::targetPackageName)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, graph.unresolvedOutgoingCount(alpha));
        assertThrows(UnsupportedOperationException.class, () -> graph.edges().clear());
        assertThrows(UnsupportedOperationException.class, () -> alphaToBeta.usages().clear());
    }

    @Test
    void projectsTheExistingCouplingCorpusOntoInternalPackages() throws Exception {
        Path sourceRoot = Path.of("validation/cases/coupling/src/main/java");
        List<Path> files;
        try (var paths = Files.walk(sourceRoot)) {
            files = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        JdtParser.Config config = new JdtParser.Config();
        config.sourcepath = List.of(sourceRoot);
        config.release = "17";
        JdtParser parser = new JdtParser(config);
        List<PackageCompound> parsed = new ArrayList<>();
        for (Path file : files) parsed.add((PackageCompound) parser.parse(file));
        List<PackageCompound> packages = PackageMerger.merge(parsed);

        PackageDependencyGraph graph = PackageDependencyGraphBuilder.build(packages);
        PackageCompound api = packageNamed(packages, "validation.coupling.api");
        PackageCompound impl = packageNamed(packages, "validation.coupling.impl");
        PackageCompound client = packageNamed(packages, "validation.coupling.client");

        assertEquals(Set.of(impl, client), graph.incomingInternal(api).stream()
                .map(PackageDependencyEdge::source)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of(api), graph.outgoing(impl, Origin.INTERNAL).stream()
                .map(PackageDependencyEdge::internalTarget).toList());
        assertEquals(List.of(api), graph.outgoing(client, Origin.INTERNAL).stream()
                .map(PackageDependencyEdge::internalTarget).toList());
    }

    private static PackageCompound pkg(String name) {
        return new PackageCompound("pkg:" + name, "p:" + name, name, name, true);
    }

    private static TypeCompound type(PackageCompound pkg, String name) {
        String qualified = pkg.getQualifiedName() + "." + name;
        TypeCompound type = new TypeCompound("type:" + qualified, "symbol:" + qualified,
                CompoundKind.CLASS, name, qualified, pkg, EnumSet.noneOf(Modifier.class),
                NestingKind.TOP_LEVEL, true, null);
        pkg.addChild(type);
        return type;
    }

    private static SymbolRef ref(TypeCompound type, Origin origin) {
        return new SymbolRef(type.symbolId, type.bindingKey, type.getQualifiedName(),
                ((PackageCompound) type.getParent()).getQualifiedName(), type.getName(), origin,
                true, TypeKindHint.CLASS);
    }

    private static void add(PackageCompound pkg, TypeCompound source,
                            SymbolRef target, DependencyUsage usage) {
        pkg.dependencyEdges.add(new cz.utulnykoutek.metrics.domain.DependencyEdge(
                source.symbolId, target, usage));
    }

    private static PackageCompound packageNamed(List<PackageCompound> packages, String name) {
        return packages.stream().filter(pkg -> pkg.getQualifiedName().equals(name))
                .findFirst().orElseThrow();
    }
}
