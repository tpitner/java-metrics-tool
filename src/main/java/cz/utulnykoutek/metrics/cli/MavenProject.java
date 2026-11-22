// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 Tomáš Pitner

package cz.utulnykoutek.metrics.cli;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Derives source roots, classpath and release level from a Maven {@code pom.xml}.
 */
public final class MavenProject {

    private MavenProject() {}

    public static final class Resolved {
        public final List<Path> sourceRoots = new ArrayList<>();
        public final List<Path> classpath = new ArrayList<>();
        public String release;
    }

    public static Resolved resolve(Path pom, boolean includeTests, boolean compile)
            throws IOException, InterruptedException {
        if (pom == null || !Files.isRegularFile(pom)) {
            throw new IOException("pom.xml not found: " + pom);
        }
        Path baseDir = pom.toAbsolutePath().getParent();
        Document doc = parse(pom);
        Properties props = properties(doc);

        Resolved r = new Resolved();
        r.release = release(doc, props);

        // source roots
        String src = text(doc, "build/sourceDirectory");
        Path mainSrc = src == null ? baseDir.resolve("src/main/java") : baseDir.resolve(src);
        if (Files.isDirectory(mainSrc)) r.sourceRoots.add(mainSrc);
        if (includeTests) {
            String testSrc = text(doc, "build/testSourceDirectory");
            Path testSrcPath = testSrc == null ? baseDir.resolve("src/test/java") : baseDir.resolve(testSrc);
            if (Files.isDirectory(testSrcPath)) r.sourceRoots.add(testSrcPath);
        }

        // optional compile to populate target/classes
        if (compile) {
            runMaven(baseDir, pom, "compile");
            if (includeTests) runMaven(baseDir, pom, "test-compile");
        }

        // project classes
        Path classes = baseDir.resolve("target/classes");
        if (Files.isDirectory(classes)) r.classpath.add(classes);
        if (includeTests) {
            Path testClasses = baseDir.resolve("target/test-classes");
            if (Files.isDirectory(testClasses)) r.classpath.add(testClasses);
        }

        // dependency classpath via Maven
        Path cpFile = Files.createTempFile("java-metrics-cp-", ".txt");
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(mavenExecutable());
            cmd.add("-q");
            cmd.add("-f");
            cmd.add(pom.toString());
            cmd.add("dependency:build-classpath");
            cmd.add("-Dmdep.outputFile=" + cpFile);
            cmd.add("-Dmdep.includeScope=" + (includeTests ? "test" : "compile"));
            runProcess(baseDir, cmd);
            String cp = Files.readString(cpFile).trim();
            if (!cp.isBlank()) {
                for (String part : cp.split(File.pathSeparator)) {
                    if (!part.isBlank()) r.classpath.add(Paths.get(part));
                }
            }
        } finally {
            Files.deleteIfExists(cpFile);
        }

        return r;
    }

    private static Document parse(Path pom) throws IOException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            DocumentBuilder b = f.newDocumentBuilder();
            return b.parse(pom.toFile());
        } catch (Exception e) {
            throw new IOException("Cannot parse pom.xml: " + pom, e);
        }
    }

    private static Properties properties(Document doc) {
        Properties p = new Properties();
        // system/environment-ish defaults not needed
        NodeList nodes = doc.getElementsByTagName("property");
        // Maven uses <properties><foo>bar</foo>, not <property>
        Element propsEl = first(doc, "properties");
        if (propsEl != null) {
            NodeList children = propsEl.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element e) {
                    p.setProperty(e.getTagName(), e.getTextContent().trim());
                }
            }
        }
        return p;
    }

    private static String release(Document doc, Properties props) {
        // 1. maven.compiler.release property
        String r = props.getProperty("maven.compiler.release");
        if (r != null && !r.isBlank()) return resolve(r, props);
        // 2. compiler plugin configuration
        NodeList plugins = doc.getElementsByTagName("plugin");
        for (int i = 0; i < plugins.getLength(); i++) {
            Element pl = (Element) plugins.item(i);
            String aid = textOf(pl, "artifactId");
            if (aid != null && aid.equals("maven-compiler-plugin")) {
                Element cfg = first(pl, "configuration");
                if (cfg != null) {
                    String rel = textOf(cfg, "release");
                    if (rel != null && !rel.isBlank()) return resolve(rel, props);
                    String src = textOf(cfg, "source");
                    String tgt = textOf(cfg, "target");
                    if (src != null && !src.isBlank()) return resolve(src, props);
                    if (tgt != null && !tgt.isBlank()) return resolve(tgt, props);
                }
            }
        }
        // 3. source/target properties
        r = props.getProperty("maven.compiler.source");
        if (r != null && !r.isBlank()) return resolve(r, props);
        r = props.getProperty("maven.compiler.target");
        if (r != null && !r.isBlank()) return resolve(r, props);
        return null;
    }

    private static String resolve(String value, Properties props) {
        String v = value.trim();
        if (v.startsWith("${") && v.endsWith("}")) {
            String key = v.substring(2, v.length() - 1);
            String pv = props.getProperty(key);
            if (pv != null) return pv.trim();
            String sys = System.getProperty(key);
            if (sys != null) return sys.trim();
        }
        return v;
    }

    private static String text(Document doc, String path) {
        String[] parts = path.split("/");
        Element cur = doc.getDocumentElement();
        // start after root project if needed
        for (String part : parts) {
            if (cur == null) return null;
            // if current tag matches first? handle root
            if (cur.getTagName().equals(part)) continue;
            cur = first(cur, part);
        }
        return cur == null ? null : cur.getTextContent().trim();
    }

    private static Element first(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        return nl.getLength() == 0 ? null : (Element) nl.item(0);
    }

    private static Element first(Document doc, String tag) {
        NodeList nl = doc.getElementsByTagName(tag);
        return nl.getLength() == 0 ? null : (Element) nl.item(0);
    }

    private static String textOf(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        return nl.item(0).getTextContent().trim();
    }

    private static void runMaven(Path baseDir, Path pom, String goal) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of(
                mavenExecutable(), "-q", "-f", pom.toString(), goal));
        runProcess(baseDir, cmd);
    }

    private static String mavenExecutable() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("windows") ? "mvn.cmd" : "mvn";
    }

    private static void runProcess(Path baseDir, List<String> cmd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(baseDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("Maven command failed (" + String.join(" ", cmd) + "):\n" + out);
        }
    }
}
