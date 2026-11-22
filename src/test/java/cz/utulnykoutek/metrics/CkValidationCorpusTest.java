// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.config.CkProviderConfig;
import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.CompoundKind;
import cz.utulnykoutek.metrics.domain.Constructor;
import cz.utulnykoutek.metrics.domain.Member;
import cz.utulnykoutek.metrics.domain.Method;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.domain.Parameter;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.ck.CkMetricsProvider;
import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementKey;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.metrics.measurement.UnavailableResult;
import cz.utulnykoutek.metrics.metrics.measurement.ValueRelation;
import cz.utulnykoutek.metrics.parser.JdtParser;
import cz.utulnykoutek.metrics.parser.PackageMerger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in validation of the real pinned CK JAR against the shared corpus. */
class CkValidationCorpusTest {
    private static final Path CORPUS = Path.of("validation");

    @TempDir Path temporaryDirectory;

    @Test
    void runsCkOverSharedCorpusAndMatchesItsOracle() throws Exception {
        String configuredJar = System.getProperty("ck.jar");
        Assumptions.assumeTrue(configuredJar != null && Files.isRegularFile(Path.of(configuredJar)),
                "Set -Dck.jar to run CK validation over the shared corpus");

        Path jar = Path.of(configuredJar).toAbsolutePath().normalize();
        Map<String, List<CSVRecord>> subjects = rowsByCase(csv(CORPUS.resolve("subjects.csv")));
        Map<String, List<CSVRecord>> exclusions = rowsByCase(csv(CORPUS.resolve("ck-exclusions.csv")));
        Map<String, List<CSVRecord>> oracle = rowsByCase(csv(CORPUS.resolve("measurements.csv"))
                .stream().filter(row -> CkMetricsProvider.ID.equals(row.get("provider"))).toList());

        for (CorpusCase corpusCase : cases()) {
            CaseAnalysis analysis = analyze(corpusCase);
            CkProviderConfig config = new CkProviderConfig(
                    true, Optional.of(jar), javaExecutable(), false, 0, true,
                    List.of(".git", "target", "build"), Duration.ofMinutes(2), false);
            ProviderResult result = new CkMetricsProvider(
                    config,
                    corpusCase.project(),
                    temporaryDirectory.resolve(corpusCase.id()),
                    false).measure(analysis.context());

            assertExpectedDiagnostics(corpusCase, result.diagnostics());
            assertRequiredSubjectsWereMeasured(
                    corpusCase, analysis, result,
                    subjects.getOrDefault(corpusCase.id(), List.of()),
                    exclusions.getOrDefault(corpusCase.id(), List.of()));
            for (CSVRecord row : oracle.getOrDefault(corpusCase.id(), List.of())) {
                assertOracleRow(analysis, result, row);
            }
        }
    }

    private static void assertExpectedDiagnostics(CorpusCase corpusCase, List<String> diagnostics) {
        switch (corpusCase.id()) {
            case "nested-types" -> {
                assertFalse(diagnostics.isEmpty(),
                        "CK currently reports local/anonymous types that have no frozen cross-tool identity");
                assertTrue(diagnostics.stream().allMatch(message ->
                                message.contains("validation.nested.Outer$1Local")
                                        || message.contains("validation.nested.Outer$Anonymous1")),
                        () -> "Unexpected nested-types CK diagnostics: " + diagnostics);
            }
            case "initializers-lambdas" -> {
                assertEquals(2, diagnostics.size(),
                        () -> "Unexpected initializer CK diagnostics: " + diagnostics);
                assertTrue(diagnostics.stream().anyMatch(message -> message.contains("#(initializer 1)")),
                        () -> "Missing first initializer diagnostic: " + diagnostics);
                assertTrue(diagnostics.stream().anyMatch(message -> message.contains("#(initializer 2)")),
                        () -> "Missing second initializer diagnostic: " + diagnostics);
            }
            default -> assertTrue(diagnostics.isEmpty(),
                    () -> corpusCase.id() + " CK mapping diagnostics: " + diagnostics);
        }
    }

