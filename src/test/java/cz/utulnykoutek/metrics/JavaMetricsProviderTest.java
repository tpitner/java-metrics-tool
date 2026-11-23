// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementKey;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.parser.JdtParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaMetricsProviderTest {
    @Test
    void computesOnlySelectedDefinitions() throws Exception {
        Path file = Path.of(getClass().getResource(
                "/cz/utulnykoutek/metrics/sample/Nested.java").toURI());
        JdtParser.Config config = new JdtParser.Config();
        PackageCompound pkg = (PackageCompound) new JdtParser(config).parse(file);
        JavaMetricsProvider provider = new JavaMetricsProvider(
                Set.of(BuiltInMetricDefinition.DECLARED_METHODS));

        ProviderResult result = provider.measure(new AnalysisContext(
                List.of(pkg), List.of(file), BaselineMetadata.capture(config)));

        assertEquals(Set.of(BuiltInMetricDefinition.DECLARED_METHODS), provider.definitions());
        assertEquals(3, result.measurements().size());
        assertTrue(result.measurements().all().stream().allMatch(measurement ->
                measurement.metricId().equals(BuiltInMetricDefinition.DECLARED_METHODS.id())));
    }

    @Test
    void exposesAndComputesOnlyConfiguredBuiltInDefinitions() throws Exception {
        Path file = Path.of(getClass().getResource(
                "/cz/utulnykoutek/metrics/sample/Nested.java").toURI());
        JdtParser.Config config = new JdtParser.Config();
        PackageCompound pkg = (PackageCompound) new JdtParser(config).parse(file);
        JavaMetricsProvider provider = new JavaMetricsProvider();

        assertEquals(Set.of(
                BuiltInMetricDefinition.DECLARED_METHODS,
                BuiltInMetricDefinition.DECLARED_CONSTRUCTORS,
                BuiltInMetricDefinition.DECLARED_FIELDS,
                BuiltInMetricDefinition.PHYSICAL_LOC,
                BuiltInMetricDefinition.NCLOC,
                BuiltInMetricDefinition.PHYSICAL_LOC_EXCLUSIVE,
                BuiltInMetricDefinition.NCLOC_EXCLUSIVE,
                BuiltInMetricDefinition.CYCLOMATIC,
                BuiltInMetricDefinition.WMC,
                BuiltInMetricDefinition.LCOM4,
                BuiltInMetricDefinition.CA,
                BuiltInMetricDefinition.CE,
                BuiltInMetricDefinition.INSTABILITY,
                BuiltInMetricDefinition.ABSTRACTNESS,
                BuiltInMetricDefinition.DISTANCE), provider.definitions());

        ProviderResult result = provider.measure(new AnalysisContext(
                List.of(pkg), List.of(file), BaselineMetadata.capture(config)));

        assertEquals(JavaMetricsProvider.ID, result.run().providerId());
        assertTrue(result.measurements().size() > 53);
        assertTrue(result.diagnostics().isEmpty());

        Compound outer = pkg.getChildren().getFirst();
        SubjectRef subject = new SubjectRef(MetricScope.TYPE, outer.symbolId);
        MeasurementKey key = new MeasurementKey(
                BuiltInMetricDefinition.DECLARED_METHODS.id(),
                subject,
                result.run().providerRunId());
        NumericResult methods = (NumericResult) result.measurements().find(key).orElseThrow().result();
        assertEquals(2, methods.value().intValueExact());
    }

    @Test
    void declaredFieldsExcludeEnumConstants() throws Exception {
        Path file = Path.of(getClass().getResource(
                "/cz/utulnykoutek/metrics/sample/EnumCounts.java").toURI());
        JdtParser.Config config = new JdtParser.Config();
        PackageCompound pkg = (PackageCompound) new JdtParser(config).parse(file);
        ProviderResult result = new JavaMetricsProvider().measure(new AnalysisContext(
                List.of(pkg), List.of(file), BaselineMetadata.capture(config)));
        Compound enumType = pkg.getChildren().getFirst();
        SubjectRef subject = new SubjectRef(MetricScope.TYPE, enumType.symbolId);
        MeasurementKey key = new MeasurementKey(
                BuiltInMetricDefinition.DECLARED_FIELDS.id(),
                subject,
                result.run().providerRunId());

        NumericResult fields = (NumericResult) result.measurements().find(key).orElseThrow().result();

        assertEquals(1, fields.value().intValueExact());
    }
}
