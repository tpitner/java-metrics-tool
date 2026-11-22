// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics;

import cz.utulnykoutek.metrics.domain.Origin;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.IntervalResult;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementResult;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.metrics.measurement.ValueRelation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Computes Martin's internal package coupling and the derived instability. */
public final class MartinCouplingComputer implements BuiltInMetricComputer {
    private static final Set<BuiltInMetricDefinition> DEFINITIONS = Set.of(
            BuiltInMetricDefinition.CA,
            BuiltInMetricDefinition.CE,
            BuiltInMetricDefinition.INSTABILITY,
            BuiltInMetricDefinition.DISTANCE);

    @Override public Set<BuiltInMetricDefinition> definitions() {
        return DEFINITIONS;
    }

    @Override
    public List<MetricMeasurement> compute(AnalysisContext context, String providerRunId) {
        PackageDependencyGraph graph = PackageDependencyGraphBuilder.build(context.packages());
        List<MetricMeasurement> result = new ArrayList<>();
        for (PackageCompound pkg : graph.packages()) {
            int ca = graph.incomingInternal(pkg).size();
            int ce = graph.outgoing(pkg, Origin.INTERNAL).size();
            int unresolvedOutgoing = graph.unresolvedOutgoingCount(pkg);
            int unresolvedPotentialIncoming = graph.packages().stream()
                    .filter(other -> other != pkg)
                    .mapToInt(graph::unresolvedOutgoingCount)
                    .sum();

            Map<String, String> attributes = attributes(
                    graph, pkg, ca, ce, unresolvedOutgoing, unresolvedPotentialIncoming);
            SubjectRef subject = new SubjectRef(MetricScope.PACKAGE, pkg.symbolId);
            result.add(new MetricMeasurement(BuiltInMetricDefinition.CA.id(), subject,
                    providerRunId, numeric(ca, unresolvedPotentialIncoming), attributes));
            result.add(new MetricMeasurement(BuiltInMetricDefinition.CE.id(), subject,
                    providerRunId, numeric(ce, unresolvedOutgoing), attributes));
            MeasurementResult instability = instability(ca, ce,
                    unresolvedOutgoing + unresolvedPotentialIncoming > 0);
            result.add(new MetricMeasurement(BuiltInMetricDefinition.INSTABILITY.id(), subject,
                    providerRunId, instability, attributes));
            AbstractnessComputer.Summary abstractness = AbstractnessComputer.summarize(pkg);
            Map<String, String> distanceAttributes = new LinkedHashMap<>(attributes);
            distanceAttributes.putAll(abstractness.attributes());
            result.add(new MetricMeasurement(BuiltInMetricDefinition.DISTANCE.id(), subject,
                    providerRunId, distance(abstractness.result(), instability),
                    distanceAttributes));
        }
        return List.copyOf(result);
    }

    private static MeasurementResult distance(
            MeasurementResult abstractness, MeasurementResult instability) {
        if (abstractness instanceof NotApplicableResult notApplicable) return notApplicable;
        if (instability instanceof NotApplicableResult notApplicable) return notApplicable;
        NumericResult abstractValue = (NumericResult) abstractness;
        if (instability instanceof IntervalResult interval) {
            BigDecimal lowerEndpoint = distance(abstractValue.value(), interval.lower());
            BigDecimal upperEndpoint = distance(abstractValue.value(), interval.upper());
            BigDecimal pivot = BigDecimal.ONE.subtract(abstractValue.value());
            BigDecimal lower = pivot.compareTo(interval.lower()) >= 0
                    && pivot.compareTo(interval.upper()) <= 0
                    ? BigDecimal.ZERO : lowerEndpoint.min(upperEndpoint);
            BigDecimal upper = lowerEndpoint.max(upperEndpoint);
            return new IntervalResult(lower, upper, interval.reasons(), interval.coverage());
        }

        NumericResult instabilityValue = (NumericResult) instability;
        BigDecimal value = distance(abstractValue.value(), instabilityValue.value());
        if (instabilityValue.relation() == ValueRelation.EXACT) {
            return NumericResult.exact(value);
        }
        Set<ReasonCode> reasons = new HashSet<>(abstractValue.reasons());
        reasons.addAll(instabilityValue.reasons());
        return new NumericResult(value, ValueRelation.OBSERVED_FRAGMENT,
                reasons, instabilityValue.coverage());
    }

    private static BigDecimal distance(BigDecimal abstractness, BigDecimal instability) {
        return abstractness.add(instability).subtract(BigDecimal.ONE).abs();
    }

    private static MeasurementResult instability(int ca, int ce, boolean incomplete) {
        int denominator = ca + ce;
        if (denominator == 0) {
            return incomplete
                    ? new IntervalResult(BigDecimal.ZERO, BigDecimal.ONE,
                    Set.of(ReasonCode.UNRESOLVED_PACKAGE_TARGET), List.of())
                    : new NotApplicableResult(ReasonCode.NO_PACKAGE_DEPENDENCIES);
        }

        BigDecimal value = BigDecimal.valueOf(ce)
                .divide(BigDecimal.valueOf(denominator), MathContext.DECIMAL128);
        return incomplete
                ? new NumericResult(value, ValueRelation.OBSERVED_FRAGMENT,
                Set.of(ReasonCode.UNRESOLVED_PACKAGE_TARGET), List.of())
                : NumericResult.exact(value);
    }

    private static NumericResult numeric(int observed, int unresolvedRelevant) {
        BigDecimal value = BigDecimal.valueOf(observed);
        return unresolvedRelevant == 0
                ? NumericResult.exact(value)
                : NumericResult.atLeast(value,
                        Set.of(ReasonCode.UNRESOLVED_PACKAGE_TARGET), List.of());
    }

    private static Map<String, String> attributes(
            PackageDependencyGraph graph,
            PackageCompound pkg,
            int ca,
            int ce,
            int unresolvedOutgoing,
            int unresolvedPotentialIncoming) {
        int external = graph.outgoing(pkg, Origin.EXTERNAL).size();
        int jdk = graph.outgoing(pkg, Origin.JDK).size();
        Map<String, String> result = new LinkedHashMap<>();
        result.put("internalIncomingPackageCount", Integer.toString(ca));
        result.put("internalOutgoingPackageCount", Integer.toString(ce));
        result.put("externalOutgoingPackageCount", Integer.toString(external));
        result.put("jdkOutgoingPackageCount", Integer.toString(jdk));
        result.put("totalResolvedOutgoingPackageCount", Integer.toString(ce + external + jdk));
        result.put("unresolvedOutgoingDependencyCount", Integer.toString(unresolvedOutgoing));
        result.put("potentialUnresolvedIncomingDependencyCount",
                Integer.toString(unresolvedPotentialIncoming));
        return result;
    }
}
