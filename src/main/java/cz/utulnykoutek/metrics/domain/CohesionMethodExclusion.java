// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

/** Reasons why a declared method is not a vertex of the primary LCOM4 graph. */
public enum CohesionMethodExclusion {
    STATIC_METHOD,
    NO_BODY,
    TRIVIAL_ACCESSOR
}
