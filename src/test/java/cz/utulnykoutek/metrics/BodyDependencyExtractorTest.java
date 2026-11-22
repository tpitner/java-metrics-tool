// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.domain.DependencyEdge;
import cz.utulnykoutek.metrics.domain.DependencyUsage;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.parser.JdtParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyDependencyExtractorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void extractsSupportedBodyUsagesAndKeepsNestedBodiesSeparate() throws Exception {
        TypeCompound outer = parse("BodyDeps.java", """
                package sample;

                public class BodyDeps {
                    Object value;

                    void work() {
                        java.time.Instant instant = java.time.Instant.now();
                        java.util.List<String> names = new java.util.ArrayList<String>();
                        java.util.Collections.emptyList();
                        System.out.println(instant);
                        java.io.Serializable serializable = (java.io.Serializable) value;
                        boolean compatible = value instanceof java.io.Serializable;
                        Class<?> type = java.nio.file.Path.class;
                        java.util.function.Function<String, String> trim = String::trim;
                        java.util.function.Supplier<String> supplier = () -> value.toString();
                        new java.io.File("x");
                        try {
                            throw new java.io.IOException();
                        } catch (java.io.IOException exception) {
                            exception.getMessage();
                        }
                        class Local {
                            void nested() { java.net.URI.create("https://example.invalid"); }
                        }
                        Runnable anonymous = new Runnable() {
                            public void run() { new java.math.BigDecimal("1"); }
                        };
                    }
                }
                """, true);

        assertUsage(outer, DependencyUsage.LOCAL_VARIABLE, "java.time.Instant");
        assertUsage(outer, DependencyUsage.LOCAL_VARIABLE, "java.util.List");
        assertUsage(outer, DependencyUsage.LOCAL_VARIABLE, "java.lang.String");
        assertUsage(outer, DependencyUsage.INSTANTIATION, "java.util.ArrayList");
        assertUsage(outer, DependencyUsage.CONSTRUCTOR_CALL, "java.io.File");
        assertUsage(outer, DependencyUsage.METHOD_CALL, "java.util.Collections");
        assertUsage(outer, DependencyUsage.FIELD_ACCESS, "java.lang.System");
        assertUsage(outer, DependencyUsage.CAST, "java.io.Serializable");
        assertUsage(outer, DependencyUsage.INSTANCEOF, "java.io.Serializable");
        assertUsage(outer, DependencyUsage.CLASS_LITERAL, "java.nio.file.Path");
        assertUsage(outer, DependencyUsage.METHOD_REFERENCE, "java.lang.String");
        assertUsage(outer, DependencyUsage.CATCH, "java.io.IOException");
        assertUsage(outer, DependencyUsage.LAMBDA_TARGET, "java.util.function.Supplier");

        Set<String> outerTargets = outer.dependencyEdges.stream()
                .map(edge -> edge.to.qualifiedName)
                .collect(java.util.stream.Collectors.toSet());
        assertFalse(outerTargets.contains("java.net.URI"), "local type body leaked into owner");
        assertFalse(outerTargets.contains("java.math.BigDecimal"),
                "anonymous type body leaked into owner");
        assertTrue(outer.getChildren().stream().map(TypeCompound.class::cast)
                .flatMap(type -> type.dependencyEdges.stream())
                .map(edge -> edge.to.qualifiedName)
                .anyMatch(target -> "java.net.URI".equals(target)
                        || "java.math.BigDecimal".equals(target)));
    }

    @Test
    void recordsUnresolvedCallsAndExplicitFieldAccesses() throws Exception {
        TypeCompound type = parse("UnresolvedBody.java", """
                package sample;

                public class UnresolvedBody {
                    void work() {
                        unknown();
                        System.out.println(this.missing);
                    }
                }
                """, false);

        assertEquals(2, unresolved(type, DependencyUsage.METHOD_CALL),
                "both unknown() and println() lack bindings when resolution is disabled");
        assertEquals(1, unresolved(type, DependencyUsage.FIELD_ACCESS));
    }

    private TypeCompound parse(String fileName, String source, boolean resolveBindings) throws Exception {
        Path packageDirectory = temporaryDirectory.resolve(fileName.replace(".java", ""))
                .resolve("sample");
        Files.createDirectories(packageDirectory);
        Path sourceFile = packageDirectory.resolve(fileName);
        Files.writeString(sourceFile, source);
        JdtParser.Config config = new JdtParser.Config();
        config.sourcepath = List.of(packageDirectory.getParent());
        config.release = "17";
        config.resolveBindings = resolveBindings;
        PackageCompound pkg = (PackageCompound) new JdtParser(config).parse(sourceFile);
        return (TypeCompound) pkg.getChildren().getFirst();
    }

    private static void assertUsage(TypeCompound type, DependencyUsage usage, String target) {
        assertTrue(type.dependencyEdges.stream().anyMatch(edge -> edge.usage == usage
                        && target.equals(edge.to.qualifiedName)),
                () -> "Missing " + usage + " dependency on " + target);
    }

    private static long unresolved(TypeCompound type, DependencyUsage usage) {
        return type.dependencyEdges.stream().filter(edge -> edge.usage == usage && !edge.resolved).count();
    }
}
