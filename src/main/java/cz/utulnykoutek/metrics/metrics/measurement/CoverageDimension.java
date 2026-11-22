// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

/** Kind of input whose resolution coverage is reported for a measurement. */
public enum CoverageDimension {
    TYPE_REFERENCES,
    METHOD_CALLS,
    FIELD_REFERENCES,
    INHERITANCE_EDGES,
    DECLARED_MEMBERS
}
