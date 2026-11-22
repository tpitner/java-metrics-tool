// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.Compound;
import cz.utulnykoutek.metrics.domain.CompoundKind;
import cz.utulnykoutek.metrics.domain.Modifier;
import cz.utulnykoutek.metrics.domain.NestingKind;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.domain.TypeCompound;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementResult;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes Martin's abstractness ratio for the production packages in the analysis scope. */
public final class AbstractnessComputer implements BuiltInMetricComputer {
    private static final Set<BuiltInMetricDefinition> DEFINITIONS =
            Set.of(BuiltInMetricDefinition.ABSTRACTNESS);

    @Override
    public Set<BuiltInMetricDefinition> definitions() {
        return DEFINITIONS;
    }

    @Override
    public List<MetricMeasurement> compute(AnalysisContext context, String providerRunId) {
        List<MetricMeasurement> measurements = new ArrayList<>();
        for (PackageCompound pkg : context.packages()) {
            Summary summary = summarize(pkg);
            measurements.add(new MetricMeasurement(BuiltInMetricDefinition.ABSTRACTNESS.id(),
                    new SubjectRef(MetricScope.PACKAGE, pkg.symbolId), providerRunId,
                    summary.result(), summary.attributes()));
        }
        return List.copyOf(measurements);
    }

    static Summary summarize(PackageCompound pkg) {
        int eligible = 0;
        int abstractTypes = 0;
        int excludedAnnotations = 0;
        int excludedNonTopLevel = 0;
        for (Compound child : pkg.getChildren()) {
            if (!(child instanceof TypeCompound type)) continue;
            if (type.nesting != NestingKind.TOP_LEVEL) {
                excludedNonTopLevel++;
                continue;
            }
            if (type.getKind() == CompoundKind.ANNOTATION) {
                excludedAnnotations++;
                continue;
            }
            if (!isEligibleKind(type.getKind())) continue;
            eligible++;
            if (isAbstract(type)) abstractTypes++;
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("eligibleTopLevelTypeCount", Integer.toString(eligible));
        attributes.put("abstractTopLevelTypeCount", Integer.toString(abstractTypes));
        attributes.put("excludedAnnotationTypeCount", Integer.toString(excludedAnnotations));
        attributes.put("excludedNonTopLevelTypeCount", Integer.toString(excludedNonTopLevel));
        return new Summary(result(abstractTypes, eligible), Map.copyOf(attributes));
    }

    record Summary(MeasurementResult result, Map<String, String> attributes) {}

    private static MeasurementResult result(int abstractTypes, int eligible) {
        if (eligible == 0) {
            return new NotApplicableResult(ReasonCode.NO_ELIGIBLE_TYPES);
        }
        return NumericResult.exact(BigDecimal.valueOf(abstractTypes)
                .divide(BigDecimal.valueOf(eligible), MathContext.DECIMAL128));
    }

    private static boolean isEligibleKind(CompoundKind kind) {
        return kind == CompoundKind.CLASS
                || kind == CompoundKind.INTERFACE
                || kind == CompoundKind.RECORD
                || kind == CompoundKind.ENUM;
    }

    private static boolean isAbstract(TypeCompound type) {
        return type.getKind() == CompoundKind.INTERFACE
                || type.getKind() == CompoundKind.CLASS
                && type.modifiers.contains(Modifier.ABSTRACT);
    }
}
