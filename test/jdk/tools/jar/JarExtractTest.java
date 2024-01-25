/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import jdk.test.lib.util.JarBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8173970
 * @summary jar tool should allow extracting to specific directory
 * @library /test/lib
 * @run junit JarExtractTest
 */
public class JarExtractTest {
    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
            .orElseThrow(() ->
                    new RuntimeException("jar tool not found")
            );

    private static final byte[] FILE_CONTENT = "Hello world!!!".getBytes(StandardCharsets.UTF_8);
    private static final String LEADING_SLASH_PRESERVED_ENTRY = "/tmp/8173970/f1.txt";
    // the jar that will get extracted in the tests
    private Path testJarPath;
    private static Collection<Path> filesToDelete = new ArrayList<>();

    @BeforeEach
    public void createTestJar() throws Exception {
        Files.deleteIfExists(Path.of(LEADING_SLASH_PRESERVED_ENTRY));

        final String tmpDir = Files.createTempDirectory("8173970-").toString();
        testJarPath = Path.of(tmpDir, "8173970-test.jar");
        final JarBuilder builder = new JarBuilder(testJarPath.toString());
        // d1
        //  |--- d2
        //  |    |--- d3
        //  |    |    |--- f2.txt
        //  |
        //  |--- d4
        //  ...
        //  f1.txt

        builder.addEntry("d1/", new byte[0]);
        builder.addEntry("f1.txt", FILE_CONTENT);
        builder.addEntry("d1/d2/d3/f2.txt", FILE_CONTENT);
        builder.addEntry("d1/d4/", new byte[0]);
        builder.build();

        filesToDelete.add(Path.of(LEADING_SLASH_PRESERVED_ENTRY));
    }

    @AfterEach
    public void cleanup() {
        for (final Path p : filesToDelete) {
            try {
                System.out.println("Deleting file/dir " + p);
                Files.delete(p);
            } catch (IOException ioe) {
                //ignore
            }
        }
    }

    /**
     * Creates and returns various relative paths, to which the jar will be extracted in the tests
     */
    static Stream<Arguments> provideRelativeExtractLocations() throws Exception {
        // create some dirs so that they already exist when the jar is being extracted
        final String existing1 = "." + File.separator + "8173970-existing-1";
        Files.createDirectories(Path.of(existing1));
        final String existing2 = "." + File.separator + "foo" + File.separator + "8173970-existing-2";
        Files.createDirectories(Path.of(existing2));
        final Path dirOutsideScratchDir = Files.createTempDirectory(Path.of(".."), "8173970");
        // we need to explicitly delete this dir after the tests end
        filesToDelete.add(dirOutsideScratchDir);
        final String existing3 = dirOutsideScratchDir.toString() + File.separator + "8173970-existing-3";
        Files.createDirectories(Path.of(existing3));

        final String anotherDirOutsideScratchDir = ".." + File.separator + "8173970-non-existent";
        filesToDelete.add(Path.of(anotherDirOutsideScratchDir));

        final List<Arguments> args = new ArrayList<>();
        args.add(Arguments.of(".")); // current dir
        // (explicitly) relative to current dir
        args.add(Arguments.of("." + File.separator + "8173970-extract-1"));
        // (implicitly) relative to current dir
        args.add(Arguments.of("8173970-extract-2"));
        // sibling to current dir
        args.add(Arguments.of(anotherDirOutsideScratchDir));
        // some existing dirs
        args.add(Arguments.of(existing1));
        args.add(Arguments.of(existing2));
        args.add(Arguments.of(existing3));
        // a non-existent dir within an existing dir
        args.add(Arguments.of(existing1 + File.separator
                + "non-existing" + File.separator + "foo"));
        return args.stream();
    }

    /**
     * Creates and returns various absolute paths, to which the jar will be extracted in the tests
     */
    static Stream<Arguments> provideAbsoluteExtractLocations() throws Exception {
        final Stream<Arguments> relative = provideRelativeExtractLocations();
        return relative.map((arg) -> {
            final String relPath = (String) arg.get()[0];
            return Arguments.of(Path.of(relPath).toAbsolutePath().toString());
        });
    }

