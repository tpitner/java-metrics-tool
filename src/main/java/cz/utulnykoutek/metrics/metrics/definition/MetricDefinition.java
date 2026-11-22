// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.definition;

import java.math.BigDecimal;
import java.util.Set;

/** Complete, versioned semantic definition of one supported metric. */
public interface MetricDefinition {
    MetricId id();

    Set<MetricScope> supportedScopes();

    MetricUnit unit();

    String specificationKey();

    default boolean supports(MetricScope scope) {
        return supportedScopes().contains(scope);
    }

    default void validate(BigDecimal value) {
        if (!unit().accepts(value)) {
            throw new IllegalArgumentException(
                    "Value " + value + " is invalid for " + id() + " with unit " + unit());
        }
    }
}
