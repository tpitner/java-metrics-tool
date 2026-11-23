// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.definition;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static cz.utulnykoutek.metrics.metrics.definition.MetricScope.CONSTRUCTOR;
import static cz.utulnykoutek.metrics.metrics.definition.MetricScope.FILE;
import static cz.utulnykoutek.metrics.metrics.definition.MetricScope.INITIALIZER;
import static cz.utulnykoutek.metrics.metrics.definition.MetricScope.LAMBDA;
import static cz.utulnykoutek.metrics.metrics.definition.MetricScope.METHOD;
import static cz.utulnykoutek.metrics.metrics.definition.MetricScope.PACKAGE;
import static cz.utulnykoutek.metrics.metrics.definition.MetricScope.PROJECT;
import static cz.utulnykoutek.metrics.metrics.definition.MetricScope.TYPE;

/** Definitions implemented directly by java-metrics. */
public enum BuiltInMetricDefinition implements MetricDefinition {
    DECLARED_METHODS(MetricName.DECLARED_METHODS, MetricUnit.COUNT,
            scopeSet(TYPE), "t2.declared-methods.v1"),
    DECLARED_CONSTRUCTORS(MetricName.DECLARED_CONSTRUCTORS, MetricUnit.COUNT,
            scopeSet(TYPE), "t2.declared-constructors.v1"),
    DECLARED_FIELDS(MetricName.DECLARED_FIELDS, MetricUnit.COUNT,
            scopeSet(TYPE), "t2.declared-fields.v1"),
    PHYSICAL_LOC(MetricName.PHYSICAL_LOC, MetricUnit.LINES,
            scopeSet(PROJECT, FILE, PACKAGE, TYPE, METHOD, CONSTRUCTOR, INITIALIZER, LAMBDA), "t2.physical-loc.v1"),
    NCLOC(MetricName.NCLOC, MetricUnit.LINES,
            scopeSet(PROJECT, FILE, PACKAGE, TYPE, METHOD, CONSTRUCTOR, INITIALIZER, LAMBDA), "t2.ncloc.v1"),
    PHYSICAL_LOC_EXCLUSIVE(MetricName.PHYSICAL_LOC_EXCLUSIVE, MetricUnit.LINES,
            scopeSet(TYPE), "t2.physical-loc-exclusive.v1"),
    NCLOC_EXCLUSIVE(MetricName.NCLOC_EXCLUSIVE, MetricUnit.LINES,
            scopeSet(TYPE), "t2.ncloc-exclusive.v1"),
    CYCLOMATIC(MetricName.CYCLOMATIC, MetricUnit.COUNT,
            scopeSet(METHOD, CONSTRUCTOR, INITIALIZER, LAMBDA), "t2.cyclomatic.v1"),
    WMC(MetricName.WMC, MetricUnit.COUNT, scopeSet(TYPE), "t2.wmc.v1"),
    DIT(MetricName.DIT, MetricUnit.COUNT, scopeSet(TYPE), "t2.dit.v1"),
    NOC(MetricName.NOC, MetricUnit.COUNT, scopeSet(TYPE), "t2.noc.v1"),
    CBO(MetricName.CBO, MetricUnit.COUNT, scopeSet(TYPE), "t2.cbo.v1"),
    FAN_IN(MetricName.FAN_IN, MetricUnit.COUNT, scopeSet(TYPE), "t2.fan-in.v1"),
    FAN_OUT(MetricName.FAN_OUT, MetricUnit.COUNT, scopeSet(TYPE), "t2.fan-out.v1"),
    LCOM4(MetricName.LCOM4, MetricUnit.COUNT, scopeSet(TYPE), "t2.lcom4.v1"),
    RFC(MetricName.RFC, MetricUnit.COUNT, scopeSet(TYPE), "t2.rfc.v1"),
    CA(MetricName.CA, MetricUnit.COUNT, scopeSet(PACKAGE), "t2.ca.v1"),
    CE(MetricName.CE, MetricUnit.COUNT, scopeSet(PACKAGE), "t2.ce.v1"),
    INSTABILITY(MetricName.INSTABILITY, MetricUnit.RATIO, scopeSet(PACKAGE), "t2.instability.v1"),
    ABSTRACTNESS(MetricName.ABSTRACTNESS, MetricUnit.RATIO, scopeSet(PACKAGE), "t2.abstractness.v1"),
    DISTANCE(MetricName.DISTANCE, MetricUnit.NORMALIZED_DISTANCE, scopeSet(PACKAGE), "t2.distance.v1");

    private final MetricId id;
    private final MetricUnit unit;
    private final Set<MetricScope> supportedScopes;
    private final String specificationKey;

    BuiltInMetricDefinition(MetricName name, MetricUnit unit, Set<MetricScope> supportedScopes,
                            String specificationKey) {
        this.id = new MetricId(MetricNamespace.JAVA, name);
        this.unit = unit;
        this.supportedScopes = supportedScopes;
        this.specificationKey = specificationKey;
    }

    @Override
    public MetricId id() {
        return id;
    }

    @Override
    public Set<MetricScope> supportedScopes() {
        return supportedScopes;
    }

    @Override
    public MetricUnit unit() {
        return unit;
    }

    @Override
    public String specificationKey() {
        return specificationKey;
    }

    private static Set<MetricScope> scopeSet(MetricScope first, MetricScope... rest) {
        return Collections.unmodifiableSet(EnumSet.of(first, rest));
    }
}