    /**
     * Creates and returns various normalized paths, to which the jar will be extracted in the tests
     */
    static Stream<Arguments> provideAbsoluteNormalizedExtractLocations() throws Exception {
        final Stream<Arguments> relative = provideRelativeExtractLocations();
        return relative.map((arg) -> {
            final String relPath = (String) arg.get()[0];
            return Arguments.of(Path.of(relPath).toAbsolutePath().normalize().toString());
        });
    }

    /**
     * Extracts a jar to various relative paths, using the -C/--dir option and then
     * verifies that the extracted content is at the expected locations with the correct
     * content
     */
    @ParameterizedTest
    @MethodSource("provideRelativeExtractLocations")
    public void testExtractToRelativeDir(final String dest) throws Exception {
        testLongFormExtract(dest);
        testExtract(dest);
    }

    /**
     * Extracts a jar to various absolute paths, using the -C/--dir option and then
     * verifies that the extracted content is at the expected locations with the correct
     * content
     */
    @ParameterizedTest
    @MethodSource("provideAbsoluteExtractLocations")
    public void testExtractToAbsoluteDir(final String dest) throws Exception {
        testExtract(dest);
        testLongFormExtract(dest);
    }

    /**
     * Extracts a jar to various normalized paths (i.e. no {@code .} or @{code ..} in the path components),
     * using the -C/--dir option and then verifies that the extracted content is at the expected locations
     * with the correct content
     */
    @ParameterizedTest
    @MethodSource("provideAbsoluteNormalizedExtractLocations")
    public void testExtractToAbsoluteNormalizedDir(final String dest) throws Exception {
        testExtract(dest);
        testLongFormExtract(dest);
    }

    /**
     * Test that extracting a jar with {@code jar -x -f --dir} works as expected
     */
    @Test
    public void testExtractLongFormDir() throws Exception {
        final String dest = "foo-bar";
        System.out.println("Extracting " + testJarPath + " to " + dest);
        final int exitCode = JAR_TOOL.run(System.out, System.err, "-x", "-f", testJarPath.toString(),
                "--dir", dest);
        assertEquals(0, exitCode, "Failed to extract " + testJarPath + " to " + dest);
        verifyExtractedContent(dest);
    }

    /**
     * Verifies that the {@code jar --help} output contains the --dir option
     */
    @Test
    public void testHelpOutput() {
        final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        final int exitCode = JAR_TOOL.run(new PrintStream(outStream), System.err, "--help");
        assertEquals(0, exitCode, "jar --help command failed");
        final String output = outStream.toString();
        // this message is expected to be the one from the jar --help output which is sourced from
        // jar.properties
        final String expectedMsg = "--dir                    Directory into which the jar will be extracted";
        assertTrue(output.contains(expectedMsg), "jar --help didn't contain --dir option");
    }

    /**
     * Tests that {@code jar -x -f} command works fine even when the -C or --dir option
     * isn't specified
     */
    @Test
    public void testExtractWithoutOutputDir() throws Exception {
        final int exitCode = JAR_TOOL.run(System.out, System.err, "-x", "-f", testJarPath.toString());
        assertEquals(0, exitCode, "Failed to extract " + testJarPath);
        // the content would have been extracted to current dir
        verifyExtractedContent(".");
    }

    /**
     * Tests that {@code jar --extract -f} command works fine even when the -C or --dir option
     * isn't specified
     */
    @Test
    public void testLongFormExtractWithoutOutputDir() throws Exception {
        final int exitCode = JAR_TOOL.run(System.out, System.err, "--extract", "-f", testJarPath.toString());
        assertEquals(0, exitCode, "Failed to extract " + testJarPath);
        // the content would have been extracted to current dir
        verifyExtractedContent(".");
    }

