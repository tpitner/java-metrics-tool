// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.Constructor;
import cz.utulnykoutek.metrics.domain.LambdaExpressionEntity;
import cz.utulnykoutek.metrics.domain.Member;
import cz.utulnykoutek.metrics.domain.Method;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementKey;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.parser.JdtParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CyclomaticComplexityComputerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void measuresExecutablesSeparatelyAndAggregatesOnlyMethodsWithBodies() throws Exception {
        Path source = temporaryDirectory.resolve("ComplexitySample.java");
        Files.writeString(source, """
                package sample;
                abstract class ComplexitySample {
                    static { while (ready()) { break; } }
                    ComplexitySample() { do { } while (ready()); }
                    abstract void missing();
                    int complex(boolean a, boolean b, boolean c, int n) {
                        if (a && b || c) n++;
                        for (int i = 0; i < n; i++) { }
                        try { n++; } catch (RuntimeException ex) { n--; }
                        n = a ? n : -n;
                        return switch (n) {
                            case 1, 2 -> 1;
                            case 3 -> 2;
                            default -> 0;
                        };
                    }
                    void withLambda() {
                        Runnable task = () -> { if (ready() && ready()) { } };
                    }
                    static boolean ready() { return true; }
                }
                """);

        JdtParser.Config config = new JdtParser.Config();
        config.sourcepath = List.of(temporaryDirectory);
        config.release = "21";
        PackageCompound pkg = (PackageCompound) new JdtParser(config).parse(source);
        TypeCompound type = (TypeCompound) pkg.getChildren().getFirst();
        ProviderResult result = new JavaMetricsProvider(Set.of(
                BuiltInMetricDefinition.CYCLOMATIC,
                BuiltInMetricDefinition.WMC)).measure(new AnalysisContext(
                        List.of(pkg), List.of(source), BaselineMetadata.capture(config)));

        Method missing = method(type, "missing");
        Method complex = method(type, "complex");
        Method withLambda = method(type, "withLambda");
        Method ready = method(type, "ready");
        Constructor constructor = type.getMembers().stream()
                .filter(Constructor.class::isInstance).map(Constructor.class::cast)
                .findFirst().orElseThrow();
        LambdaExpressionEntity lambda = type.lambdas.getFirst();

        assertEquals(new NotApplicableResult(ReasonCode.EXECUTABLE_HAS_NO_BODY),
                measurement(result, BuiltInMetricDefinition.CYCLOMATIC,
                        MetricScope.METHOD, missing.id).result());
        assertValue(result, MetricScope.METHOD, complex.id, 9);
        assertValue(result, MetricScope.METHOD, withLambda.id, 1);
        assertValue(result, MetricScope.METHOD, ready.id, 1);
        assertValue(result, MetricScope.CONSTRUCTOR, constructor.id, 2);
        assertValue(result, MetricScope.INITIALIZER, type.initializers.getFirst().id(), 2);
        assertValue(result, MetricScope.LAMBDA, lambda.id(), 3);

        MetricMeasurement wmc = measurement(result, BuiltInMetricDefinition.WMC,
                MetricScope.TYPE, type.symbolId);
        assertEquals(11, ((NumericResult) wmc.result()).value().intValueExact());
        assertEquals("3", wmc.attributes().get("includedMethodCount"));
        assertEquals("1", wmc.attributes().get("bodylessMethodCount"));
    }

    private static Method method(TypeCompound type, String name) {
        return type.getMembers().stream()
                .filter(Method.class::isInstance).map(Method.class::cast)
                .filter(method -> method.name.equals(name))
                .findFirst().orElseThrow();
    }

    private static void assertValue(ProviderResult result, MetricScope scope, String id, int expected) {
        NumericResult numeric = (NumericResult) measurement(
                result, BuiltInMetricDefinition.CYCLOMATIC, scope, id).result();
        assertEquals(expected, numeric.value().intValueExact(), scope + " " + id);
    }

    private static MetricMeasurement measurement(
            ProviderResult result,
            BuiltInMetricDefinition definition,
            MetricScope scope,
            String id) {
        return result.measurements().find(new MeasurementKey(
                definition.id(), new SubjectRef(scope, id), result.run().providerRunId())).orElseThrow();
    }
}
