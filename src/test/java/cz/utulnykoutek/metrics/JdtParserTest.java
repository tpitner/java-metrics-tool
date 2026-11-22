// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.export.TextExporter;
import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementKey;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.parser.JdtParser;
import cz.utulnykoutek.metrics.parser.PackageMerger;
import cz.utulnykoutek.metrics.parser.SyntaxErrorException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link cz.utulnykoutek.metrics.parser.JdtParser}. */
class JdtParserTest {

    @Test
    void reportsSyntaxErrors() throws Exception {
        Path file = Path.of(getClass().getResource("/cz/utulnykoutek/metrics/sample/SyntaxError.java").toURI());
        JdtParser parser = new JdtParser();

        SyntaxErrorException e = assertThrows(SyntaxErrorException.class, () -> parser.parse(file));

        assertFalse(e.getErrors().isEmpty());
        String first = e.getErrors().get(0);
        assertTrue(first.contains("SyntaxError.java"), first);
        assertTrue(first.matches(".*SyntaxError\\.java:\\d+:\\d+: .*"), first);
    }

    @Test
    void acceptsMissingDependencies() throws Exception {
        Path file = Path.of(getClass().getResource("/cz/utulnykoutek/metrics/sample/MissingDependencies.java").toURI());
        JdtParser parser = new JdtParser();

        Compound root = parser.parse(file);

        assertInstanceOf(PackageCompound.class, root);
        assertEquals("cz.utulnykoutek.metrics.sample", root.getQualifiedName());
        assertEquals(1, root.getChildren().size());
    }

    @Test
    void mergesPackagesAcrossCompilationUnits() throws Exception {
        Path first = Path.of(getClass().getResource("/cz/utulnykoutek/metrics/sample/Nested.java").toURI());
        Path second = Path.of(getClass().getResource("/cz/utulnykoutek/metrics/sample/Nested2.java").toURI());
        JdtParser parser = new JdtParser();

        List<PackageCompound> merged = PackageMerger.merge(List.of(
                (PackageCompound) parser.parse(first),
                (PackageCompound) parser.parse(second)));

        assertEquals(1, merged.size());
        assertEquals("cz.utulnykoutek.metrics.sample", merged.getFirst().getQualifiedName());
        assertEquals(2, merged.getFirst().getChildren().size());
    }

    @Test
    void parsesNestedTypesAndMeasuresDeclaredMembers() throws Exception {
        Path file = Path.of(getClass().getResource("/cz/utulnykoutek/metrics/sample/Nested.java").toURI());
        JdtParser.Config config = new JdtParser.Config();
        JdtParser parser = new JdtParser(config);
        Compound root = parser.parse(file);
        PackageCompound pkg = (PackageCompound) root;
        ProviderResult measurements = new JavaMetricsProvider().measure(new AnalysisContext(
                List.of(pkg), List.of(file), BaselineMetadata.capture(config)));

        assertEquals("cz.utulnykoutek.metrics.sample", pkg.getQualifiedName());

        // top-level type
        assertEquals(1, pkg.getChildren().size());
        Compound outer = pkg.getChildren().getFirst();
        assertEquals("Outer", outer.getName());

        assertEquals(2, count(measurements, outer, BuiltInMetricDefinition.DECLARED_METHODS));
        assertEquals(1, count(measurements, outer, BuiltInMetricDefinition.DECLARED_CONSTRUCTORS));
        assertEquals(3, count(measurements, outer, BuiltInMetricDefinition.DECLARED_FIELDS));

        // inner
        Compound inner = outer.getChildren().getFirst();
        assertEquals("Inner", inner.getName());
        assertEquals(3, count(measurements, inner, BuiltInMetricDefinition.DECLARED_METHODS));
        assertEquals(1, count(measurements, inner, BuiltInMetricDefinition.DECLARED_CONSTRUCTORS));
        assertEquals(2, count(measurements, inner, BuiltInMetricDefinition.DECLARED_FIELDS));

        // deep inner
        Compound deep = inner.getChildren().getFirst();
        assertEquals("DeepInner", deep.getName());
        assertEquals(2, count(measurements, deep, BuiltInMetricDefinition.DECLARED_METHODS));
        assertEquals(0, count(measurements, deep, BuiltInMetricDefinition.DECLARED_CONSTRUCTORS));
        assertEquals(0, count(measurements, deep, BuiltInMetricDefinition.DECLARED_FIELDS));

        // text output sanity
        String text = TextExporter.render(pkg, measurements);
        assertTrue(text.contains("Package: cz.utulnykoutek.metrics.sample"));
        assertTrue(text.contains("Outer [class]"));
        assertTrue(text.contains("DeepInner [class]"));
        assertTrue(text.contains("Declared: methods=2, constructors=1, fields=3"));
        assertFalse(text.contains("Recursive:"));
    }

    private static int count(
            ProviderResult result,
            Compound type,
            BuiltInMetricDefinition definition) {
        SubjectRef subject = new SubjectRef(MetricScope.TYPE, type.symbolId);
        MeasurementKey key = new MeasurementKey(
                definition.id(), subject, result.run().providerRunId());
        NumericResult numeric = (NumericResult) result.measurements().find(key).orElseThrow().result();
        return numeric.value().intValueExact();
    }
}