    /**
     * Tests that extracting a jar using {@code -P} flag and without any explicit destination
     * directory works correctly if the jar contains entries with leading slashes and/or {@code ..}
     * parts preserved.
     */
    @Test
    public void testExtractNoDestDirWithPFlag() throws Exception {
        // create a jar which has leading slash (/) and dot-dot (..) preserved in entry names
        final Path jarPath = createJarWithPFlagSemantics();
        final List<String[]> cmdArgs = new ArrayList<>();
        cmdArgs.add(new String[]{"-xvfP", jarPath.toString()});
        cmdArgs.add(new String[]{"--extract", "-v", "-P", "-f", jarPath.toString()});
        for (final String[] args : cmdArgs) {
            printJarCommand(args);
            final int exitCode = JAR_TOOL.run(System.out, System.err, args);
            assertEquals(0, exitCode, "Failed to extract " + jarPath);
            final String dest = ".";
            assertTrue(Files.isDirectory(Path.of(dest)), dest + " is not a directory");
            final Path d1 = Path.of(dest, "d1");
            assertTrue(Files.isDirectory(d1), d1 + " directory is missing or not a directory");
            final Path d2 = Path.of(dest, "d1", "d2");
            assertTrue(Files.isDirectory(d2), d2 + " directory is missing or not a directory");
            final Path f1 = Path.of(LEADING_SLASH_PRESERVED_ENTRY);
            assertTrue(Files.isRegularFile(f1), f1 + " is missing or not a file");
            assertArrayEquals(FILE_CONTENT, Files.readAllBytes(f1), "Unexpected content in file " + f1);
            final Path f2 = Path.of("d1/d2/../f2.txt");
            assertTrue(Files.isRegularFile(f2), f2 + " is missing or not a file");
            assertArrayEquals(FILE_CONTENT, Files.readAllBytes(f2), "Unexpected content in file " + f2);
        }
    }

