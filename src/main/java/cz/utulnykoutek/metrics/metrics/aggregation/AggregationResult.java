// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

/** Result of the primary aggregation over exact {@code OK} measurements. */
public sealed interface AggregationResult
        permits AggregatedStatistics, NoAggregatableValues {}
