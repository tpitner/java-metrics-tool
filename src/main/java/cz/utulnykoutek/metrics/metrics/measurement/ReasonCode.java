// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.measurement;

/** Machine-readable reason why a measurement is not exact or has no numeric result. */
public enum ReasonCode {
    UNRESOLVED_TYPE,
    UNRESOLVED_SUPERTYPE,
    UNRESOLVED_METHOD_CALL,
    UNRESOLVED_FIELD_REFERENCE,
    INCOMPLETE_CLASSPATH,
    PARSE_ERROR,
    PROVIDER_FAILED,
    PROVIDER_DID_NOT_REPORT_VALUE,
    NO_ELIGIBLE_MEMBERS,
    INTERFACE_OR_ANNOTATION,
    STATIC_UTILITY_TYPE,
    ACCESSORS_ONLY,
    COHESION_EVIDENCE_UNAVAILABLE,
    UNRESOLVED_PACKAGE_TARGET,
    NO_PACKAGE_DEPENDENCIES,
    NO_ELIGIBLE_TYPES,
    EMPTY_TYPE,
    DIVISION_BY_ZERO
}
