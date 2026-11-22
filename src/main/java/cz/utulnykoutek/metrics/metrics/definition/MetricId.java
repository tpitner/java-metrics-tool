// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.definition;

import java.util.Objects;

/** Stable, structured identifier of a metric definition. */
public record MetricId(MetricNamespace namespace, MetricName name) implements Comparable<MetricId> {

    public MetricId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(name, "name");
    }

    public String serializedName() {
        return namespace.serializedName() + "." + name.serializedName();
    }

    public static MetricId parse(String value) {
        Objects.requireNonNull(value, "value");
        int separator = value.indexOf('.');
        if (separator <= 0 || separator != value.lastIndexOf('.') || separator == value.length() - 1) {
            throw new IllegalArgumentException("Metric ID must have the form <namespace>.<name>: " + value);
        }
        return new MetricId(
                MetricNamespace.fromSerializedName(value.substring(0, separator)),
                MetricName.fromSerializedName(value.substring(separator + 1)));
    }

    @Override
    public int compareTo(MetricId other) {
        Objects.requireNonNull(other, "other");
        int namespaceComparison = namespace.serializedName()
                .compareTo(other.namespace.serializedName());
        if (namespaceComparison != 0) {
            return namespaceComparison;
        }
        return name.serializedName().compareTo(other.name.serializedName());
    }

    @Override
    public String toString() {
        return serializedName();
    }
}
