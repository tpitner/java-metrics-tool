// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

/**
 * Java modifier flags used in the domain model.
 */
public enum Modifier {
    /** {@code public}. */
    PUBLIC,
    /** {@code protected}. */
    PROTECTED,
    /** {@code private}. */
    PRIVATE,
    /** {@code static}. */
    STATIC,
    /** {@code final}. */
    FINAL,
    /** {@code abstract}. */
    ABSTRACT,
    /** {@code sealed}. */
    SEALED,
    /** {@code non-sealed}. */
    NON_SEALED,
    /** {@code default}. */
    DEFAULT,
    /** {@code strictfp}. */
    STRICTFP,
    /** {@code transitive}. */
    TRANSITIVE,
    /** {@code synchronized}. */
    SYNCHRONIZED,
    /** {@code volatile}. */
    VOLATILE,
    /** {@code transient}. */
    TRANSIENT,
    /** {@code native}. */
    NATIVE
}