    /**
     * Tests that the {@code -P} option cannot be used during jar extraction when the {@code -C} and/or
     * {@code --dir} option is used
     */
    @Test
    public void testExtractWithDirPFlagNotAllowed() throws Exception {
        // this error message is expected to be the one from the jar --help output which is sourced from
        // jar.properties
        final String expectedErrMsg = "You may not specify '-Px' with the '-C' or '--dir' options";
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final List<String[]> cmdArgs = new ArrayList<>();
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-P", "-C", tmpDir});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-P", "--dir", tmpDir});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-P", "-C", "."});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-P", "--dir", "."});
        cmdArgs.add(new String[]{"-xvfP", testJarPath.toString(), "-C", tmpDir});
        cmdArgs.add(new String[]{"--extract", "-f", testJarPath.toString(), "-P", "-C", tmpDir});
        cmdArgs.add(new String[]{"--extract", "-f", testJarPath.toString(), "-P", "--dir", tmpDir});
        cmdArgs.add(new String[]{"--extract", "-f", testJarPath.toString(), "-P", "-C", "."});
        cmdArgs.add(new String[]{"--extract", "-f", testJarPath.toString(), "-P", "--dir", "."});
        for (final String[] args : cmdArgs) {
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            printJarCommand(args);
            int exitCode = JAR_TOOL.run(System.out, new PrintStream(err), args);
            assertNotEquals(0, exitCode, "jar extraction was expected to fail but didn't");
            // verify it did indeed fail due to the right reason
            assertTrue(err.toString(StandardCharsets.UTF_8).contains(expectedErrMsg));
        }
    }

    /**
     * Tests that {@code jar -xvf <jarname> -C <dir>} works fine too
     */
    @Test
    public void testLegacyCompatibilityMode() throws Exception {
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final String[] args = new String[]{"-xvf", testJarPath.toString(), "-C", tmpDir};
        printJarCommand(args);
        final int exitCode = JAR_TOOL.run(System.out, System.err, args);
        assertEquals(0, exitCode, "Failed to extract " + testJarPath);
        verifyExtractedContent(tmpDir);
    }

    /**
     * Tests that when multiple directories are specified for extracting the jar, the jar extraction
     * fails
     */
    @Test
    public void testExtractFailWithMultipleDir() throws Exception {
        // this error message is expected to be the one from the jar --help output which is sourced from
        // jar.properties
        final String expectedErrMsg = "You may not specify the '-C' or '--dir' option more than once with the '-x' option";
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final List<String[]> cmdArgs = new ArrayList<>();
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "-C", tmpDir, "-C", tmpDir});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "--dir", tmpDir, "--dir", tmpDir});
        cmdArgs.add(new String[]{"-x", "-f", testJarPath.toString(), "--dir", tmpDir, "-C", tmpDir});
        cmdArgs.add(new String[]{"--extract", "-f", testJarPath.toString(), "-C", tmpDir, "-C", tmpDir});
        cmdArgs.add(new String[]{"--extract", "-f", testJarPath.toString(), "--dir", tmpDir, "--dir", tmpDir});
        cmdArgs.add(new String[]{"--extract", "-f", testJarPath.toString(), "--dir", tmpDir, "-C", tmpDir});
        for (final String[] args : cmdArgs) {
            final ByteArrayOutputStream err = new ByteArrayOutputStream();
            printJarCommand(args);
            int exitCode = JAR_TOOL.run(System.out, new PrintStream(err), args);
            assertNotEquals(0, exitCode, "jar extraction was expected to fail but didn't");
            // verify it did indeed fail due to the right reason
            assertTrue(err.toString(StandardCharsets.UTF_8).contains(expectedErrMsg));
        }
    }

    /**
     * Tests that extracting only specific files from a jar, into a specific destination directory,
     * works as expected
     */
    @Test
    public void testExtractPartialContent() throws Exception {
        String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        String[] cmdArgs = new String[]{"-x", "-f", testJarPath.toString(), "--dir", tmpDir,
                "f1.txt", "d1/d2/d3/f2.txt"};
        testExtractPartialContent(tmpDir, cmdArgs);

        tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        cmdArgs = new String[]{"--extract", "-f", testJarPath.toString(), "--dir", tmpDir,
                "f1.txt", "d1/d2/d3/f2.txt"};
        testExtractPartialContent(tmpDir, cmdArgs);

    }

    /**
     * Extract to destDir using the passed command arguments and verify the extracted content
     */
    private void testExtractPartialContent(final String destDir, final String[] extractArgs) throws Exception {
        printJarCommand(extractArgs);
        final int exitCode = JAR_TOOL.run(System.out, System.err, extractArgs);
        assertEquals(0, exitCode, "Failed to extract " + testJarPath);
        // make sure only the specific files were extracted
        final Stream<Path> paths = Files.walk(Path.of(destDir));
        // files/dirs count expected to be found when the location to which the jar was extracted
        // is walked.
        // 1) The top level dir being walked 2) f1.txt file 3) d1 dir 4) d1/d2 dir
        // 5) d1/d2/d3 dir 6) d1/d2/d3/f2.txt file
        final int numExpectedFiles = 6;
        assertEquals(numExpectedFiles, paths.count(), "Unexpected number of files/dirs in " + destDir);
        final Path f1 = Path.of(destDir, "f1.txt");
        assertTrue(Files.isRegularFile(f1), f1.toString() + " wasn't extracted from " + testJarPath);
        assertArrayEquals(FILE_CONTENT, Files.readAllBytes(f1), "Unexpected content in file " + f1);
        final Path d1 = Path.of(destDir, "d1");
        assertTrue(Files.isDirectory(d1), d1.toString() + " wasn't extracted from " + testJarPath);
        assertEquals(2, Files.walk(d1, 1).count(), "Unexpected number " +
                "of files/dirs in " + d1);
        final Path d2 = Path.of(d1.toString(), "d2");
        assertTrue(Files.isDirectory(d2), d2.toString() + " wasn't extracted from " + testJarPath);
        assertEquals(2, Files.walk(d2, 1).count(), "Unexpected number " +
                "of files/dirs in " + d2);
        final Path d3 = Path.of(d2.toString(), "d3");
        assertTrue(Files.isDirectory(d3), d3.toString() + " wasn't extracted from " + testJarPath);
        assertEquals(2, Files.walk(d3, 1).count(), "Unexpected number " +
                "of files/dirs in " + d3);
        final Path f2 = Path.of(d3.toString(), "f2.txt");
        assertTrue(Files.isRegularFile(f2), f2.toString() + " wasn't extracted from " + testJarPath);
        assertArrayEquals(FILE_CONTENT, Files.readAllBytes(f2), "Unexpected content in file " + f2);
    }

    /**
     * Extracts the jar file using {@code jar -x -f <jarfile> -C <dest>} and verifies the extracted content
     */
    private void testExtract(final String dest) throws Exception {
        final String[] args = new String[]{"-x", "-f", testJarPath.toString(), "-C", dest};
        printJarCommand(args);
        final int exitCode = JAR_TOOL.run(System.out, System.err, args);
        assertEquals(0, exitCode, "Failed to extract " + testJarPath + " to " + dest);
        verifyExtractedContent(dest);
    }

    /**
     * Extracts the jar file using {@code jar --extract -f <jarfile> -C <dest>} and verifies the
     * extracted content
     */
    private void testLongFormExtract(final String dest) throws Exception {
        final String[] args = new String[]{"--extract", "-f", testJarPath.toString(), "-C", dest};
        printJarCommand(args);
        final int exitCode = JAR_TOOL.run(System.out, System.err, args);
        assertEquals(0, exitCode, "Failed to extract " + testJarPath + " to " + dest);
        verifyExtractedContent(dest);
    }

    /**
     * Verifies that the extracted jar content matches what was present in the original jar
     */
    private void verifyExtractedContent(final String dest) throws IOException {
        assertTrue(Files.isDirectory(Path.of(dest)), dest + " is not a directory");
        final Path d1 = Path.of(dest, "d1");
        assertTrue(Files.isDirectory(d1), d1 + " directory is missing or not a directory");
        final Path d2 = Path.of(dest, "d1", "d2");
        assertTrue(Files.isDirectory(d2), d2 + " directory is missing or not a directory");
        final Path d3 = Path.of(dest, "d1", "d2", "d3");
        assertTrue(Files.isDirectory(d3), d3 + " directory is missing or not a directory");
        final Path d4 = Path.of(dest, "d1", "d4");
        assertTrue(Files.isDirectory(d4), d4 + " directory is missing or not a directory");
        // d1/d4 is expected to be empty directory
        final List<Path> d4Children;
        try (final Stream<Path> s = Files.walk(d4, 1)) {
            d4Children = s.toList();
        }
        assertEquals(1, d4Children.size(), "Directory " + d4
                + " has unexpected files/dirs: " + d4Children);
        final Path f1 = Path.of(dest, "f1.txt");
        assertTrue(Files.isRegularFile(f1), f1 + " is missing or not a file");
        assertArrayEquals(FILE_CONTENT, Files.readAllBytes(f1), "Unexpected content in file " + f1);
        final Path f2 = Path.of(d3.toString(), "f2.txt");
        assertTrue(Files.isRegularFile(f2), f2 + " is missing or not a file");
        assertArrayEquals(FILE_CONTENT, Files.readAllBytes(f2), "Unexpected content in file " + f2);
    }

    /**
     * Creates a jar whose entries have a leading slash and the dot-dot character preserved.
     * This is the same as creating a jar using {@code jar -cfP somejar.jar <file1> <file2> ...}
     */
    private static Path createJarWithPFlagSemantics() throws IOException {
        final String tmpDir = Files.createTempDirectory(Path.of("."), "8173970-").toString();
        final Path jarPath = Path.of(tmpDir, "8173970-test-withpflag.jar");
        final JarBuilder builder = new JarBuilder(jarPath.toString());
        builder.addEntry("d1/", new byte[0]);
        builder.addEntry("d1/d2/", new byte[0]);
        builder.addEntry(LEADING_SLASH_PRESERVED_ENTRY, FILE_CONTENT);
        builder.addEntry("d1/d2/../f2.txt", FILE_CONTENT);
        builder.build();
        return jarPath;
    }

    private static void printJarCommand(final String[] cmdArgs) {
        System.out.println("Running 'jar " + String.join(" ", cmdArgs) + "'");
    }
}