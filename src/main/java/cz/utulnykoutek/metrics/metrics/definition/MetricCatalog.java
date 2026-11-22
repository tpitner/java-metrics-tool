// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.definition;

import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;

import static cz.utulnykoutek.metrics.util.Strings.ensureNonBlank;
import static cz.utulnykoutek.metrics.util.Strings.ensureNonNull;
import static cz.utulnykoutek.metrics.util.Strings.requireNonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/** Immutable registry of metric definitions known to an analysis. */
public final class MetricCatalog {
    private final NavigableMap<MetricId, MetricDefinition> byId;
    private final List<MetricDefinition> definitions;

    private MetricCatalog(Map<MetricId, MetricDefinition> sourceDefinitions) {
        TreeMap<MetricId, MetricDefinition> sorted = new TreeMap<>(sourceDefinitions);
        this.byId = Collections.unmodifiableNavigableMap(sorted);
        this.definitions = List.copyOf(sorted.values());
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<MetricDefinition> find(MetricId id) {
        return Optional.ofNullable(byId.get(requireNonNull(id, "id")));
    }

    public MetricDefinition require(MetricId id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown metric: " + id));
    }

    /** Returns all definitions as an immutable list in {@link MetricId} order. */
    public List<MetricDefinition> definitions() {
        return definitions;
    }

    public static final class Builder {
        private final Map<MetricId, MetricDefinition> definitions = new HashMap<>();

        public Builder register(MetricDefinition definition) {
            ensureNonNull(definition, "definition");
            if (definition.supportedScopes().isEmpty()) {
                throw new IllegalArgumentException("Metric must support at least one scope: " + definition.id());
            }
            ensureNonBlank(definition.specificationKey(), "Metric must have a specification key: " + definition.id());
            MetricDefinition previous = definitions.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate metric definition: " + definition.id());
            }
            return this;
        }

        public Builder registerAll(Collection<? extends MetricDefinition> definitions) {
            definitions.forEach(this::register);
            return this;
        }

        public Builder registerAll(MetricDefinition... definitions) {
            return registerAll(Arrays.asList(definitions));
        }

        public MetricCatalog build() {
            return new MetricCatalog(definitions);
        }
    }
}
