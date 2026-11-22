// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.*;
import cz.utulnykoutek.metrics.parser.JdtParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Lcom4ComputerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void connectsMethodsBySharedFieldsCallsAndExpandedAccessors() throws Exception {
        MeasurementFixture cohesive = measure("Cohesive.java", """
                package sample;
                public class Cohesive {
                    int state;
                    void setState(int state) { this.state = state; }
                    void write() { setState(1); }
                    int readAdjusted() { return state + 1; }
                }
                """, true);
        NumericResult cohesiveResult = (NumericResult) cohesive.measurement.result();
        assertEquals(1, cohesiveResult.value().intValueExact());
        assertEquals(ValueRelation.EXACT, cohesiveResult.relation());
        assertEquals("2", cohesive.measurement.attributes().get("eligibleMethodCount"));
        assertEquals("1", cohesive.measurement.attributes().get("excludedAccessorCount"));
        assertEquals("1", cohesive.measurement.attributes().get("fieldEdgeCount"));

        MeasurementFixture disconnected = measure("Disconnected.java", """
                package sample;
                public class Disconnected {
                    int left;
                    int right;
                    void a() { left++; }
                    void b() { left--; }
                    void c() { right++; }
                    void d() { c(); }
                }
                """, true);
        NumericResult disconnectedResult = (NumericResult) disconnected.measurement.result();
        assertEquals(2, disconnectedResult.value().intValueExact());
        assertEquals("1", disconnected.measurement.attributes().get("fieldEdgeCount"));
        assertEquals("1", disconnected.measurement.attributes().get("callEdgeCount"));
        assertEquals("false", disconnected.measurement.attributes().get("lowInformation"));
    }

    @Test
    void marksSingleMethodAsExactButLowInformation() throws Exception {
        MeasurementFixture fixture = measure("Single.java", """
                package sample;
                public class Single {
                    void work() {}
                }
                """, true);

        NumericResult result = (NumericResult) fixture.measurement.result();
        assertEquals(1, result.value().intValueExact());
        assertEquals(ValueRelation.EXACT, result.relation());
        assertEquals("true", fixture.measurement.attributes().get("lowInformation"));
    }

    @Test
    void reportsSpecificNotApplicableReasons() throws Exception {
        assertNotApplicable("Contract.java", """
                package sample;
                public interface Contract { void work(); }
                """, ReasonCode.INTERFACE_OR_ANNOTATION);
        assertNotApplicable("Utilities.java", """
                package sample;
                public class Utilities { static void work() {} }
                """, ReasonCode.STATIC_UTILITY_TYPE);
        assertNotApplicable("Dto.java", """
                package sample;
                public class Dto {
                    int value;
                    int getValue() { return value; }
                    void setValue(int value) { this.value = value; }
                }
                """, ReasonCode.ACCESSORS_ONLY);
        assertNotApplicable("Empty.java", """
                package sample;
                public class Empty {}
                """, ReasonCode.NO_ELIGIBLE_MEMBERS);
    }

    @Test
    void reportsObservedComponentsAsUpperBoundWhenEdgesAreUnresolved() throws Exception {
        MeasurementFixture fixture = measure("Partial.java", """
                package sample;
                public class Partial {
                    int state;
                    void target() {}
                    void source() {
                        state++;
                        target();
                    }
                }
                """, false);

        NumericResult result = (NumericResult) fixture.measurement.result();
        assertEquals(2, result.value().intValueExact());
        assertEquals(ValueRelation.AT_MOST, result.relation());
        assertEquals(MeasurementStatus.PARTIAL, fixture.measurement.status());
        assertEquals(Set.of(ReasonCode.UNRESOLVED_FIELD_REFERENCE,
                ReasonCode.UNRESOLVED_METHOD_CALL), result.reasons());
        assertEquals("1", fixture.measurement.attributes().get("unresolvedFieldAccessCount"));
        assertEquals("1", fixture.measurement.attributes().get("unresolvedIntraTypeCallCount"));
    }

    private void assertNotApplicable(String fileName, String source, ReasonCode reason) throws Exception {
        MeasurementFixture fixture = measure(fileName, source, true);
        assertEquals(new NotApplicableResult(reason), fixture.measurement.result());
    }

    private MeasurementFixture measure(String fileName, String source, boolean resolveBindings)
            throws Exception {
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
        TypeCompound type = (TypeCompound) pkg.getChildren().getFirst();
        ProviderResult provider = new JavaMetricsProvider(Set.of(BuiltInMetricDefinition.LCOM4))
                .measure(new AnalysisContext(
                        List.of(pkg), List.of(sourceFile), BaselineMetadata.capture(config)));
        MetricMeasurement measurement = provider.measurements().find(new MeasurementKey(
                BuiltInMetricDefinition.LCOM4.id(),
                new SubjectRef(MetricScope.TYPE, type.symbolId),
                provider.run().providerRunId())).orElseThrow();
        return new MeasurementFixture(type, measurement);
    }

    private record MeasurementFixture(TypeCompound type, MetricMeasurement measurement) {}
}
