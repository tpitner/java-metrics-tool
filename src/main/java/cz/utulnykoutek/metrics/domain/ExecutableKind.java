// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

/** Kind of executable declaration measured independently for complexity. */
public enum ExecutableKind {
    METHOD,
    CONSTRUCTOR,
    INITIALIZER,
    LAMBDA
}
