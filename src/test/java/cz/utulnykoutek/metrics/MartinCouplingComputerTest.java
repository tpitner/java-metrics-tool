// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.*;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.*;
import cz.utulnykoutek.metrics.parser.JdtParser;
import cz.utulnykoutek.metrics.parser.PackageMerger;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MartinCouplingComputerTest {
    private static final String RUN = "java-metrics:test";

    @Test
    void computesInternalCountsAndConservativeUnresolvedLowerBounds() {
        PackageCompound alpha = pkg("sample.alpha");
        PackageCompound beta = pkg("sample.beta");
        PackageCompound gamma = pkg("sample.gamma");
        TypeCompound alphaType = type(alpha, "Alpha");
        TypeCompound betaType = type(beta, "Beta");
        TypeCompound gammaType = type(gamma, "Gamma");
        add(alpha, alphaType, ref(betaType), DependencyUsage.FIELD);
        add(beta, betaType, ref(alphaType), DependencyUsage.METHOD_CALL);
        add(gamma, gammaType, ref(betaType), DependencyUsage.EXTENDS);
        add(alpha, alphaType, new SymbolRef("external", null, "vendor.api.Widget",
                "vendor.api", "Widget", Origin.EXTERNAL, true, TypeKindHint.CLASS),
                DependencyUsage.FIELD);
        add(alpha, alphaType, new SymbolRef("jdk", null, "java.lang.String",
                "java.lang", "String", Origin.JDK, true, TypeKindHint.CLASS),
                DependencyUsage.METHOD_RETURN);
        add(alpha, alphaType, SymbolRef.unresolved("missing-a", "MissingA"),
                DependencyUsage.METHOD_CALL);
        add(gamma, gammaType, SymbolRef.unresolved("missing-g", "MissingG"),
                DependencyUsage.FIELD_ACCESS);

        ProviderResult provider = provider(List.of(alpha, beta, gamma));

        assertPartial(provider, alpha, BuiltInMetricDefinition.CA, 1);
        assertPartial(provider, alpha, BuiltInMetricDefinition.CE, 1);
        assertPartial(provider, beta, BuiltInMetricDefinition.CA, 2);
        assertExact(provider, beta, BuiltInMetricDefinition.CE, 1);
        assertPartial(provider, gamma, BuiltInMetricDefinition.CA, 0);
        assertPartial(provider, gamma, BuiltInMetricDefinition.CE, 1);
        assertObservedInstability(provider, alpha, "0.5");
        assertObservedInstability(provider, beta, "0.3333333333333333333333333333333333");
        assertObservedInstability(provider, gamma, "1");
        assertObservedDistance(provider, alpha, "0.5");
        assertObservedDistance(provider, beta, "0.6666666666666666666666666666666667");
        assertObservedDistance(provider, gamma, "0");

        MetricMeasurement alphaCe = measurement(provider, alpha, BuiltInMetricDefinition.CE);
        assertEquals("1", alphaCe.attributes().get("externalOutgoingPackageCount"));
        assertEquals("1", alphaCe.attributes().get("jdkOutgoingPackageCount"));
        assertEquals("3", alphaCe.attributes().get("totalResolvedOutgoingPackageCount"));
        assertEquals("1", alphaCe.attributes().get("unresolvedOutgoingDependencyCount"));
    }

    @Test
    void computesExactInternalCouplingForTheCouplingCorpus() throws Exception {
        Path sourceRoot = Path.of("validation/cases/coupling/src/main/java");
        List<Path> files;
        try (var paths = Files.walk(sourceRoot)) {
            files = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        JdtParser.Config config = new JdtParser.Config();
        config.sourcepath = List.of(sourceRoot);
        config.release = "17";
        JdtParser parser = new JdtParser(config);
        List<PackageCompound> parsed = new ArrayList<>();
        for (Path file : files) parsed.add((PackageCompound) parser.parse(file));
        List<PackageCompound> packages = PackageMerger.merge(parsed);
        ProviderResult provider = new JavaMetricsProvider(Set.of(
                BuiltInMetricDefinition.CA, BuiltInMetricDefinition.CE,
                BuiltInMetricDefinition.INSTABILITY,
                BuiltInMetricDefinition.DISTANCE)).measure(
                new AnalysisContext(packages, files, BaselineMetadata.capture(config)));

        PackageCompound api = packageNamed(packages, "validation.coupling.api");
        PackageCompound impl = packageNamed(packages, "validation.coupling.impl");
        PackageCompound client = packageNamed(packages, "validation.coupling.client");
        assertExact(provider, api, BuiltInMetricDefinition.CA, 2);
        assertExact(provider, api, BuiltInMetricDefinition.CE, 0);
        assertExact(provider, impl, BuiltInMetricDefinition.CA, 0);
        assertExact(provider, impl, BuiltInMetricDefinition.CE, 1);
        assertExact(provider, client, BuiltInMetricDefinition.CA, 0);
        assertExact(provider, client, BuiltInMetricDefinition.CE, 1);
        assertExactInstability(provider, api, "0");
        assertExactInstability(provider, impl, "1");
        assertExactInstability(provider, client, "1");
        assertExactDistance(provider, api, "0");
        assertExactDistance(provider, impl, "0");
        assertExactDistance(provider, client, "0");
    }

    @Test
    void distinguishesExactIsolationFromIncompleteZeroObservedCoupling() {
        PackageCompound isolated = pkg("sample.isolated");
        type(isolated, "Isolated");
        PackageCompound uncertain = pkg("sample.uncertain");
        TypeCompound uncertainType = type(uncertain, "Uncertain");
        interfaceType(uncertain, "Contract");
        add(uncertain, uncertainType, SymbolRef.unresolved("missing", "Missing"),
                DependencyUsage.FIELD);

        ProviderResult isolatedProvider = provider(List.of(isolated));
        ProviderResult uncertainProvider = provider(List.of(uncertain));

        NotApplicableResult isolatedResult = (NotApplicableResult) measurement(isolatedProvider,
                isolated, BuiltInMetricDefinition.INSTABILITY).result();
        assertEquals(ReasonCode.NO_PACKAGE_DEPENDENCIES, isolatedResult.reason());
        IntervalResult uncertainResult = (IntervalResult) measurement(uncertainProvider,
                uncertain, BuiltInMetricDefinition.INSTABILITY).result();
        assertEquals(BigDecimal.ZERO, uncertainResult.lower());
        assertEquals(BigDecimal.ONE, uncertainResult.upper());
        assertEquals(Set.of(ReasonCode.UNRESOLVED_PACKAGE_TARGET), uncertainResult.reasons());

        NotApplicableResult isolatedDistance = (NotApplicableResult) measurement(isolatedProvider,
                isolated, BuiltInMetricDefinition.DISTANCE).result();
        assertEquals(ReasonCode.NO_PACKAGE_DEPENDENCIES, isolatedDistance.reason());
        IntervalResult uncertainDistance = (IntervalResult) measurement(uncertainProvider,
                uncertain, BuiltInMetricDefinition.DISTANCE).result();
        assertEquals(BigDecimal.ZERO, uncertainDistance.lower());
        assertEquals(0, new BigDecimal("0.5").compareTo(uncertainDistance.upper()));
    }

    private static ProviderResult provider(List<PackageCompound> packages) {
        JdtParser.Config config = new JdtParser.Config();
        return new JavaMetricsProvider(Set.of(BuiltInMetricDefinition.CA,
                BuiltInMetricDefinition.CE, BuiltInMetricDefinition.INSTABILITY,
                BuiltInMetricDefinition.DISTANCE)).measure(
                new AnalysisContext(packages, List.of(), BaselineMetadata.capture(config)));
    }

    private static void assertExactDistance(ProviderResult provider, PackageCompound pkg,
                                            String value) {
        assertResult(provider, pkg, BuiltInMetricDefinition.DISTANCE,
                value, ValueRelation.EXACT);
    }

    private static void assertObservedDistance(ProviderResult provider, PackageCompound pkg,
                                               String value) {
        assertResult(provider, pkg, BuiltInMetricDefinition.DISTANCE,
                value, ValueRelation.OBSERVED_FRAGMENT);
    }

    private static void assertResult(ProviderResult provider, PackageCompound pkg,
                                     BuiltInMetricDefinition definition, String value,
                                     ValueRelation relation) {
        NumericResult result = (NumericResult) measurement(provider, pkg, definition).result();
        assertEquals(0, new BigDecimal(value).compareTo(result.value()));
        assertEquals(relation, result.relation());
    }

    private static void assertExactInstability(ProviderResult provider, PackageCompound pkg,
                                                String value) {
        NumericResult result = (NumericResult) measurement(provider, pkg,
                BuiltInMetricDefinition.INSTABILITY).result();
        assertEquals(0, new BigDecimal(value).compareTo(result.value()));
        assertEquals(ValueRelation.EXACT, result.relation());
    }

    private static void assertObservedInstability(ProviderResult provider, PackageCompound pkg,
                                                   String value) {
        NumericResult result = (NumericResult) measurement(provider, pkg,
                BuiltInMetricDefinition.INSTABILITY).result();
        assertEquals(0, new BigDecimal(value).compareTo(result.value()));
        assertEquals(ValueRelation.OBSERVED_FRAGMENT, result.relation());
        assertEquals(Set.of(ReasonCode.UNRESOLVED_PACKAGE_TARGET), result.reasons());
    }

    private static void assertExact(ProviderResult provider, PackageCompound pkg,
                                    BuiltInMetricDefinition definition, int value) {
        NumericResult result = (NumericResult) measurement(provider, pkg, definition).result();
        assertEquals(value, result.value().intValueExact());
        assertEquals(ValueRelation.EXACT, result.relation());
    }

    private static void assertPartial(ProviderResult provider, PackageCompound pkg,
                                      BuiltInMetricDefinition definition, int value) {
        NumericResult result = (NumericResult) measurement(provider, pkg, definition).result();
        assertEquals(value, result.value().intValueExact());
        assertEquals(ValueRelation.AT_LEAST, result.relation());
        assertEquals(Set.of(ReasonCode.UNRESOLVED_PACKAGE_TARGET), result.reasons());
    }

    private static MetricMeasurement measurement(ProviderResult provider, PackageCompound pkg,
                                                 BuiltInMetricDefinition definition) {
        return provider.measurements().find(new MeasurementKey(definition.id(),
                new SubjectRef(MetricScope.PACKAGE, pkg.symbolId),
                provider.run().providerRunId())).orElseThrow();
    }

    private static PackageCompound pkg(String name) {
        return new PackageCompound("pkg:" + name, "p:" + name, name, name, true);
    }

    private static TypeCompound type(PackageCompound pkg, String name) {
        return type(pkg, name, CompoundKind.CLASS);
    }

    private static TypeCompound interfaceType(PackageCompound pkg, String name) {
        return type(pkg, name, CompoundKind.INTERFACE);
    }

    private static TypeCompound type(PackageCompound pkg, String name, CompoundKind kind) {
        String qualified = pkg.getQualifiedName() + "." + name;
        TypeCompound type = new TypeCompound("type:" + qualified, "symbol:" + qualified,
                kind, name, qualified, pkg, EnumSet.noneOf(Modifier.class),
                NestingKind.TOP_LEVEL, true, null);
        pkg.addChild(type);
        return type;
    }

    private static SymbolRef ref(TypeCompound type) {
        return new SymbolRef(type.symbolId, type.bindingKey, type.getQualifiedName(),
                ((PackageCompound) type.getParent()).getQualifiedName(), type.getName(),
                Origin.EXTERNAL, true, TypeKindHint.CLASS);
    }

    private static void add(PackageCompound pkg, TypeCompound source,
                            SymbolRef target, DependencyUsage usage) {
        pkg.dependencyEdges.add(new DependencyEdge(source.symbolId, target, usage));
    }

    private static PackageCompound packageNamed(List<PackageCompound> packages, String name) {
        return packages.stream().filter(pkg -> pkg.getQualifiedName().equals(name))
                .findFirst().orElseThrow();
    }
}