    private static void assertRequiredSubjectsWereMeasured(
            CorpusCase corpusCase,
            CaseAnalysis analysis,
            ProviderResult result,
            List<CSVRecord> subjects,
            List<CSVRecord> exclusions) {
        Set<SubjectRef> measuredSubjects = result.measurements().all().stream()
                .map(MetricMeasurement::subject)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> excludedSubjects = exclusions.stream()
                .map(row -> row.get("subject"))
                .collect(java.util.stream.Collectors.toSet());
        for (CSVRecord row : subjects) {
            MetricScope scope = scope(row.get("scope"));
            if (scope != MetricScope.TYPE
                    && scope != MetricScope.METHOD
                    && scope != MetricScope.CONSTRUCTOR) {
                continue;
            }
            SubjectRef subject = resolveSubject(analysis, scope, row.get("qualified_name"));
            if (excludedSubjects.contains(row.get("qualified_name"))) {
                assertFalse(measuredSubjects.contains(subject), () ->
                        corpusCase.id() + " CK exclusion is stale; CK now measures " + row.get("qualified_name"));
            } else {
                assertTrue(measuredSubjects.contains(subject), () ->
                        corpusCase.id() + " CK did not map required type " + row.get("qualified_name"));
            }
        }
        for (CSVRecord exclusion : exclusions) {
            assertEquals("type", exclusion.get("scope"), "Only type-level CK exclusions are supported");
            assertTrue(analysis.typesByQualifiedName().containsKey(exclusion.get("subject")),
                    () -> "CK exclusion refers to an unknown subject: " + exclusion.get("subject"));
        }
    }

    private static void assertOracleRow(
            CaseAnalysis analysis,
            ProviderResult result,
            CSVRecord row) {
        MetricScope scope = scope(row.get("scope"));
        SubjectRef subject = resolveSubject(analysis, scope, row.get("subject"));
        MetricMeasurement measurement = result.measurements().find(new MeasurementKey(
                MetricId.parse(row.get("metric")),
                subject,
                result.run().providerRunId())).orElseThrow(() ->
                new AssertionError("Missing CK measurement for oracle row " + row.getRecordNumber()));

        String label = row.get("case_id") + ":" + row.get("metric") + ":" + row.get("subject");
        assertEquals(row.get("status"), measurement.status().name(), label);
        switch (measurement.result()) {
            case NumericResult numeric -> {
                assertFalse(row.get("value").isBlank(), label + " has no oracle value");
                assertEquals(ValueRelation.valueOf(row.get("relation")), numeric.relation(), label);
                BigDecimal expected = new BigDecimal(row.get("value"));
                assertEquals(0, numeric.value().compareTo(expected),
                        () -> label + " expected=" + expected + " actual=" + numeric.value());
            }
            case NotApplicableResult notApplicable ->
                    assertReason(row, Set.of(notApplicable.reason()), label);
            case UnavailableResult unavailable ->
                    assertReason(row, unavailable.reasons(), label);
            default -> throw new AssertionError(label + " has unsupported CK result " + measurement.result());
        }
    }

    private static MetricScope scope(String serializedName) {
        return java.util.Arrays.stream(MetricScope.values())
                .filter(candidate -> candidate.serializedName().equals(serializedName))
                .findFirst().orElseThrow(() -> new AssertionError("Unknown metric scope: " + serializedName));
    }

    private static SubjectRef resolveSubject(
            CaseAnalysis analysis, MetricScope scope, String canonicalName) {
        return switch (scope) {
            case TYPE -> {
                Compound type = analysis.typesByQualifiedName().get(canonicalName);
                assertNotNull(type, "Unknown corpus type subject: " + canonicalName);
                yield new SubjectRef(MetricScope.TYPE, type.symbolId);
            }
            case METHOD, CONSTRUCTOR -> {
                Member member = analysis.membersByCanonicalName().get(canonicalName);
                assertNotNull(member, "Unknown corpus member subject: " + canonicalName);
                MetricScope actual = member instanceof Constructor
                        ? MetricScope.CONSTRUCTOR : MetricScope.METHOD;
                assertEquals(scope, actual, "Wrong scope for corpus member " + canonicalName);
                yield new SubjectRef(scope, member.id);
            }
            default -> throw new AssertionError("Unsupported corpus subject scope: " + scope);
        };
    }

