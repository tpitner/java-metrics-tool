// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.InitializerBlock;
import cz.utulnykoutek.metrics.domain.LambdaExpressionEntity;
import cz.utulnykoutek.metrics.domain.Member;
import cz.utulnykoutek.metrics.domain.Method;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.export.JsonExporter;
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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceLineMetricsComputerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void countsPhysicalAndNonCommentLinesByExactSourceRange() throws Exception {
        Path source = temporaryDirectory.resolve("Outer.java");
        Files.writeString(source, """
                package sample; // package declaration

                /**
                 * type documentation
                 */
                @Deprecated
                public class Outer {
                    /* field documentation */
                    private int x;

                    public int value() {
                        return x;
                    }

                    class Inner {
                        String text = \"""
                                content only
                                \""";
                    }
                }
                """);

        JdtParser.Config parserConfig = new JdtParser.Config();
        parserConfig.sourcepath = List.of(temporaryDirectory);
        parserConfig.release = "17";
        PackageCompound pkg = (PackageCompound) new JdtParser(parserConfig).parse(source);
        Compound outer = pkg.getChildren().getFirst();
        Compound inner = outer.getChildren().getFirst();
        Member value = outer.getMembers().stream()
                .filter(member -> member instanceof Method && member.name.equals("value"))
                .findFirst().orElseThrow();

        assertNotNull(outer.sourceSpan);
        assertEquals('@', Files.readString(source).charAt(outer.sourceSpan.startOffset()));

        ProviderResult result = new JavaMetricsProvider(Set.of(
                BuiltInMetricDefinition.PHYSICAL_LOC,
                BuiltInMetricDefinition.NCLOC,
                BuiltInMetricDefinition.PHYSICAL_LOC_EXCLUSIVE,
                BuiltInMetricDefinition.NCLOC_EXCLUSIVE)).measure(new AnalysisContext(
                        List.of(pkg), List.of(source), BaselineMetadata.capture(parserConfig)));

        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC, MetricScope.PROJECT, "project", 20);
        assertMetric(result, BuiltInMetricDefinition.NCLOC, MetricScope.PROJECT, "project", 12);
        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC, MetricScope.FILE,
                source.toAbsolutePath().normalize().toString(), 20);
        assertMetric(result, BuiltInMetricDefinition.NCLOC, MetricScope.PACKAGE, pkg.symbolId, 12);
        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC, MetricScope.TYPE, outer.symbolId, 15);
        assertMetric(result, BuiltInMetricDefinition.NCLOC, MetricScope.TYPE, outer.symbolId, 11);
        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC_EXCLUSIVE,
                MetricScope.TYPE, outer.symbolId, 10);
        assertMetric(result, BuiltInMetricDefinition.NCLOC_EXCLUSIVE,
                MetricScope.TYPE, outer.symbolId, 7);
        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC, MetricScope.TYPE, inner.symbolId, 5);
        assertMetric(result, BuiltInMetricDefinition.NCLOC, MetricScope.TYPE, inner.symbolId, 4);
        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC, MetricScope.METHOD, value.id, 3);
        assertMetric(result, BuiltInMetricDefinition.NCLOC, MetricScope.METHOD, value.id, 3);
    }

    @Test
    void measuresInitializersAndLambdasAsIndependentSubjects() throws Exception {
        Path source = temporaryDirectory.resolve("InitLambda.java");
        Files.writeString(source, """
                package sample;
                class InitLambda {
                    static {
                        int x = 1;
                    }
                    {
                        Runnable task = () -> {
                            System.out.println("x");
                        };
                    }
                    java.util.function.IntUnaryOperator operation =
                            value -> value + 1;
                }
                """);

        JdtParser.Config parserConfig = new JdtParser.Config();
        parserConfig.sourcepath = List.of(temporaryDirectory);
        parserConfig.release = "17";
        PackageCompound pkg = (PackageCompound) new JdtParser(parserConfig).parse(source);
        cz.utulnykoutek.metrics.domain.TypeCompound type =
                (cz.utulnykoutek.metrics.domain.TypeCompound) pkg.getChildren().getFirst();

        assertEquals(2, type.initializers.size());
        assertEquals(2, type.lambdas.size());
        InitializerBlock staticInitializer = type.initializers.getFirst();
        InitializerBlock instanceInitializer = type.initializers.get(1);
        LambdaExpressionEntity blockLambda = type.lambdas.getFirst();
        LambdaExpressionEntity expressionLambda = type.lambdas.get(1);
        assertEquals(instanceInitializer.id(), blockLambda.enclosingSubjectId());
        assertEquals(type.getMembers().getFirst().id, expressionLambda.enclosingSubjectId());

        ProviderResult result = new JavaMetricsProvider(Set.of(
                BuiltInMetricDefinition.PHYSICAL_LOC,
                BuiltInMetricDefinition.NCLOC)).measure(new AnalysisContext(
                        List.of(pkg), List.of(source), BaselineMetadata.capture(parserConfig)));

        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC,
                MetricScope.INITIALIZER, staticInitializer.id(), 3);
        assertMetric(result, BuiltInMetricDefinition.NCLOC,
                MetricScope.INITIALIZER, staticInitializer.id(), 3);
        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC,
                MetricScope.INITIALIZER, instanceInitializer.id(), 5);
        assertMetric(result, BuiltInMetricDefinition.NCLOC,
                MetricScope.INITIALIZER, instanceInitializer.id(), 5);
        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC,
                MetricScope.LAMBDA, blockLambda.id(), 3);
        assertMetric(result, BuiltInMetricDefinition.NCLOC,
                MetricScope.LAMBDA, blockLambda.id(), 3);
        assertMetric(result, BuiltInMetricDefinition.PHYSICAL_LOC,
                MetricScope.LAMBDA, expressionLambda.id(), 1);
        assertMetric(result, BuiltInMetricDefinition.NCLOC,
                MetricScope.LAMBDA, expressionLambda.id(), 1);

        String json = JsonExporter.render(
                pkg, BaselineMetadata.capture(parserConfig), result);
        assertTrue(json.contains("\"initializers\":"));
        assertTrue(json.contains("\"lambdas\":"));
        assertTrue(json.contains("\"enclosingSubjectId\":"));
        assertTrue(json.contains("\"scope\":\"initializer\""));
        assertTrue(json.contains("\"scope\":\"lambda\""));
    }

    private static void assertMetric(
            ProviderResult result,
            BuiltInMetricDefinition definition,
            MetricScope scope,
            String subjectId,
            int expected) {
        MeasurementKey key = new MeasurementKey(
                definition.id(), new SubjectRef(scope, subjectId), result.run().providerRunId());
        NumericResult numeric = (NumericResult) result.measurements().find(key).orElseThrow().result();
        assertEquals(expected, numeric.value().intValueExact(), definition.id() + " " + scope);
    }
}
