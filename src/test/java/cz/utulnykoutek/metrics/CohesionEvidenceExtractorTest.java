// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.domain.CohesionMethodExclusion;
import cz.utulnykoutek.metrics.domain.MethodCohesionEvidence;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CohesionEvidenceExtractorTest {
    @TempDir Path temporaryDirectory;

    @Test
    void extractsEligibilityFieldsCallsLambdasAndScopeBoundaries() throws Exception {
        TypeCompound type = parse("CohesionSubject.java", """
                package sample;

                public abstract class CohesionSubject {
                    int shared;
                    int lambdaState;
                    int nestedOnly;
                    static int staticState;

                    int getShared() {
                        return shared;
                    }

                    void setShared(int shared) {
                        this.shared = shared;
                    }

                    int adjustedShared() {
                        return shared + 1;
                    }

                    void updateShared(int shared) {
                        this.shared = shared;
                        lambdaState++;
                    }

                    void first() {
                        shared++;
                        second(1);
                        Runnable action = () -> lambdaState++;
                        action.run();
                        class Local {
                            void touch() { nestedOnly++; }
                        }
                        Object anonymous = new Object() {
                            void touch() { nestedOnly++; }
                        };
                    }

                    void second(int value) {
                        shared += value;
                    }

                    void second(String value) {
                        lambdaState += value.length();
                    }

                    void recursive() {
                        recursive();
                    }

                    void isolated() {}

                    static void utility() {
                        staticState++;
                    }

                    abstract void missing();
                }
                """, true);

        assertNotNull(type.cohesionEvidence);
        assertEquals(Set.of("lambdaState", "nestedOnly", "shared"),
                type.cohesionEvidence.declaredInstanceFields().stream()
                        .map(field -> field.name).collect(java.util.stream.Collectors.toSet()));

        MethodCohesionEvidence getter = evidence(type, "getShared", 0);
        MethodCohesionEvidence setter = evidence(type, "setShared", 1);
        assertEquals(Set.of(CohesionMethodExclusion.TRIVIAL_ACCESSOR), getter.exclusions(), getter.toString());
        assertEquals(Set.of(CohesionMethodExclusion.TRIVIAL_ACCESSOR), setter.exclusions(), setter.toString());
        assertEquals(Set.of("shared"), fieldNames(getter));
        assertEquals(Set.of("shared"), fieldNames(setter));
        assertTrue(evidence(type, "adjustedShared", 0).eligible(),
                "computed getter-like methods are not trivial accessors");
        assertTrue(evidence(type, "updateShared", 1).eligible(),
                "multi-statement setters are not trivial accessors");

        MethodCohesionEvidence first = evidence(type, "first", 0);
        assertTrue(first.eligible());
        assertTrue(first.complete());
        assertEquals(Set.of("shared", "lambdaState"), fieldNames(first),
                "lambda access belongs to the enclosing method; local/anonymous bodies do not");
        assertEquals(1, first.calledDeclaredMethods().size());
        var calledOverload = first.calledDeclaredMethods().iterator().next();
        assertEquals("second", calledOverload.name);
        assertEquals("int", calledOverload.parameters.getFirst().type.displayName);
        var overloadEvidence = type.cohesionEvidence.methods().stream()
                .filter(candidate -> candidate.method().name.equals("second"))
                .toList();
        assertEquals(2, overloadEvidence.size());
        assertEquals(Set.of("shared"), fieldNames(overloadEvidence.stream()
                .filter(candidate -> candidate.method().parameters.getFirst().type.displayName.equals("int"))
                .findFirst().orElseThrow()));
        assertEquals(Set.of("lambdaState"), fieldNames(overloadEvidence.stream()
                .filter(candidate -> candidate.method().parameters.getFirst().type.qualifiedName
                        .equals("java.lang.String"))
                .findFirst().orElseThrow()));

        MethodCohesionEvidence recursive = evidence(type, "recursive", 0);
        assertEquals(Set.of(recursive.method()), recursive.calledDeclaredMethods());
        assertTrue(evidence(type, "isolated", 0).eligible());

        MethodCohesionEvidence utility = evidence(type, "utility", 0);
        assertEquals(Set.of(CohesionMethodExclusion.STATIC_METHOD), utility.exclusions());
        assertTrue(utility.accessedInstanceFields().isEmpty(),
                "static fields are not instance-field cohesion evidence");
        assertEquals(Set.of(CohesionMethodExclusion.NO_BODY),
                evidence(type, "missing", 0).exclusions());

        assertFalse(fieldNames(evidence(type, "second", 1)).contains("nestedOnly"));
        assertEquals(2, type.getChildren().stream()
                .filter(child -> child instanceof TypeCompound childType
                        && (childType.nesting == cz.utulnykoutek.metrics.domain.NestingKind.LOCAL
                        || childType.nesting == cz.utulnykoutek.metrics.domain.NestingKind.ANONYMOUS))
                .count());
    }

    @Test
    void recordsOnlyPlausibleUnresolvedIntraTypeEvidence() throws Exception {
        TypeCompound type = parse("UnresolvedCohesion.java", """
                package sample;

                class UnresolvedCohesion {
                    int state;

                    void target(int value) {}

                    void source() {
                        state++;
                        target(1);
                        external();
                    }
                }
                """, false);

        MethodCohesionEvidence source = evidence(type, "source", 0);
        assertEquals(1, source.unresolvedFieldAccessCount());
        assertEquals(1, source.unresolvedIntraTypeCallCount(),
                "unknown external() must not be classified as a potential intra-type call");
        assertTrue(source.accessedInstanceFields().isEmpty());
        assertTrue(source.calledDeclaredMethods().isEmpty());
        assertFalse(source.complete());
    }

    private TypeCompound parse(String fileName, String source, boolean resolveBindings) throws Exception {
        Path packageDirectory = temporaryDirectory.resolve("sample");
        Files.createDirectories(packageDirectory);
        Path sourceFile = packageDirectory.resolve(fileName);
        Files.writeString(sourceFile, source);
        JdtParser.Config config = new JdtParser.Config();
        config.sourcepath = List.of(temporaryDirectory);
        config.release = "17";
        config.resolveBindings = resolveBindings;
        PackageCompound pkg = (PackageCompound) new JdtParser(config).parse(sourceFile);
        return (TypeCompound) pkg.getChildren().getFirst();
    }

    private static MethodCohesionEvidence evidence(TypeCompound type, String name, int arity) {
        return type.cohesionEvidence.methods().stream()
                .filter(candidate -> candidate.method().name.equals(name)
                        && candidate.method().parameters.size() == arity)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Missing cohesion evidence for " + name + "/" + arity));
    }

    private static Set<String> fieldNames(MethodCohesionEvidence evidence) {
        return evidence.accessedInstanceFields().stream().map(field -> field.name)
                .collect(java.util.stream.Collectors.toSet());
    }
}
