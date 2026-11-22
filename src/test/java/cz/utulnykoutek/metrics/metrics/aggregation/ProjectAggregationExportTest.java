package cz.utulnykoutek.metrics.metrics.aggregation;

import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricCatalog;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.measurement.MeasurementStore;
import cz.utulnykoutek.metrics.metrics.measurement.MetricMeasurement;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.ReasonCode;
import cz.utulnykoutek.metrics.metrics.measurement.SubjectRef;
import cz.utulnykoutek.metrics.export.ProjectAggregationExporter;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectAggregationExportTest {
    private static final BuiltInMetricDefinition METRIC = BuiltInMetricDefinition.PHYSICAL_LOC;
    private static final String RUN = "java-metrics:test";

    @Test
    void deterministicJsonAndCsvIncludeAggregation() {
        MeasurementStore store = MeasurementStore.builder(MetricCatalog.builder().register(METRIC).build())
                .add(new MetricMeasurement(METRIC.id(), new SubjectRef(MetricScope.TYPE, "one"), RUN,
                        NumericResult.exact(BigDecimal.ONE)))
                .build();

        MetricAggregation aggregation = ProjectMetricAggregator.aggregate(store, METRIC.id(), MetricScope.TYPE, RUN);
        String json1 = ProjectAggregationExporter.toJson(List.of(aggregation));
        String json2 = ProjectAggregationExporter.toJson(List.of(aggregation));
        String csv = ProjectAggregationExporter.toCsv(List.of(aggregation));

        assertTrue(json1.equals(json2));
        assertTrue(json1.contains("\"metricId\":\"java.physical-loc\""));
        assertTrue(csv.contains("primary,sum,1"));
    }
}