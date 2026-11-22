// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.definition;

import java.util.Arrays;

/** Provider-independent name of a metric concept. */
public enum MetricName {
    LOC("loc"),
    PHYSICAL_LOC("physical-loc"),
    PHYSICAL_LOC_EXCLUSIVE("physical-loc-exclusive"),
    NCLOC("ncloc"),
    NCLOC_EXCLUSIVE("ncloc-exclusive"),
    CYCLOMATIC("cyclomatic"),
    NPATH("npath"),
    PARAMETERS("parameters"),
    FIELDS("fields"),
    DECLARED_FIELDS("declared-fields"),
    PUBLIC_FIELDS("public-fields"),
    METHODS("methods"),
    DECLARED_METHODS("declared-methods"),
    DECLARED_CONSTRUCTORS("declared-constructors"),
    PUBLIC_METHODS("public-methods"),
    WMC("wmc"),
    DIT("dit"),
    NOC("noc"),
    CBO("cbo"),
    CBO_MODIFIED("cbo-modified"),
    FAN_IN("fan-in"),
    FAN_OUT("fan-out"),
    LCOM("lcom"),
    LCOM_STAR("lcom-star"),
    LCOM4("lcom4"),
    TCC("tcc"),
    LCC("lcc"),
    RFC("rfc"),
    CA("ca"),
    CE("ce"),
    INSTABILITY("instability"),
    ABSTRACTNESS("abstractness"),
    DISTANCE("distance");

    private final String serializedName;

    MetricName(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static MetricName fromSerializedName(String value) {
        return Arrays.stream(values())
                .filter(name -> name.serializedName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown metric name: " + value));
    }
}
