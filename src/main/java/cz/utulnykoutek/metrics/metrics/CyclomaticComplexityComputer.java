// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.CyclomaticComplexityEvidence;
import cz.utulnykoutek.metrics.domain.ExecutableKind;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes executable cyclomatic complexity and method-only WMC from parser evidence. */
public final class CyclomaticComplexityComputer implements BuiltInMetricComputer {
    private static final Set<BuiltInMetricDefinition> DEFINITIONS = Set.copyOf(EnumSet.of(
            BuiltInMetricDefinition.CYCLOMATIC,
            BuiltInMetricDefinition.WMC));

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
            throw new IllegalArgumentException("CyclomaticComplexityComputer received an unsupported metric");
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
            long wmc = 0;
            int includedMethods = 0;
            int bodylessMethods = 0;
            for (CyclomaticComplexityEvidence evidence : type.cyclomaticComplexityEvidence) {
                if (requestedDefinitions.contains(BuiltInMetricDefinition.CYCLOMATIC)) {
                    measurements.add(cyclomatic(evidence, providerRunId));
                }
                if (evidence.kind() == ExecutableKind.METHOD) {
                    if (evidence.hasBody()) {
                        wmc += evidence.complexity();
                        includedMethods++;
                    } else {
                        bodylessMethods++;
                    }
                }
            }
            if (requestedDefinitions.contains(BuiltInMetricDefinition.WMC)) {
                Map<String, String> attributes = new LinkedHashMap<>();
                attributes.put("includedMethodCount", Integer.toString(includedMethods));
                attributes.put("bodylessMethodCount", Integer.toString(bodylessMethods));
                measurements.add(new MetricMeasurement(
                        BuiltInMetricDefinition.WMC.id(),
                        new SubjectRef(MetricScope.TYPE, type.symbolId),
                        providerRunId,
                        NumericResult.exact(BigDecimal.valueOf(wmc)),
                        attributes));
            }
        }
        for (Compound child : compound.getChildren()) {
            collect(child, providerRunId, requestedDefinitions, measurements);
        }
    }

    private static MetricMeasurement cyclomatic(
            CyclomaticComplexityEvidence evidence, String providerRunId) {
        return new MetricMeasurement(
                BuiltInMetricDefinition.CYCLOMATIC.id(),
                new SubjectRef(scope(evidence.kind()), evidence.subjectId()),
                providerRunId,
                evidence.hasBody()
                        ? NumericResult.exact(BigDecimal.valueOf(evidence.complexity()))
                        : new NotApplicableResult(ReasonCode.EXECUTABLE_HAS_NO_BODY),
                Map.of("decisionPointCount", Integer.toString(evidence.decisionPoints())));
    }

    private static MetricScope scope(ExecutableKind kind) {
        return switch (kind) {
            case METHOD -> MetricScope.METHOD;
            case CONSTRUCTOR -> MetricScope.CONSTRUCTOR;
            case INITIALIZER -> MetricScope.INITIALIZER;
            case LAMBDA -> MetricScope.LAMBDA;
        };
    }
}
