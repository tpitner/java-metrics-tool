// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.config.CkProviderConfig;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.ck.CkCsvSchema;
import cz.utulnykoutek.metrics.metrics.ck.CkMetricDefinition;
import cz.utulnykoutek.metrics.metrics.ck.CkMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ck.CkProcessRunner;
import cz.utulnykoutek.metrics.metrics.ck.CkProviderException;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementKey;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.parser.JdtParser;
import cz.utulnykoutek.metrics.parser.PackageMerger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CkMetricsProviderTest {
    @TempDir Path temporaryDirectory;

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void runsIsolatedProcessMapsSubjectsAndImportsMetrics() throws Exception {
        Path fixture = Path.of("src/test/resources/maven-it/small/src/main/java")
                .toAbsolutePath().normalize();
        Path app = fixture.resolve("com/example/demo/App.java");
        Path util = fixture.resolve("com/example/demo/Util.java");
        JdtParser.Config parserConfig = new JdtParser.Config();
        parserConfig.sourcepath = List.of(fixture);
        List<PackageCompound> packages = PackageMerger.merge(List.of(
                (PackageCompound) new JdtParser(parserConfig).parse(app),
                (PackageCompound) new JdtParser(parserConfig).parse(util)));
        AnalysisContext context = new AnalysisContext(
                packages, List.of(app, util), BaselineMetadata.capture(parserConfig));

        Path fakeJar = fakeJar();
        Path executable = fakeJavaExecutable();
        CkProviderConfig config = new CkProviderConfig(
                true, Optional.of(fakeJar), executable.toString(), false, 0, false,
                List.of("target"), Duration.ofSeconds(10), true);

        ProviderResult result = new CkMetricsProvider(
                config, fixture, temporaryDirectory.resolve("artifacts"), false).measure(context);

        assertEquals("ck", result.run().providerId());
        assertEquals("0.7.1-test", result.run().version());
        assertEquals("false", result.run().configuration().get("rawArtifactsRetained"));
        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());

        String appTypeId = packages.getFirst().getChildren().stream()
                .filter(type -> type.getQualifiedName().equals("com.example.demo.App"))
                .findFirst().orElseThrow().symbolId;
        SubjectRef appSubject = new SubjectRef(MetricScope.TYPE, appTypeId);
        NumericResult wmc = (NumericResult) result.measurements().find(new MeasurementKey(
                CkMetricDefinition.WMC.id(), appSubject, result.run().providerRunId()))
                .orElseThrow().result();
        assertEquals(2, wmc.value().intValueExact());
        assertInstanceOf(NotApplicableResult.class, result.measurements().find(new MeasurementKey(
                CkMetricDefinition.TCC.id(), appSubject, result.run().providerRunId()))
                .orElseThrow().result());

        assertTrue(result.measurements().all().stream().anyMatch(measurement ->
                measurement.metricId().equals(CkMetricDefinition.PARAMETERS.id())
                        && measurement.subject().scope() == MetricScope.CONSTRUCTOR
                        && ((NumericResult) measurement.result()).value().intValueExact() == 1));
        assertFalse(Files.exists(temporaryDirectory.resolve("artifacts")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void reportsNonZeroProcessExit() throws Exception {
        Path fakeJar = fakeJar();
        Path executable = temporaryDirectory.resolve("failing-java");
        Files.writeString(executable, "#!/bin/sh\necho failed >&2\nexit 7\n");
        makeExecutable(executable);
        CkProviderConfig config = new CkProviderConfig(
                true, Optional.of(fakeJar), executable.toString(), false, 0, false,
                List.of(), Duration.ofSeconds(10), false);

        CkProviderException error = assertThrows(CkProviderException.class, () ->
                new CkProcessRunner().run(
                        config, temporaryDirectory, temporaryDirectory.resolve("artifacts"), false));

        assertTrue(error.getMessage().contains("code 7"), error.getMessage());
    }

    private Path fakeJavaExecutable() throws Exception {
        String classHeader = String.join(",", CkCsvSchema.CLASS_HEADER);
        String methodHeader = String.join(",", CkCsvSchema.METHOD_HEADER);
        String classRow = classRow();
        String greetRow = methodRow("greet/0", false, 13, 0);
        String constructorRow = methodRow("App/1[java.lang.String]", true, 9, 1);
        Path executable = temporaryDirectory.resolve("fake-java");
        Files.writeString(executable, """
                #!/bin/sh
                if [ "$1" = "-version" ]; then
                  echo 'fake java 21'
                  exit 0
                fi
                out="$7"
                cat > "$out/class.csv" <<'CSV'
                %s
                %s
                CSV
                cat > "$out/method.csv" <<'CSV'
                %s
                %s
                %s
                CSV
                echo 'Metrics extracted!!!'
                """.formatted(classHeader, classRow, methodHeader, greetRow, constructorRow));
        makeExecutable(executable);
        return executable;
    }

    private static String classRow() {
        List<String> values = defaults(CkCsvSchema.CLASS_HEADER.size());
        put(values, CkCsvSchema.CLASS_HEADER, "file", "/tmp/App.java");
        put(values, CkCsvSchema.CLASS_HEADER, "class", "com.example.demo.App");
        put(values, CkCsvSchema.CLASS_HEADER, "type", "class");
        put(values, CkCsvSchema.CLASS_HEADER, "wmc", "2");
        put(values, CkCsvSchema.CLASS_HEADER, "dit", "1");
        put(values, CkCsvSchema.CLASS_HEADER, "totalMethodsQty", "2");
        put(values, CkCsvSchema.CLASS_HEADER, "publicMethodsQty", "2");
        put(values, CkCsvSchema.CLASS_HEADER, "totalFieldsQty", "1");
        put(values, CkCsvSchema.CLASS_HEADER, "loc", "9");
        put(values, CkCsvSchema.CLASS_HEADER, "lcom*", "0.0");
        put(values, CkCsvSchema.CLASS_HEADER, "tcc", "NaN");
        put(values, CkCsvSchema.CLASS_HEADER, "lcc", "NaN");
        return String.join(",", values);
    }

    private static String methodRow(String method, boolean constructor, int line, int parameters) {
        List<String> values = defaults(CkCsvSchema.METHOD_HEADER.size());
        put(values, CkCsvSchema.METHOD_HEADER, "file", "/tmp/App.java");
        put(values, CkCsvSchema.METHOD_HEADER, "class", "com.example.demo.App");
        put(values, CkCsvSchema.METHOD_HEADER, "method", method);
        put(values, CkCsvSchema.METHOD_HEADER, "constructor", Boolean.toString(constructor));
        put(values, CkCsvSchema.METHOD_HEADER, "line", Integer.toString(line));
        put(values, CkCsvSchema.METHOD_HEADER, "wmc", "1");
        put(values, CkCsvSchema.METHOD_HEADER, "loc", "3");
        put(values, CkCsvSchema.METHOD_HEADER, "parametersQty", Integer.toString(parameters));
        put(values, CkCsvSchema.METHOD_HEADER, "hasJavaDoc", "false");
        return String.join(",", values);
    }

    private Path fakeJar() throws Exception {
        Path jar = temporaryDirectory.resolve("ck-test.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("META-INF/maven/com.github.mauricioaniche/ck/pom.properties"));
            output.write("version=0.7.1-test\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private static List<String> defaults(int size) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < size; i++) values.add("0");
        return values;
    }

    private static void put(List<String> values, List<String> header, String column, String value) {
        values.set(header.indexOf(column), value);
    }

    private static void makeExecutable(Path file) throws Exception {
        Files.setPosixFilePermissions(file, EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE));
    }
}
