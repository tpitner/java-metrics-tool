// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics;

import cz.utulnykoutek.metrics.domain.PackageCompound;
import cz.utulnykoutek.metrics.parser.JdtParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class JavaReleaseCompatibilityTest {
    @TempDir
    Path temporaryDirectory;

    @TestFactory
    Stream<DynamicTest> acceptsEverySupportedSourceRelease() {
        return IntStream.rangeClosed(8, 27).mapToObj(release -> DynamicTest.dynamicTest(
                "Java " + release,
                () -> assertParses(release, "final class Release" + release + " {}")));
    }

    @TestFactory
    Stream<DynamicTest> parsesRepresentativeStableLanguageFeatures() {
        Map<Integer, String> sources = new LinkedHashMap<>();
        sources.put(8, """
                import java.util.function.Supplier;
                final class Feature8 { Supplier<String> value = () -> \"ok\"; }
                """);
        sources.put(9, """
                interface Feature9 {
                    private String value() { return \"ok\"; }
                    default String exposed() { return value(); }
                }
                """);
        sources.put(10, """
                final class Feature10 { void run() { var value = \"ok\"; } }
                """);
        sources.put(11, """
                import java.util.function.Function;
                final class Feature11 { Function<String, String> value = (var text) -> text; }
                """);
        sources.put(14, """
                final class Feature14 {
                    int value(int input) { return switch (input) { case 1 -> 1; default -> 0; }; }
                }
                """);
        sources.put(15, """
                final class Feature15 { String value = \"\"\"
                        text
                        \"\"\"; }
                """);
        sources.put(16, """
                record Feature16(String value) {
                    boolean nonempty(Object candidate) {
                        return candidate instanceof String text && !text.isEmpty();
                    }
                }
                """);
        sources.put(17, """
                sealed interface Feature17 permits Feature17Impl {}
                final class Feature17Impl implements Feature17 {}
                """);
        sources.put(21, """
                record Pair(String left, String right) {}
                final class Feature21 {
                    String first(Object value) {
                        return switch (value) {
                            case Pair(String left, String right) -> left;
                            default -> \"\";
                        };
                    }
                }
                """);

        return sources.entrySet().stream().map(entry -> DynamicTest.dynamicTest(
                "Java " + entry.getKey() + " stable features",
                () -> assertParses(entry.getKey(), entry.getValue())));
    }

    private void assertParses(int release, String source) throws Exception {
        Path file = temporaryDirectory.resolve("Release" + release + ".java");
        Files.writeString(file, source);
        JdtParser.Config config = new JdtParser.Config();
        config.release = Integer.toString(release);

        Object parsed = new JdtParser(config).parse(file);

        PackageCompound packageCompound = assertInstanceOf(PackageCompound.class, parsed);
        assertFalse(packageCompound.getChildren().isEmpty());
    }
}
