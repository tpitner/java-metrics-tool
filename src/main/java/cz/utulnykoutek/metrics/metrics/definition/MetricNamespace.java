// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.definition;

import java.util.Arrays;

/** Identifies the specification family from which a metric originates. */
public enum MetricNamespace {
    JAVA("java"),
    CK("ck"),
    CKJM("ckjm"),
    PMD("pmd"),
    CHECKSTYLE("checkstyle"),
    DESIGNITE("designite");

    private final String serializedName;

    MetricNamespace(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MetricNamespace fromSerializedName(String value) {
        return Arrays.stream(values())
                .filter(namespace -> namespace.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown metric namespace: " + value));
    }
}
