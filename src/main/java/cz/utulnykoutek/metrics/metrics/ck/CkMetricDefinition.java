// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.ck;

import cz.utulnykoutek.metrics.metrics.definition.MetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import cz.utulnykoutek.metrics.metrics.definition.MetricName;
import cz.utulnykoutek.metrics.metrics.definition.MetricNamespace;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.definition.MetricUnit;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/** CK metrics imported in the first frozen class/method CSV adapter. */
public enum CkMetricDefinition implements MetricDefinition {
    CBO(MetricName.CBO, MetricUnit.COUNT, scopes(), "cbo", "cbo"),
    CBO_MODIFIED(MetricName.CBO_MODIFIED, MetricUnit.COUNT, scopes(), "cboModified", "cboModified"),
    FAN_IN(MetricName.FAN_IN, MetricUnit.COUNT, scopes(), "fanin", "fanin"),
    FAN_OUT(MetricName.FAN_OUT, MetricUnit.COUNT, scopes(), "fanout", "fanout"),
    WMC(MetricName.WMC, MetricUnit.COUNT, scopes(), "wmc", "wmc"),
    DIT(MetricName.DIT, MetricUnit.COUNT, typeScope(), "dit", null),
    NOC(MetricName.NOC, MetricUnit.COUNT, typeScope(), "noc", null),
    RFC(MetricName.RFC, MetricUnit.COUNT, scopes(), "rfc", "rfc"),
    LCOM(MetricName.LCOM, MetricUnit.COUNT, typeScope(), "lcom", null),
    LCOM_STAR(MetricName.LCOM_STAR, MetricUnit.RATIO, typeScope(), "lcom*", null),
    TCC(MetricName.TCC, MetricUnit.RATIO, typeScope(), "tcc", null),
    LCC(MetricName.LCC, MetricUnit.RATIO, typeScope(), "lcc", null),
    METHODS(MetricName.METHODS, MetricUnit.COUNT, typeScope(), "totalMethodsQty", null),
    PUBLIC_METHODS(MetricName.PUBLIC_METHODS, MetricUnit.COUNT, typeScope(), "publicMethodsQty", null),
    FIELDS(MetricName.FIELDS, MetricUnit.COUNT, typeScope(), "totalFieldsQty", null),
    PUBLIC_FIELDS(MetricName.PUBLIC_FIELDS, MetricUnit.COUNT, typeScope(), "publicFieldsQty", null),
    LOC(MetricName.LOC, MetricUnit.LINES, scopes(), "loc", "loc"),
    PARAMETERS(MetricName.PARAMETERS, MetricUnit.COUNT, memberScopes(), null, "parametersQty");

    private final MetricId id;
    private final MetricUnit unit;
    private final Set<MetricScope> scopes;
    private final String classColumn;
    private final String methodColumn;

    CkMetricDefinition(
            MetricName name,
            MetricUnit unit,
            Set<MetricScope> scopes,
            String classColumn,
            String methodColumn) {
        this.id = new MetricId(MetricNamespace.CK, name);
        this.unit = unit;
        this.scopes = scopes;
        this.classColumn = classColumn;
        this.methodColumn = methodColumn;
    }

    @Override public MetricId id() { return id; }
    @Override public Set<MetricScope> supportedScopes() { return scopes; }
    @Override public MetricUnit unit() { return unit; }
    @Override public String specificationKey() {
        return "ck." + CkCsvSchema.UPSTREAM_REVISION + "." + id.name().serializedName() + ".v1";
    }

    public Optional<String> classColumn() { return Optional.ofNullable(classColumn); }
    public Optional<String> methodColumn() { return Optional.ofNullable(methodColumn); }

    @Override
    public void validate(BigDecimal value) {
        MetricDefinition.super.validate(value);
        if (value.signum() < 0) throw new IllegalArgumentException("CK metric must not be negative: " + id);
        if (unit == MetricUnit.RATIO && value.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("CK ratio must be between zero and one: " + id);
        }
    }

    private static Set<MetricScope> typeScope() {
        return Set.of(MetricScope.TYPE);
    }

    private static Set<MetricScope> memberScopes() {
        return Set.of(MetricScope.METHOD, MetricScope.CONSTRUCTOR);
    }

    private static Set<MetricScope> scopes() {
        return Set.of(MetricScope.TYPE, MetricScope.METHOD, MetricScope.CONSTRUCTOR);
    }
}
