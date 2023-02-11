/*
 * Copyright 2023 Miroslav Pokorny (github.com/mP1)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package walkingkooka.gwt.archivemaker.maven;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import walkingkooka.collect.iterable.Iterables;
import walkingkooka.collect.iterator.Iterators;
import walkingkooka.collect.list.Lists;
import walkingkooka.file.Files2;
import walkingkooka.j2cl.maven.J2clArtifact;
import walkingkooka.j2cl.maven.J2clArtifactShadeFile;
import walkingkooka.javashader.JavaShaders;
import walkingkooka.reflect.PackageName;
import walkingkooka.text.CaseSensitivity;
import walkingkooka.text.CharSequences;
import walkingkooka.text.LineEnding;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class GwtArchiveMakerTool {

    public static void main(final String[] arguments) throws Exception {
        final int argumentCount = arguments.length;

        switch (argumentCount) {
            case 3:
                GwtArchiveMakerTool.make(
                        Paths.get(arguments[0]),
                        Paths.get(arguments[1]),
                        Paths.get(arguments[2])
                );
                break;
            default:
                System.err.println("Expected 3 arguments: input-jar-path, output-jar-path, module-gwt-xml-path");
                break;
        }
    }

    private final static String META_INF = "META-INF";
    private final static String MANIFEST_PATH = META_INF + "/MANIFEST.MF";

    static GwtArchiveMakerTool make(final Path archiveIn,
                                    final Path archiveOut,
                                    final Path pom) throws Exception {
        if(false == archiveIn.toFile().exists()) {
            throw new IllegalArgumentException("Unable to find input *.jar file: " + archiveIn.toAbsolutePath());
        }
        if(!pom.toFile().exists()) {
            throw new IllegalArgumentException("Unable to find replacement POM.XML for built jar file: " + archiveIn.toAbsolutePath());
        }

        return new GwtArchiveMakerTool(
                archiveIn,
                archiveOut,
                pom
        );
    }

    private GwtArchiveMakerTool(final Path archiveIn,
                                final Path archiveOut,
                                final Path pom) throws Exception {
        final List<JarArchiveFileEntry> files = Lists.array();

        Manifest manifest = null;
        Map<PackageName, PackageName> shadings = null;
        Predicate<String> ignoreFiles = null;
        Predicate<String> publicFiles = null;
        String publicOutput = null;
        String superOutput = null;

        try (final JarFile jarFile = new JarFile(archiveIn.toFile())) {
            for (final JarEntry entry : Iterables.iterator(Iterators.enumeration(jarFile.entries()))) {
                if (entry.isDirectory()) {
                    continue;
                }

                final byte[] content = jarFile.getInputStream(entry)
                        .readAllBytes();

                final String name = entry.getName();
                if (name.startsWith(META_INF)) {
                    switch (name) {
                        case MANIFEST_PATH:
                            manifest = manifest(
                                    new ByteArrayInputStream(content)
                            );
                            break;
                        default:
                            // ignore the POM.properties and POM.xml
                    }
                    continue;
                }

                // all other files.
                switch (name) {
                    case J2clArtifact.CLASSPATH_REQUIRED_FILE:
                    case J2clArtifact.IGNORED_DEPENDENCY_FILE:
                    case J2clArtifact.JAVASCRIPT_SOURCE_REQUIRED_FILE:
                            // ignore these files...
                            break;
                        case J2clArtifact.IGNORED_FILES:
                            ignoreFiles = globPattern(content);
                            break;
                    case J2clArtifact.PUBLIC_FILES:
                        publicFiles = globPattern(content);
                        break;
                    case J2clArtifact.SHADE_FILE:
                        shadings = J2clArtifactShadeFile.readShadeFile(
                                new ByteArrayInputStream(content)
                        );
                        break;
                    default:
                        if (name.endsWith(".gwt.xml")) {
                            publicOutput = publicDirectory(name, content);
                            superOutput = superSourceDirectory(name, content);
                        }

                        files.add(
                                JarArchiveFileEntry.with(
                                        name,
                                        entry.getTime(),
                                        content
                                )
                        );
                        break;
                }
            }
        }

        this.files = files;

        if (null == manifest) {
            throw new IllegalArgumentException("Manifest missing from source jar file");
        }
        this.manifest = manifest;

        if(null == publicOutput) {
            throw new IllegalArgumentException("Required GWT module file (*.gwt.xml) missing");
        }

        if(null != ignoreFiles) {
            this.removeIgnoredFiles(
                    ignoreFiles
            );
        }

        if (null != shadings && shadings.size() > 0) {
            this.shadeFiles(
                    shadings,
                    superOutput
            );
        }

        if(null != publicFiles) {
            this.movePublicFiles(
                    publicFiles,
                    publicOutput
            );
        }

        if(null != ignoreFiles) {
            this.removeIgnoredFiles(
                    ignoreFiles
            );
        }

        this.synthesizeMavenFiles(pom);

        this.createArchiveAndWrite(
                archiveOut
        );
    }

    private static Predicate<String> globPattern(final byte[] contents) {
        return Files2.globPatterns(
                new String(
                        contents,
                        Charset.defaultCharset()
                ),
                CaseSensitivity.SENSITIVE
        );
    }


    private final Manifest manifest;

    private final List<JarArchiveFileEntry> files;

    /**
     * Reads the module gwt xml to get the public directory.
     */
    private static String publicDirectory(final String moduleGwtXml,
                                          final byte[] content) throws Exception {
        final String moduleParentDirectory = moduleXmlParentDirectory(moduleGwtXml);
        return moduleParentDirectory + "/" + elementPathAttributeOrDefault(
                content,
                "public",
                "public"
        );
    }

    /**
     * Reads the module gwt xml to get the super source directory.
     */
    private static String superSourceDirectory(final String moduleGwtXml,
                                               final byte[] content) throws Exception {
        final String moduleParentDirectory = moduleXmlParentDirectory(moduleGwtXml);
        return moduleParentDirectory + "/" + elementPathAttributeOrDefault(
                content,
                "super-source",
                "super"
        );
    }

    private static String moduleXmlParentDirectory(final String moduleGwtXmlPath) {
        final int parentDirectoryEnd = moduleGwtXmlPath.lastIndexOf('/');
        if (-1 == parentDirectoryEnd) {
            throw new IllegalArgumentException("GWT Module may not be in the root of the jar file.");
        }

        return moduleGwtXmlPath.substring(0, parentDirectoryEnd);
    }

    private static String elementPathAttributeOrDefault(final byte[] content,
                                                        final String tagName,
                                                        final String defaultValue) throws Exception {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            final Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(inputStream);

            final NodeList tags = document.getDocumentElement().getElementsByTagName(tagName);

            final String result;
            switch (tags.getLength()) {
                case 0:
                    result = defaultValue;
                    break;
                case 1:
                    final Element element = (Element) tags.item(0);
                    result = element.getAttribute("path");
                    break;
                default:
                    throw new IllegalArgumentException("Got " + tags.getLength() + " " + CharSequences.quoteAndEscape(tagName) + " expected only 0 or 1");
            }

            return result;
        }
    }

    // Manifest-Version: 1.0
    // Created-By: Maven Jar Plugin 3.2.0
    // Build-Jdk-Spec: 11
    private static Manifest manifest(final InputStream content) throws IOException {
        final Manifest newManifest = new Manifest(content);
        newManifest.getMainAttributes()
                .put(
                        new Attributes.Name("Created-By"),
                        "walkingkooka-gwt-archive-maker-maven-plugin"
                );
        return newManifest;
    }

    /**
     * All files are first checked and moved to match the first mapping.
     * After that all *.java and *.clas files are then shaded as necessary.
     */
    private void shadeFiles(final Map<PackageName, PackageName> shadings,
                            final String superDirectory) {
        for (final Map.Entry<PackageName, PackageName> mapping : shadings.entrySet()) {
            final String fromPackage = mapping.getKey().value();
            final String toPackage = mapping.getValue().value();

            final String fromDirectory = fromPackage.replace('.', '/');

            // only try and move files that havent been moved before
            this.files.stream()
                    .filter(f -> !f.hasMoved())
                    .filter(f -> {
                        final String path = f.path();
                        return path.startsWith(fromDirectory) &&
                                (
                                        path.endsWith(".class") || path.endsWith(".java")
                                );
                    })
                    .forEach(f -> {
                                final String path = f.path();

                                final String base;
                                if (!fromPackage.equals(toPackage) && path.endsWith(".java")) {
                                    base = superDirectory + "/";
                                } else {
                                    base = "";
                                }

                                final String toDirectory = base + toPackage.replace('.', '/');
                                f.setPath(
                                        toDirectory + path.substring(fromDirectory.length())
                                );
                            }
                    );
        }

        this.files.sort(
                (final JarArchiveFileEntry l, final JarArchiveFileEntry r) -> l.path().compareTo(r.path())
        );

        for (final JarArchiveFileEntry entry : this.files) {
            final String path = entry.path();
            if (path.endsWith(".java")) {
                entry.setContent(
                        JavaShaders.javaFilePackageShader(Charset.defaultCharset())
                                .apply(
                                        entry.content(),
                                        shadings
                                )
                );
            }

            if (path.endsWith(".class")) {
                final byte[] before = entry.content();
                final byte[] after = JavaShaders.classFilePackageShader()
                        .apply(
                                before,
                                shadings
                        );
                // if a *.class file was shaded, mark it for deletion.
                if (!Arrays.equals(before, after)) {
                    entry.setContent(
                            null
                    );
                }
            }
        }
    }

    private void removeIgnoredFiles(final Predicate<String> ignored) {
        final Iterator<JarArchiveFileEntry> entries = this.files.iterator();

        while (entries.hasNext()) {
            if (ignored.test(
                    entries.next()
                            .path()
            )) {
                entries.remove();
            }
        }
    }

    private void movePublicFiles(final Predicate<String> publicFiles,
                                 final String publicOutput) {
        this.files.stream()
                .filter(f -> publicFiles.test(f.path()))
                .forEach(f ->
                        f.setPath(publicOutput + "/" + f.path())
                );
    }

    /**
     * Creates the MAVEN pom.properties and copies pom.xml to the files.
     */
    private void synthesizeMavenFiles(final Path pom) throws Exception {
        // <project xmlns="http://maven.apache.org/POM/4.0.0"
        //         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        //         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
        //    <modelVersion>4.0.0</modelVersion>
        //
        //    <groupId>walkingkooka</groupId>
        //    <artifactId>walkingkooka-gwt-archive-maker-maven-plugin</artifactId>
        //    <version>1.0-SNAPSHOT</version>
        //
        // first read the POM to extract the groupId, artifactId and versionId.
        String groupId = null;
        String artifactId = null;
        String version = null;

        try (final FileInputStream fileInputStream = new FileInputStream(pom.toFile())) {
            final Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(fileInputStream);

            final Element root = document.getDocumentElement();
            groupId = elementTextOrFail(root, "groupId");
            artifactId = elementTextOrFail(root, "artifactId");
            version = elementTextOrFail(root, "version");
        }

        if (CharSequences.isNullOrEmpty(groupId)) {
            throw new IllegalArgumentException("POM file missing \"groupdId\"");
        }
        if (CharSequences.isNullOrEmpty(artifactId)) {
            throw new IllegalArgumentException("POM file missing \"artifactId\"");
        }
        if (CharSequences.isNullOrEmpty(version)) {
            throw new IllegalArgumentException("POM file missing \"version\"");
        }

        final String mavenDir = META_INF + "/maven/" + groupId + "/" + artifactId + "/";

        // artifactId=walkingkooka-spreadsheet
        // groupId=walkingkooka
        // version=1.0-SNAPSHOT
        // /META-INF/MAVEN/groupId/artifactId/pom.properties
        this.files.add(
                JarArchiveFileEntry.with(
                        mavenDir + "pom.properties",
                        System.currentTimeMillis(),
                        ("artifactId=" + artifactId + LineEnding.SYSTEM +
                                "groupId=" + groupId + LineEnding.SYSTEM +
                                "version=" + version + LineEnding.SYSTEM).getBytes(StandardCharsets.UTF_8)
                )
        );

        // /META-INF/MAVEN/groupId/artifactId/pom.xml
        this.files.add(
                JarArchiveFileEntry.with(
                        mavenDir + "pom.xml",
                        System.currentTimeMillis(),
                        Files.readAllBytes(pom)
                )
        );
    }

    private String elementTextOrFail(final Element parent,
                                     final String tagName) {
        final NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child instanceof Element) {
                final Element childElement = (Element) child;
                if (childElement.getTagName().equals(tagName)) {
                    return childElement.getTextContent();
                }
            }
        }

        throw new IllegalArgumentException("Missing " + CharSequences.quoteAndEscape(tagName));
    }

    /**
     * Creates an archive from the files field. This assumes that magic files have been removed and actioned if necessary.
     */
    private void createArchiveAndWrite(final Path path) throws IOException {
        try (final FileOutputStream jarFile = new FileOutputStream(path.toFile())) {
            try (final JarOutputStream jar = new JarOutputStream(jarFile, this.manifest)) {
                for (final JarArchiveFileEntry entry : this.files) {

                    // shaded class files will have a NULL content, dont write them back out.
                    final byte[] content = entry.content();
                    if(null == content) {
                        continue;
                    }
                    final JarEntry targetJarFileEntry = new JarEntry(entry.path());

                    // write the file lastModified and then its content.
                    targetJarFileEntry.setTime(entry.lastModified());
                    jar.putNextEntry(targetJarFileEntry);
                    jar.write(content);
                    jar.closeEntry();
                }

                jarFile.flush();
            }
        }
    }
}
