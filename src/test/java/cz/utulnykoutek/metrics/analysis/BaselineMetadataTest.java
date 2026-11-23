// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.analysis;

import cz.utulnykoutek.metrics.parser.JdtParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaselineMetadataTest {
    @Test
    void derivesToolVersionFromMavenBuildMetadata() {
        String projectVersion = System.getProperty("javaMetricsProjectVersion");

        BaselineMetadata metadata = BaselineMetadata.capture(new JdtParser.Config());

        assertEquals(projectVersion, metadata.toolVersion);
    }
}
