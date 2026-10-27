/*
 * CorruptDataPromptTest.java
 *
 * Acceptance tests for the startup question shown when the saved data file is corrupt:
 * quitting is the default, and setting the file aside needs YES.
 */
package com.campusplan.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The choices offered by {@link CorruptDataPrompt}. */
class CorruptDataPromptTest {

    /** Output of the last {@link #ask} call. */
    private String output;

    /**
     * Shows the prompt for a sample error and answers it with the given lines.
     *
     * @param input the operator's answers, separated by newlines
     * @return whether the prompt decided to set the file aside
     */
    private boolean ask(String input) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean result = new CorruptDataPrompt(new BufferedReader(new StringReader(input)),
                new PrintStream(bytes, true, StandardCharsets.UTF_8))
                .askToSetAside("Data file campusplan.json is corrupt at line 3, column 5: oops", Path.of("campusplan.json"));
        output = bytes.toString(StandardCharsets.UTF_8);
        return result;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "quit", "\n"})
    @DisplayName("Given a corrupt data file, when the organizer chooses quit or just presses Enter, then the file is left unchanged")
    void quitIsTheDefault(String answer) {
        assertEquals(false, ask(answer));

        assertTrue(output.contains("Error: Data file campusplan.json is corrupt at line 3, column 5: oops"));
        assertTrue(output.contains("Data file left unchanged. Goodbye."));
    }

    @ParameterizedTest
    @CsvSource({"YES, true", "yes, false", "y, false", "'', false"})
    @DisplayName("Given a corrupt data file, when the organizer chooses to set it aside, then only typing YES confirms it")
    void settingAsideNeedsYes(String confirmation, boolean expected) {
        assertEquals(expected, ask("2\n" + confirmation + "\n"));
    }
}
