// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.aggregation;

/** Aggregated distribution of available lower or upper endpoints. */
public sealed interface EndpointAggregation
        permits EndpointStatistics, NoEndpointValues {}
