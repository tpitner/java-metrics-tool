// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.definition;

import java.math.BigDecimal;
import java.util.Objects;

/** Unit of a metric and the numeric representation accepted by that unit. */
public enum MetricUnit {
    COUNT(true),
    LINES(true),
    RATIO(false),
    NORMALIZED_DISTANCE(false);

    private final boolean integral;

    MetricUnit(boolean integral) {
        this.integral = integral;
    }

    public boolean isIntegral() {
        return integral;
    }

    public boolean accepts(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return !integral || value.stripTrailingZeros().scale() <= 0;
    }
}
