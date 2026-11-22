// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.Constructor;
import cz.utulnykoutek.metrics.domain.InitializerBlock;
import cz.utulnykoutek.metrics.domain.LambdaExpressionEntity;
import cz.utulnykoutek.metrics.domain.Member;
import cz.utulnykoutek.metrics.domain.Method;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.domain.Parameter;
import cz.utulnykoutek.metrics.domain.NestingKind;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementKey;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.IntervalResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.UnavailableResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.metrics.measurement.ValueRelation;
import cz.utulnykoutek.metrics.parser.JdtParser;
import cz.utulnykoutek.metrics.parser.PackageMerger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Executable checks for the provider-neutral validation corpus. */
class ValidationCorpusTest {
    private static final Path CORPUS = Path.of("validation");

    @TempDir Path temporaryDirectory;

    @Test
    void casesHaveExpectedCompilationStatus() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Validation requires a JDK, not a JRE");

        for (CorpusCase corpusCase : cases()) {
            Path output = temporaryDirectory.resolve(corpusCase.id());
            Files.createDirectories(output);
            List<String> arguments = new ArrayList<>(List.of(
                    "--release", "17", "-d", output.toString()));
            sourceFiles(corpusCase).forEach(path -> arguments.add(path.toString()));
            ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();

            int exitCode = compiler.run(null, null, diagnostics, arguments.toArray(String[]::new));

            assertEquals(corpusCase.expectedBuild(), exitCode == 0,
                    () -> corpusCase.id() + " compilation status differs from manifest:\n"
                            + diagnostics.toString(StandardCharsets.UTF_8));
        }
    }

    @Test
    void allRequiredSubjectsMapToJdtModel() throws Exception {
        Map<String, CaseAnalysis> analyses = analyses();
        for (CSVRecord row : csv(CORPUS.resolve("subjects.csv"))) {
            CaseAnalysis analysis = analyses.get(row.get("case_id"));
            assertNotNull(analysis, "Unknown corpus case in subjects.csv: " + row.get("case_id"));
            boolean mapped = switch (row.get("scope")) {
                case "type" -> analysis.typesByQualifiedName().containsKey(row.get("qualified_name"));
                case "method", "constructor" ->
                        analysis.membersByCanonicalName().containsKey(row.get("qualified_name"));
                case "initializer" ->
                        analysis.initializersByCanonicalName().containsKey(row.get("qualified_name"));
                case "lambda" ->
                        analysis.lambdasByCanonicalName().containsKey(row.get("qualified_name"));
                default -> throw new AssertionError("Unsupported subject scope: " + row.get("scope"));
            };
            assertTrue(mapped, () -> "Subject not mapped in " + row.get("case_id")
                    + ": " + row.get("qualified_name"));
        }

        CaseAnalysis nested = analyses.get("nested-types");
        assertEquals(1, countTypes(nested.context().packages(), NestingKind.LOCAL),
                "Local type must be collected exactly once");
        assertEquals(1, countTypes(nested.context().packages(), NestingKind.ANONYMOUS),
                "Anonymous type must be collected exactly once");
    }

    @Test
    void builtInMeasurementsMatchIndependentOracle() throws Exception {
        Map<String, CaseAnalysis> analyses = analyses();
        Map<String, ProviderResult> results = new HashMap<>();

        for (CSVRecord row : csv(CORPUS.resolve("measurements.csv"))) {
            if (!JavaMetricsProvider.ID.equals(row.get("provider"))) continue;

            String caseId = row.get("case_id");
            CaseAnalysis analysis = analyses.get(caseId);
            assertNotNull(analysis, "Unknown corpus case in measurements.csv: " + caseId);
            ProviderResult provider = results.computeIfAbsent(
                    caseId, ignored -> new JavaMetricsProvider().measure(analysis.context()));
            SubjectRef subject = resolveSubject(analysis, row);

            MetricMeasurement measurement = provider.measurements().find(new MeasurementKey(
                    MetricId.parse(row.get("metric")),
                    subject,
                    provider.run().providerRunId())).orElseThrow(() ->
                    new AssertionError("Missing measurement for oracle row " + row.getRecordNumber()));

            assertEquals(row.get("status"), measurement.status().name(), oracleLabel(row));
            switch (measurement.result()) {
                case NumericResult numeric -> {
                    assertEquals(ValueRelation.valueOf(row.get("relation")),
                            numeric.relation(), oracleLabel(row));
                    assertEquals(0, numeric.value().compareTo(
                            new java.math.BigDecimal(row.get("value"))), oracleLabel(row));
                    assertReasons(row, numeric.reasons());
                }
                case NotApplicableResult notApplicable ->
                        assertEquals(ReasonCode.valueOf(row.get("reason")),
                                notApplicable.reason(), oracleLabel(row));
                case IntervalResult interval -> {
                    assertEquals(0, interval.lower().compareTo(
                            new java.math.BigDecimal(row.get("lower"))), oracleLabel(row));
                    assertEquals(0, interval.upper().compareTo(
                            new java.math.BigDecimal(row.get("upper"))), oracleLabel(row));
                    assertReasons(row, interval.reasons());
                }
                case UnavailableResult unavailable -> assertReasons(row, unavailable.reasons());
                default -> throw new AssertionError(
                        "Unsupported built-in oracle result: " + measurement.result());
            }
        }
    }

    private static SubjectRef resolveSubject(CaseAnalysis analysis, CSVRecord row) {
        return switch (row.get("scope")) {
            case "package" -> {
                PackageCompound pkg = analysis.context().packages().stream()
                        .filter(candidate -> candidate.getQualifiedName().equals(row.get("subject")))
                        .findFirst().orElse(null);
                assertNotNull(pkg, "Unknown oracle subject: " + row.get("subject"));
                yield new SubjectRef(MetricScope.PACKAGE, pkg.symbolId);
            }
            case "type" -> {
                Compound type = analysis.typesByQualifiedName().get(row.get("subject"));
                assertNotNull(type, "Unknown oracle subject: " + row.get("subject"));
                yield new SubjectRef(MetricScope.TYPE, type.symbolId);
            }
            case "method", "constructor" -> {
                Member member = analysis.membersByCanonicalName().get(row.get("subject"));
                assertNotNull(member, "Unknown oracle subject: " + row.get("subject"));
                MetricScope scope = member instanceof Constructor
                        ? MetricScope.CONSTRUCTOR : MetricScope.METHOD;
                assertEquals(row.get("scope"), scope.serializedName(),
                        "Wrong oracle scope for " + row.get("subject"));
                yield new SubjectRef(scope, member.id);
            }
            case "initializer" -> {
                InitializerBlock initializer = analysis.initializersByCanonicalName().get(row.get("subject"));
                assertNotNull(initializer, "Unknown oracle subject: " + row.get("subject"));
                yield new SubjectRef(MetricScope.INITIALIZER, initializer.id());
            }
            case "lambda" -> {
                LambdaExpressionEntity lambda = analysis.lambdasByCanonicalName().get(row.get("subject"));
                assertNotNull(lambda, "Unknown oracle subject: " + row.get("subject"));
                yield new SubjectRef(MetricScope.LAMBDA, lambda.id());
            }
            default -> throw new AssertionError("Unsupported oracle scope: " + row.get("scope"));
        };
    }

    private static String oracleLabel(CSVRecord row) {
        return row.get("case_id") + ":" + row.get("metric") + ":" + row.get("subject");
    }

    private static void assertReasons(CSVRecord row, java.util.Set<ReasonCode> actual) {
        java.util.Set<ReasonCode> expected = row.get("reason").isBlank()
                ? java.util.Set.of()
                : java.util.Arrays.stream(row.get("reason").split("\\|"))
                        .map(String::trim).map(ReasonCode::valueOf)
                        .collect(java.util.stream.Collectors.toSet());
        assertEquals(expected, actual, oracleLabel(row));
    }

    private static Map<String, CaseAnalysis> analyses() throws Exception {
        Map<String, CaseAnalysis> result = new LinkedHashMap<>();
        for (CorpusCase corpusCase : cases()) result.put(corpusCase.id(), analyze(corpusCase));
        return Map.copyOf(result);
    }

    private static CaseAnalysis analyze(CorpusCase corpusCase) throws Exception {
        List<Path> files = sourceFiles(corpusCase);
        JdtParser.Config config = new JdtParser.Config();
        config.sourcepath = List.of(corpusCase.sourceRoot());
        config.release = "17";
        JdtParser parser = new JdtParser(config);
        List<PackageCompound> parsed = new ArrayList<>();
        for (Path file : files) parsed.add((PackageCompound) parser.parse(file));
        List<PackageCompound> packages = PackageMerger.merge(parsed);
        AnalysisContext context = new AnalysisContext(
                packages, files, BaselineMetadata.capture(config));
        Map<String, Compound> types = new HashMap<>();
        for (Compound pkg : packages) collectTypes(pkg, types);
        Map<String, Member> members = new HashMap<>();
        Map<String, InitializerBlock> initializers = new HashMap<>();
        Map<String, LambdaExpressionEntity> lambdas = new HashMap<>();
        for (Compound type : types.values()) {
            for (Member member : type.getMembers()) {
                if (member instanceof Method || member instanceof Constructor) {
                    Member previous = members.put(canonicalName(member), member);
                    if (previous != null) {
                        throw new AssertionError("Duplicate corpus member identity: " + canonicalName(member));
                    }
                }
            }
            if (type instanceof TypeCompound typeCompound) {
                for (InitializerBlock initializer : typeCompound.initializers) {
                    initializers.put(canonicalName(initializer), initializer);
                }
                for (LambdaExpressionEntity lambda : typeCompound.lambdas) {
                    lambdas.put(canonicalName(lambda), lambda);
                }
            }
        }
        return new CaseAnalysis(context, Map.copyOf(types), Map.copyOf(members),
                Map.copyOf(initializers), Map.copyOf(lambdas));
    }

    private static void collectTypes(Compound compound, Map<String, Compound> target) {
        if (compound.getKind() != cz.utulnykoutek.metrics.domain.CompoundKind.PACKAGE
                && compound.getQualifiedName() != null) {
            target.put(compound.getQualifiedName(), compound);
        }
        compound.getChildren().forEach(child -> collectTypes(child, target));
    }

    private static long countTypes(List<? extends Compound> roots, NestingKind nesting) {
        return roots.stream().mapToLong(root -> countTypes(root, nesting)).sum();
    }

    private static long countTypes(Compound compound, NestingKind nesting) {
        long current = compound instanceof TypeCompound type && type.nesting == nesting ? 1 : 0;
        return current + compound.getChildren().stream()
                .mapToLong(child -> countTypes(child, nesting)).sum();
    }

    private static String canonicalName(Member member) {
        String name = member instanceof Constructor ? "<init>" : member.name;
        List<Parameter> parameters = member instanceof Constructor constructor
                ? constructor.parameters : ((Method) member).parameters;
        return member.owner.qualifiedName + "#" + name + "(" + parameters.stream()
                .map(ValidationCorpusTest::canonicalType)
                .collect(java.util.stream.Collectors.joining(",")) + ")";
    }

    private static String canonicalName(InitializerBlock initializer) {
        String category = initializer.isStatic() ? "static" : "instance";
        String suffix = initializer.id().substring(initializer.id().lastIndexOf(':') + 1,
                initializer.id().length() - 1);
        return initializer.owner().qualifiedName + "#<initializer:" + category + ":" + suffix + ">";
    }

    private static String canonicalName(LambdaExpressionEntity lambda) {
        String suffix = lambda.id().substring(lambda.id().lastIndexOf(':') + 1,
                lambda.id().length() - 1);
        return lambda.owner().qualifiedName + "#<lambda:" + suffix + ">";
    }

    private static String canonicalType(Parameter parameter) {
        if (parameter.type == null) return "?";
        String type = parameter.type.qualifiedName == null || parameter.type.qualifiedName.isBlank()
                ? parameter.type.displayName : parameter.type.qualifiedName;
        if (parameter.varargs && !type.endsWith("[]")) type += "[]";
        return type.replace('$', '.').replace(" ", "");
    }

    private static List<CorpusCase> cases() throws Exception {
        List<CorpusCase> result = new ArrayList<>();
        for (CSVRecord row : csv(CORPUS.resolve("cases.csv"))) {
            Path project = CORPUS.resolve(row.get("project_dir")).normalize();
            Path sourceRoot = CORPUS.resolve(row.get("source_root")).normalize();
            assertTrue(Files.isRegularFile(project.resolve("pom.xml")), "Missing pom.xml for " + row.get("case_id"));
            assertTrue(Files.isDirectory(sourceRoot), "Missing source root for " + row.get("case_id"));
            result.add(new CorpusCase(
                    row.get("case_id"), project, sourceRoot, Boolean.parseBoolean(row.get("expected_build"))));
        }
        return List.copyOf(result);
    }

    private static List<Path> sourceFiles(CorpusCase corpusCase) {
        try (var paths = Files.walk(corpusCase.sourceRoot())) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot list sources for " + corpusCase.id(), e);
        }
    }

    private static List<CSVRecord> csv(Path file) throws Exception {
        try (var parser = CSVFormat.DEFAULT.withFirstRecordAsHeader()
                .parse(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
            return parser.getRecords();
        }
    }

    private record CorpusCase(String id, Path project, Path sourceRoot, boolean expectedBuild) {}

    private record CaseAnalysis(
            AnalysisContext context,
            Map<String, Compound> typesByQualifiedName,
            Map<String, Member> membersByCanonicalName,
            Map<String, InitializerBlock> initializersByCanonicalName,
            Map<String, LambdaExpressionEntity> lambdasByCanonicalName) {}
}
