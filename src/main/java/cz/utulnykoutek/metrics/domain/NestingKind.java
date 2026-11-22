// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

/**
 * Nesting kind of a declared type.
 */
public enum NestingKind {
    /** Top-level type. */
    TOP_LEVEL,
    /** Static member type. */
    STATIC_MEMBER,
    /** Non-static inner member type. */
    INNER_MEMBER,
    /** Local type. */
    LOCAL,
    /** Anonymous type. */
    ANONYMOUS
}