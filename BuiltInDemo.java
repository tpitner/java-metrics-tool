// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

import cz.utulnykoutek.metrics.analysis.BaselineMetadata;
import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.export.TextExporter;
import cz.utulnykoutek.metrics.export.YamlPreviewConverter;
import cz.utulnykoutek.metrics.metrics.AnalysisContext;
import cz.utulnykoutek.metrics.metrics.JavaMetricsProvider;
import cz.utulnykoutek.metrics.metrics.ProviderResult;
import cz.utulnykoutek.metrics.metrics.definition.BuiltInMetricDefinition;
import cz.utulnykoutek.metrics.metrics.measurement.IntervalResult;
import cz.utulnykoutek.metrics.metrics.measurement.NotApplicableResult;
import cz.utulnykoutek.metrics.metrics.measurement.NumericResult;
import cz.utulnykoutek.metrics.metrics.measurement.UnavailableResult;
import cz.utulnykoutek.metrics.parser.JdtParser;

import java.nio.file.Path;
import java.util.List;

/** End-to-end demonstration of the built-in java-metrics provider. */
public final class BuiltInDemo {
    private BuiltInDemo() {
    }

    public static void main(String[] args) throws Exception {
        Path input = args.length == 0
                ? Path.of("src/test/resources/cz/utulnykoutek/metrics/sample/Nested.java")
                : Path.of(args[0]);

        // 1. Parse the source and capture reproducibility metadata.
        JdtParser.Config parserConfig = new JdtParser.Config();
        BaselineMetadata baseline = BaselineMetadata.capture(parserConfig);
        PackageCompound pkg =
                (PackageCompound) new JdtParser(parserConfig).parse(input);

        // 2. Build the project context shared by measurement providers.
        AnalysisContext context = new AnalysisContext(
                List.of(pkg),
                List.of(input),
                baseline);

        // 3. Inspect the built-in definitions implemented by the Java provider.
        JavaMetricsProvider provider = new JavaMetricsProvider();
        for (BuiltInMetricDefinition definition : provider.definitions()) {
            System.out.printf("definition: %s [%s], spec=%s%n",
                    definition.id(),
                    definition.unit(),
                    definition.specificationKey());
        }

        // 4. Measure without mutating the parsed domain tree.
        ProviderResult providerResult = provider.measure(context);
        providerResult.measurements().all().forEach(measurement -> {
            String outcome = switch (measurement.result()) {
                case NumericResult result -> "value=" + result.value();
                case IntervalResult result ->
                        "interval=[" + result.lower() + ", " + result.upper() + "]";
                case NotApplicableResult result -> "reason=" + result.reason();
                case UnavailableResult result -> "reasons=" + result.reasons();
            };
            System.out.printf("%s  %s  %s  status=%s  run=%s%n",
                    measurement.metricId(),
                    measurement.subject().id(),
                    outcome,
                    measurement.status(),
                    measurement.providerRunId());
        });

        // 5. Render text and ANSI preview from the same MeasurementStore.
        System.out.println();
        System.out.println(TextExporter.render(pkg, providerResult));
        System.out.println(YamlPreviewConverter.render(pkg, providerResult));
    }
}
