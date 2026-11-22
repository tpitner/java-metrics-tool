// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.metrics.definition.MetricDefinition;

import java.util.Set;

/** Produces immutable measurements for one analysis without mutating the domain model. */
public interface MeasurementProvider {
    String id();

    Set<? extends MetricDefinition> definitions();

    ProviderResult measure(AnalysisContext context);
}
