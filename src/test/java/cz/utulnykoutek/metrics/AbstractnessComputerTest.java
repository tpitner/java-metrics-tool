// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.CompoundKind;
import cz.utulnykoutek.metrics.domain.Modifier;
import cz.utulnykoutek.metrics.domain.NestingKind;
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
import cz.utulnykoutek.metrics.metrics.measurement.ValueRelation;
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

class AbstractnessComputerTest {
    @Test
    void countsOnlyTopLevelInterfacesAndExplicitlyAbstractClassesAsAbstract() {
        PackageCompound pkg = pkg("sample.mixed");
        add(pkg, CompoundKind.INTERFACE, "Api", NestingKind.TOP_LEVEL);
        add(pkg, CompoundKind.CLASS, "Base", NestingKind.TOP_LEVEL, Modifier.ABSTRACT);
        add(pkg, CompoundKind.CLASS, "Concrete", NestingKind.TOP_LEVEL, Modifier.SEALED);
        add(pkg, CompoundKind.RECORD, "Value", NestingKind.TOP_LEVEL);
        add(pkg, CompoundKind.ENUM, "Mode", NestingKind.TOP_LEVEL);
        add(pkg, CompoundKind.ANNOTATION, "Marker", NestingKind.TOP_LEVEL);
        add(pkg, CompoundKind.INTERFACE, "NestedApi", NestingKind.STATIC_MEMBER);

        MetricMeasurement measurement = measurement(provider(pkg), pkg);
        NumericResult result = (NumericResult) measurement.result();

        assertEquals(0, new BigDecimal("0.4").compareTo(result.value()));
        assertEquals(ValueRelation.EXACT, result.relation());
        assertEquals("5", measurement.attributes().get("eligibleTopLevelTypeCount"));
        assertEquals("2", measurement.attributes().get("abstractTopLevelTypeCount"));
        assertEquals("1", measurement.attributes().get("excludedAnnotationTypeCount"));
        assertEquals("1", measurement.attributes().get("excludedNonTopLevelTypeCount"));
    }

    @Test
    void reportsNotApplicableWhenPackageHasNoEligibleTypes() {
        PackageCompound pkg = pkg("sample.annotations");
        add(pkg, CompoundKind.ANNOTATION, "Marker", NestingKind.TOP_LEVEL);

        NotApplicableResult result = (NotApplicableResult) measurement(provider(pkg), pkg).result();

        assertEquals(ReasonCode.NO_ELIGIBLE_TYPES, result.reason());
    }

    @Test
    void computesAbstractnessFromTheModernTypesCorpus() throws Exception {
        Path sourceRoot = Path.of("validation/cases/modern-types/src/main/java");
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
        PackageCompound pkg = PackageMerger.merge(parsed).getFirst();

        NumericResult result = (NumericResult) measurement(provider(pkg), pkg).result();

        assertEquals(0, new BigDecimal("0.2").compareTo(result.value()));
    }

    private static ProviderResult provider(PackageCompound pkg) {
        JdtParser.Config config = new JdtParser.Config();
        return new JavaMetricsProvider(Set.of(BuiltInMetricDefinition.ABSTRACTNESS)).measure(
                new AnalysisContext(List.of(pkg), List.of(), BaselineMetadata.capture(config)));
    }

    private static MetricMeasurement measurement(ProviderResult provider, PackageCompound pkg) {
        return provider.measurements().find(new MeasurementKey(
                BuiltInMetricDefinition.ABSTRACTNESS.id(),
                new SubjectRef(MetricScope.PACKAGE, pkg.symbolId),
                provider.run().providerRunId())).orElseThrow();
    }

    private static PackageCompound pkg(String name) {
        return new PackageCompound("pkg:" + name, "p:" + name, name, name, true);
    }

    private static void add(PackageCompound pkg, CompoundKind kind, String name,
                            NestingKind nesting, Modifier... modifiers) {
        String qualified = pkg.getQualifiedName() + "." + name;
        EnumSet<Modifier> modifierSet = EnumSet.noneOf(Modifier.class);
        modifierSet.addAll(List.of(modifiers));
        pkg.addChild(new TypeCompound("type:" + qualified, "symbol:" + qualified, kind,
                name, qualified, pkg, modifierSet,
                nesting, true, null));
    }
}
