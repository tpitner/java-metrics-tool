// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

/**
 * Kind of a {@link Compound}.
 */
public enum CompoundKind {
    /** Package. */
    PACKAGE,
    /** Class. */
    CLASS,
    /** Interface. */
    INTERFACE,
    /** Enum. */
    ENUM,
    /** Record. */
    RECORD,
    /** Annotation type. */
    ANNOTATION
}