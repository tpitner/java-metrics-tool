// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.definition.MetricCatalog;
import cz.utulnykoutek.metrics.metrics.definition.MetricId;
import cz.utulnykoutek.metrics.metrics.definition.MetricName;
import cz.utulnykoutek.metrics.metrics.definition.MetricNamespace;
import cz.utulnykoutek.metrics.metrics.definition.MetricScope;
import cz.utulnykoutek.metrics.metrics.definition.MetricUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricDefinitionTest {

    @Test
    void parsesAndOrdersStructuredMetricIdsLexicographically() {
        MetricId id = MetricId.parse("java.dit");

        assertEquals(new MetricId(MetricNamespace.JAVA, MetricName.DIT), id);
        assertEquals("java.dit", id.serializedName());
        assertTrue(new MetricId(MetricNamespace.CK, MetricName.WMC).compareTo(id) < 0);
        assertTrue(new MetricId(MetricNamespace.JAVA, MetricName.CBO).compareTo(id) < 0);
        assertThrows(IllegalArgumentException.class, () -> MetricId.parse("java"));
        assertThrows(IllegalArgumentException.class, () -> MetricId.parse("java.unknown"));
    }

    @Test
    void catalogIsImmutableOrderedAndRejectsDuplicates() {
        MetricCatalog catalog = MetricCatalog.builder()
                .register(BuiltInMetricDefinition.WMC)
                .register(BuiltInMetricDefinition.DIT)
                .build();

        assertEquals(BuiltInMetricDefinition.DIT, catalog.definitions().getFirst());
        assertSame(BuiltInMetricDefinition.WMC, catalog.require(BuiltInMetricDefinition.WMC.id()));
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.definitions().add(BuiltInMetricDefinition.CBO));
        assertThrows(IllegalArgumentException.class, () -> MetricCatalog.builder()
                .register(BuiltInMetricDefinition.DIT)
                .register(BuiltInMetricDefinition.DIT));
    }

    @Test
    void unitDeterminesWhetherFractionsAreAccepted() {
        assertTrue(MetricUnit.COUNT.accepts(new BigDecimal("2")));
        assertTrue(MetricUnit.COUNT.accepts(new BigDecimal("2.0")));
        assertFalse(MetricUnit.COUNT.accepts(new BigDecimal("2.5")));
        assertTrue(MetricUnit.RATIO.accepts(new BigDecimal("0.25")));
        assertTrue(BuiltInMetricDefinition.DIT.supports(MetricScope.TYPE));
        assertFalse(BuiltInMetricDefinition.DIT.supports(MetricScope.PACKAGE));
    }
}
