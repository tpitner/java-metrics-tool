// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricCatalog;
import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStore;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Measurement provider for metrics implemented directly by java-metrics. */
public final class JavaMetricsProvider implements MeasurementProvider {
    public static final String ID = "java-metrics";

    private final List<BuiltInMetricComputer> computers;
    private final Set<BuiltInMetricDefinition> definitions;

    public JavaMetricsProvider() {
        this(List.of(new MemberCountComputer(), new SourceLineMetricsComputer(),
                new CyclomaticComplexityComputer(), new Lcom4Computer(),
                new MartinCouplingComputer(), new AbstractnessComputer()));
    }

    public JavaMetricsProvider(Set<BuiltInMetricDefinition> selectedDefinitions) {
        this(List.of(new MemberCountComputer(), new SourceLineMetricsComputer(),
                        new CyclomaticComplexityComputer(), new Lcom4Computer(),
                        new MartinCouplingComputer(),
                        new AbstractnessComputer()),
                selectedDefinitions);
    }

    public JavaMetricsProvider(List<? extends BuiltInMetricComputer> computers) {
        this(computers, null);
    }

    private JavaMetricsProvider(
            List<? extends BuiltInMetricComputer> computers,
            Set<BuiltInMetricDefinition> selectedDefinitions) {
        this.computers = List.copyOf(Objects.requireNonNull(computers, "computers"));
        EnumSet<BuiltInMetricDefinition> available = EnumSet.noneOf(BuiltInMetricDefinition.class);
        for (BuiltInMetricComputer computer : this.computers) {
            Objects.requireNonNull(computer, "computer");
            for (BuiltInMetricDefinition definition : computer.definitions()) {
                if (!available.add(definition)) {
                    throw new IllegalArgumentException(
                            "Built-in metric has more than one computer: " + definition.id());
                }
            }
        }
        EnumSet<BuiltInMetricDefinition> selected = selectedDefinitions == null
                ? available.clone()
                : selectedDefinitions.isEmpty()
                        ? EnumSet.noneOf(BuiltInMetricDefinition.class)
                        : EnumSet.copyOf(selectedDefinitions);
        if (!available.containsAll(selected)) {
            selected.removeAll(available);
            throw new IllegalArgumentException("No computer implements selected metrics: " + selected);
        }
        this.definitions = Collections.unmodifiableSet(selected);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Set<BuiltInMetricDefinition> definitions() {
        return definitions;
    }

    @Override
    public ProviderResult measure(AnalysisContext context) {
        Objects.requireNonNull(context, "context");
        String version = context.baselineMetadata().toolVersion;
        String providerRunId = ID + ":" + version;
        String metricIds = definitions.stream()
                .map(definition -> definition.id().serializedName())
                .sorted()
                .collect(java.util.stream.Collectors.joining(","));
        ProviderRunMetadata run = new ProviderRunMetadata(
                ID, providerRunId, version, Map.of("metrics", metricIds));

        MetricCatalog catalog = MetricCatalog.builder().registerAll(definitions).build();
        MeasurementStore.Builder store = MeasurementStore.builder(catalog);
        Set<MetricId> supportedIds = new HashSet<>();
        definitions.forEach(definition -> supportedIds.add(definition.id()));

        for (BuiltInMetricComputer computer : computers) {
            EnumSet<BuiltInMetricDefinition> requested = EnumSet.noneOf(BuiltInMetricDefinition.class);
            requested.addAll(computer.definitions());
            requested.retainAll(definitions);
            if (requested.isEmpty()) continue;
            List<MetricMeasurement> computed = new ArrayList<>(
                    computer.compute(context, providerRunId, Collections.unmodifiableSet(requested)));
            Set<MetricId> computerIds = new HashSet<>();
            computer.definitions().forEach(definition -> computerIds.add(definition.id()));
            for (MetricMeasurement measurement : computed) {
                if (!computerIds.contains(measurement.metricId())) {
                    throw new IllegalStateException(
                            "Computer returned an undeclared metric: " + measurement.metricId());
                }
                if (!providerRunId.equals(measurement.providerRunId())) {
                    throw new IllegalStateException(
                            "Computer returned a measurement for a different provider run");
                }
            }
            store.addAll(computed.stream()
                    .filter(measurement -> supportedIds.contains(measurement.metricId()))
                    .toList());
        }

        return new ProviderResult(run, store.build(), List.of());
    }
}
