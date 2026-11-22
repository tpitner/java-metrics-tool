// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.Constructor;
import cz.utulnykoutek.metrics.domain.Field;
import cz.utulnykoutek.metrics.domain.Member;
import cz.utulnykoutek.metrics.domain.Method;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Computes counts of members declared directly in each measured type. */
public final class MemberCountComputer implements BuiltInMetricComputer {
    private static final Set<BuiltInMetricDefinition> DEFINITIONS = Set.copyOf(EnumSet.of(
            BuiltInMetricDefinition.DECLARED_METHODS,
            BuiltInMetricDefinition.DECLARED_CONSTRUCTORS,
            BuiltInMetricDefinition.DECLARED_FIELDS));

    @Override
    public Set<BuiltInMetricDefinition> definitions() {
        return DEFINITIONS;
    }

    @Override
    public List<MetricMeasurement> compute(AnalysisContext context, String providerRunId) {
        return compute(context, providerRunId, DEFINITIONS);
    }

    @Override
    public List<MetricMeasurement> compute(
            AnalysisContext context,
            String providerRunId,
            Set<BuiltInMetricDefinition> requestedDefinitions) {
        if (!DEFINITIONS.containsAll(requestedDefinitions)) {
            throw new IllegalArgumentException("MemberCountComputer received an unsupported metric");
        }
        List<MetricMeasurement> measurements = new ArrayList<>();
        for (Compound pkg : context.packages()) {
            for (Compound child : pkg.getChildren()) {
                collect(child, providerRunId, requestedDefinitions, measurements);
            }
        }
        return List.copyOf(measurements);
    }

    private static void collect(
            Compound compound,
            String providerRunId,
            Set<BuiltInMetricDefinition> requestedDefinitions,
            List<MetricMeasurement> measurements) {
        if (compound instanceof TypeCompound type) {
            long methods = 0;
            long constructors = 0;
            long fields = 0;
            for (Member member : type.getMembers()) {
                if (member instanceof Method) {
                    methods++;
                } else if (member instanceof Constructor) {
                    constructors++;
                } else if (member instanceof Field) {
                    fields++;
                }
            }

            SubjectRef subject = new SubjectRef(
                    MetricScope.TYPE,
                    type.symbolId == null ? type.getId() : type.symbolId);
            addIfRequested(measurements, requestedDefinitions,
                    BuiltInMetricDefinition.DECLARED_METHODS, subject, providerRunId, methods);
            addIfRequested(measurements, requestedDefinitions,
                    BuiltInMetricDefinition.DECLARED_CONSTRUCTORS, subject, providerRunId, constructors);
            addIfRequested(measurements, requestedDefinitions,
                    BuiltInMetricDefinition.DECLARED_FIELDS, subject, providerRunId, fields);
        }

        for (Compound child : compound.getChildren()) {
            collect(child, providerRunId, requestedDefinitions, measurements);
        }
    }

    private static void addIfRequested(
            List<MetricMeasurement> measurements,
            Set<BuiltInMetricDefinition> requestedDefinitions,
            BuiltInMetricDefinition definition,
            SubjectRef subject,
            String providerRunId,
            long value) {
        if (requestedDefinitions.contains(definition)) {
            measurements.add(exact(definition, subject, providerRunId, value));
        }
    }

    private static MetricMeasurement exact(
            BuiltInMetricDefinition definition,
            SubjectRef subject,
            String providerRunId,
            long value) {
        return new MetricMeasurement(
                definition.id(),
                subject,
                providerRunId,
                NumericResult.exact(BigDecimal.valueOf(value)));
    }
}
