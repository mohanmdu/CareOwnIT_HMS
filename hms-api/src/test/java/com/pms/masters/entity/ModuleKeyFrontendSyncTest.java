package com.pms.masters.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * ModuleKey exists twice - this Java enum, and the matching TS union in
 * hms-web/src/app/layout/package-config.ts. Every file that touches either
 * side says "keep these in sync" in a comment (ModuleKey.java,
 * ModulePathMappings.java, package-config.ts itself), but a comment doesn't
 * actually stop them from drifting apart. This test reads the real sibling
 * TS source file (both apps live in the same repo checkout) and fails the
 * build the moment the two module vocabularies disagree, instead of relying
 * on someone remembering to update both.
 *
 * Skips rather than fails if the hms-web checkout isn't present alongside
 * hms-api - deliberately not a hard cross-repo build dependency, just a
 * same-repo convenience check that runs whenever both halves are checked out
 * together (the normal case here).
 */
class ModuleKeyFrontendSyncTest {

    private static final Path FRONTEND_FILE = Path.of("../hms-web/src/app/layout/package-config.ts");

    @Test
    @EnabledIf("frontendFileExists")
    void moduleKeyEnumMatchesFrontendUnion() throws IOException {
        String source = Files.readString(FRONTEND_FILE);

        Matcher unionBlock = Pattern.compile("export type ModuleKey =([^;]+);", Pattern.DOTALL).matcher(source);
        assertTrue(unionBlock, "Could not find 'export type ModuleKey = ...;' in " + FRONTEND_FILE);

        Set<String> frontendKeys = new TreeSet<>();
        Matcher literal = Pattern.compile("'([a-z0-9-]+)'").matcher(unionBlock.group(1));
        while (literal.find()) {
            frontendKeys.add(literal.group(1));
        }

        Set<String> backendKeys =
                Arrays.stream(ModuleKey.values()).map(ModuleKey::key).collect(Collectors.toCollection(TreeSet::new));

        assertEquals(
                frontendKeys,
                backendKeys,
                "ModuleKey.java and package-config.ts's ModuleKey union have drifted apart - add/remove the module in both places.");
    }

    static boolean frontendFileExists() {
        return Files.exists(FRONTEND_FILE);
    }

    private static void assertTrue(Matcher matcher, String message) {
        if (!matcher.find()) {
            throw new AssertionError(message);
        }
    }
}
