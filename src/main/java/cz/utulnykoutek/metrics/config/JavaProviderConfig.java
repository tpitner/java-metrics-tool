// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.config;

import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Configuration of metrics implemented directly by java-metrics. */
public record JavaProviderConfig(
        boolean enabled,
        Set<BuiltInMetricDefinition> metrics) {

    public JavaProviderConfig {
        Objects.requireNonNull(metrics, "metrics");
        metrics = metrics.isEmpty()
                ? Set.of()
                : Set.copyOf(metrics);
    }
}
