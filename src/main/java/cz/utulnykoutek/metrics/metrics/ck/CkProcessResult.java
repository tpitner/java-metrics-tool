// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.ck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/** Successful isolated CK invocation and its raw artifacts. */
public record CkProcessResult(
        List<String> command,
        Path outputDirectory,
        Path classCsv,
        Path methodCsv,
        String stdout,
        String stderr,
        Duration duration,
        String jarSha256,
        String ckVersion,
        String javaVersion,
        boolean retained) implements AutoCloseable {

    public CkProcessResult {
        command = List.copyOf(command);
    }

    @Override
    public void close() {
        if (retained || !Files.exists(outputDirectory)) return;
        try (var paths = Files.walk(outputDirectory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new CkProviderException("Cannot remove temporary CK output " + outputDirectory, e);
        }
    }
}
