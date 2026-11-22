// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.definition;

/** Kind of program element to which a measurement belongs. */
public enum MetricScope {
    PROJECT("project"),
    FILE("file"),
    PACKAGE("package"),
    TYPE("type"),
    METHOD("method"),
    CONSTRUCTOR("constructor"),
    INITIALIZER("initializer"),
    LAMBDA("lambda");

    private final String serializedName;

    MetricScope(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
