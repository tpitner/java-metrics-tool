// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.metrics.ck;

import cz.utulnykoutek.metrics.config.CkProviderConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

/** Runs the standalone CK JAR in a separate process and captures all raw artifacts. */
public final class CkProcessRunner {
    public CkProcessResult run(
            CkProviderConfig config,
            Path projectDirectory,
            Path artifactDirectory,
            boolean retainArtifacts) {
        Path jar = config.jar().orElseThrow(() ->
                new CkProviderException("CK JAR is required"));
        if (!Files.isRegularFile(jar) || !Files.isReadable(jar)) {
            throw new CkProviderException("CK JAR is not a readable file: " + jar);
        }
        Path project = projectDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(project)) {
            throw new CkProviderException("CK project directory does not exist: " + project);
        }

        long waitMillis = timeoutMillis(config.timeout());
        Path output = createOutputDirectory(artifactDirectory, retainArtifacts);
        Path stdoutFile = output.resolve("stdout.log");
        Path stderrFile = output.resolve("stderr.log");
        List<String> command = command(config, jar, project, output);
        Instant started = Instant.now();
        boolean success = false;
        try {
            Process process = new ProcessBuilder(command)
                    .directory(project.toFile())
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile())
                    .start();
            boolean completed = process.waitFor(waitMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
                throw new CkProviderException("CK timed out after " + config.timeout());
            }
            String stdout = read(stdoutFile);
            String stderr = read(stderrFile);
            if (process.exitValue() != 0) {
                throw new CkProviderException("CK exited with code " + process.exitValue()
                        + diagnosticSuffix(stdout, stderr));
            }
            Path classCsv = output.resolve("class.csv");
            Path methodCsv = output.resolve("method.csv");
            if (!Files.isRegularFile(classCsv) || !Files.isRegularFile(methodCsv)) {
                throw new CkProviderException("CK completed without class.csv and method.csv"
                        + diagnosticSuffix(stdout, stderr));
            }
            CkProcessResult result = new CkProcessResult(
                    command,
                    output,
                    classCsv,
                    methodCsv,
                    stdout,
                    stderr,
                    Duration.between(started, Instant.now()),
                    sha256(jar),
                    readCkVersion(jar),
                    probeJavaVersion(config.javaExecutable(), output),
                    retainArtifacts);
            success = true;
            return result;
        } catch (CkProviderException e) {
            throw e;
        } catch (IOException e) {
            throw new CkProviderException("Cannot start CK process: " + command, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CkProviderException("Interrupted while waiting for CK", e);
        } finally {
            if (!success) {
                cleanupAfterFailure(output, retainArtifacts);
            }
        }
    }

    private static List<String> command(
            CkProviderConfig config,
            Path jar,
            Path project,
            Path output) {
        List<String> command = new ArrayList<>();
        command.add(config.javaExecutable());
        command.add("-jar");
        command.add(jar.toAbsolutePath().normalize().toString());
        command.add(project.toString());
        command.add(Boolean.toString(config.useJars()));
        command.add(Integer.toString(config.maxFilesPerPartition()));
        command.add(Boolean.toString(config.variablesAndFields()));
        command.add(output.toString());
        command.addAll(config.ignoredDirectories());
        return List.copyOf(command);
    }

    private static Path createOutputDirectory(Path artifactDirectory, boolean retain) {
        try {
            if (retain) {
                Path root = artifactDirectory.toAbsolutePath().normalize();
                Files.createDirectories(root);
                return Files.createTempDirectory(root, "ck-");
            }
            return Files.createTempDirectory("java-metrics-ck-");
        } catch (IOException e) {
            throw new CkProviderException("Cannot create CK output directory", e);
        }
    }

    private static long timeoutMillis(Duration timeout) {
        try {
            return timeout.toMillis();
        } catch (ArithmeticException e) {
            throw new CkProviderException("CK timeout is too large: " + timeout, e);
        }
    }

    private static String read(Path file) throws IOException {
        return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
    }

    private static String diagnosticSuffix(String stdout, String stderr) {
        String combined = (stderr + System.lineSeparator() + stdout).trim();
        if (combined.isEmpty()) return "";
        int limit = Math.min(combined.length(), 4_000);
        return ": " + combined.substring(0, limit);
    }

    private static String sha256(Path file) {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int read; (read = input.read(buffer)) >= 0;) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new CkProviderException("Cannot hash CK JAR " + file, e);
        }
    }

    private static String readCkVersion(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            var entry = jarFile.getJarEntry("META-INF/maven/com.github.mauricioaniche/ck/pom.properties");
            if (entry == null) return CkCsvSchema.UPSTREAM_VERSION;
            Properties properties = new Properties();
            try (InputStream input = jarFile.getInputStream(entry)) {
                properties.load(input);
            }
            return properties.getProperty("version", CkCsvSchema.UPSTREAM_VERSION);
        } catch (IOException e) {
            throw new CkProviderException("Cannot read CK version from " + jar, e);
        }
    }

    private static String probeJavaVersion(String executable, Path output) {
        Path versionFile = output.resolve("java-version.log");
        try {
            Process process = new ProcessBuilder(executable, "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(versionFile.toFile())
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "unavailable (timeout)";
            }
            return read(versionFile).trim();
        } catch (IOException e) {
            return "unavailable (" + e.getMessage() + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unavailable (interrupted)";
        }
    }

    private static void cleanupAfterFailure(Path output, boolean retained) {
        if (retained) return;
        new CkProcessResult(List.of(), output, output, output, "", "", Duration.ZERO,
                "", "", "", false).close();
    }
}
