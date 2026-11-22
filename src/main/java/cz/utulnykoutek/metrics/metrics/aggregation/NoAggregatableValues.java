// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

/** No exact {@code OK} value exists; the primary aggregation is not applicable. */
public record NoAggregatableValues() implements AggregationResult {}
