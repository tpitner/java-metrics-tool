// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.config.CkProviderConfig;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.ck.CkMetricDefinition;
import cz.utulnykoutek.metrics.metrics.ck.CkMetricsProvider;
import cz.utulnykoutek.metrics.parser.JdtParser;
import cz.utulnykoutek.metrics.parser.PackageMerger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in test against a pinned CK JAR using an explicitly selected CK JVM. */
class CkActualIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void importsRealCkClassAndMethodCsv() throws Exception {
        String configuredJar = System.getProperty("ck.jar");
        Assumptions.assumeTrue(configuredJar != null && Files.isRegularFile(Path.of(configuredJar)),
                "Set -Dck.jar to run the real CK integration test");

        Path sourceRoot = Path.of("src/test/resources/maven-it/small/src/main/java")
                .toAbsolutePath().normalize();
        Path app = sourceRoot.resolve("com/example/demo/App.java");
        Path util = sourceRoot.resolve("com/example/demo/Util.java");
        JdtParser.Config parserConfig = new JdtParser.Config();
        parserConfig.sourcepath = List.of(sourceRoot);
        List<PackageCompound> packages = PackageMerger.merge(List.of(
                (PackageCompound) new JdtParser(parserConfig).parse(app),
                (PackageCompound) new JdtParser(parserConfig).parse(util)));
        AnalysisContext context = new AnalysisContext(
                packages, List.of(app, util), BaselineMetadata.capture(parserConfig));
        Path javaExecutable = ckJavaExecutable();
        CkProviderConfig config = new CkProviderConfig(
                true, Optional.of(Path.of(configuredJar).toAbsolutePath()),
                javaExecutable.toString(), false, 0, true,
                List.of(".git", "target"), Duration.ofMinutes(2), false);

        ProviderResult result = new CkMetricsProvider(
                config, sourceRoot, temporaryDirectory, false).measure(context);

        assertTrue(result.diagnostics().isEmpty(), result.diagnostics().toString());
        assertEquals(5, result.measurements().forMetric(CkMetricDefinition.WMC.id()).size());
        assertEquals(58, result.measurements().size());
    }

    private static Path ckJavaExecutable() {
        String configuredJava = System.getProperty("ck.java");
        assertTrue(configuredJava != null && !configuredJava.isBlank(),
                "Set -Dck.java=/path/to/jdk-21/bin/java when -Dck.jar is set");
        Path executable = Path.of(configuredJava).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(executable) && Files.isExecutable(executable),
                "-Dck.java must point to an executable file: " + executable);
        return executable;
    }
}