    private static void assertReason(CSVRecord row, Set<ReasonCode> actual, String label) {
        assertFalse(row.get("reason").isBlank(), label + " has no oracle reason");
        Set<ReasonCode> expected = java.util.Arrays.stream(row.get("reason").split("\\|"))
                .map(String::trim).map(ReasonCode::valueOf)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, actual, label);
    }

    private static String javaExecutable() {
        String configuredJava = System.getProperty("ck.java");
        assertTrue(configuredJava != null && !configuredJava.isBlank(),
                "Set -Dck.java=/path/to/jdk-21/bin/java when -Dck.jar is set");
        Path executable = Path.of(configuredJava).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(executable) && Files.isExecutable(executable),
                "-Dck.java must point to an executable file: " + executable);
        return executable.toString();
    }

    private static Map<String, List<CSVRecord>> rowsByCase(List<CSVRecord> rows) {
        Map<String, List<CSVRecord>> result = new LinkedHashMap<>();
        for (CSVRecord row : rows) {
            result.computeIfAbsent(row.get("case_id"), ignored -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private static List<CorpusCase> cases() throws Exception {
        List<CorpusCase> result = new ArrayList<>();
        for (CSVRecord row : csv(CORPUS.resolve("cases.csv"))) {
            Path project = CORPUS.resolve(row.get("project_dir")).toAbsolutePath().normalize();
            Path sourceRoot = CORPUS.resolve(row.get("source_root")).toAbsolutePath().normalize();
            assertTrue(Files.isRegularFile(project.resolve("pom.xml")), "Missing pom.xml for " + row.get("case_id"));
            assertTrue(Files.isDirectory(sourceRoot), "Missing source root for " + row.get("case_id"));
            result.add(new CorpusCase(row.get("case_id"), project, sourceRoot));
        }
        return List.copyOf(result);
    }

    private static CaseAnalysis analyze(CorpusCase corpusCase) throws Exception {
        List<Path> files = sourceFiles(corpusCase.sourceRoot());
        JdtParser.Config config = new JdtParser.Config();
        config.sourcepath = List.of(corpusCase.sourceRoot());
        config.release = "17";
        JdtParser parser = new JdtParser(config);
        List<PackageCompound> parsed = new ArrayList<>();
        for (Path file : files) parsed.add((PackageCompound) parser.parse(file));
        List<PackageCompound> packages = PackageMerger.merge(parsed);
        AnalysisContext context = new AnalysisContext(packages, files, BaselineMetadata.capture(config));
        Map<String, Compound> types = new HashMap<>();
        for (Compound pkg : packages) collectTypes(pkg, types);
        Map<String, Member> members = new HashMap<>();
        for (Compound type : types.values()) {
            for (Member member : type.getMembers()) {
                if (member instanceof Method || member instanceof Constructor) {
                    Member previous = members.put(canonicalName(member), member);
                    if (previous != null) {
                        throw new AssertionError("Duplicate corpus member identity: " + canonicalName(member));
                    }
                }
            }
        }
        return new CaseAnalysis(context, Map.copyOf(types), Map.copyOf(members));
    }

    private static void collectTypes(Compound compound, Map<String, Compound> target) {
        if (compound.getKind() != CompoundKind.PACKAGE && compound.getQualifiedName() != null) {
            target.put(compound.getQualifiedName(), compound);
        }
        compound.getChildren().forEach(child -> collectTypes(child, target));
    }

    private static List<Path> sourceFiles(Path sourceRoot) {
        try (var paths = Files.walk(sourceRoot)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot list corpus sources in " + sourceRoot, e);
        }
    }

    private static String canonicalName(Member member) {
        String name = member instanceof Constructor ? "<init>" : member.name;
        List<Parameter> parameters = member instanceof Constructor constructor
                ? constructor.parameters : ((Method) member).parameters;
        return member.owner.qualifiedName + "#" + name + "(" + parameters.stream()
                .map(CkValidationCorpusTest::canonicalType)
                .collect(java.util.stream.Collectors.joining(",")) + ")";
    }

    private static String canonicalType(Parameter parameter) {
        if (parameter.type == null) return "?";
        String type = parameter.type.qualifiedName == null || parameter.type.qualifiedName.isBlank()
                ? parameter.type.displayName : parameter.type.qualifiedName;
        if (parameter.varargs && !type.endsWith("[]")) type += "[]";
        return type.replace('$', '.').replace(" ", "");
    }

    private static List<CSVRecord> csv(Path file) throws Exception {
        try (var parser = CSVFormat.DEFAULT.withFirstRecordAsHeader()
                .parse(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            return parser.getRecords();
        }
    }

    private record CorpusCase(String id, Path project, Path sourceRoot) {}

    private record CaseAnalysis(
            AnalysisContext context,
            Map<String, Compound> typesByQualifiedName,
            Map<String, Member> membersByCanonicalName) {}
}
