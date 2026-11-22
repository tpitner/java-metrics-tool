// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.domain;

public enum DependencyUsage {
    EXTENDS,
    IMPLEMENTS,
    FIELD,
    METHOD_RETURN,
    METHOD_PARAM,
    THROWS,
    TYPE_PARAM_BOUND,
    LOCAL_VARIABLE,
    INSTANTIATION,
    METHOD_CALL,
    CONSTRUCTOR_CALL,
    FIELD_ACCESS,
    CAST,
    INSTANCEOF,
    CLASS_LITERAL,
    METHOD_REFERENCE,
    CATCH,
    LAMBDA_TARGET,
    OTHER
}
