/*
 * CorruptDataPrompt.java
 *
 * User-interface layer: the startup question shown when the saved data file cannot
 * be loaded, letting the organizer quit or set the file aside and start empty.
 */
package com.campusplan.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Asks what to do about an unreadable data file. Quitting is the default; setting the file
 * aside needs the operator to type YES. This class only asks; the caller acts on the answer.
 */
public class CorruptDataPrompt {

    /** Operator input. */
    private final BufferedReader in;
    /** Prompts and messages. */
    private final PrintStream out;

    /**
     * Creates a prompt over the given streams.
     *
     * @param in  operator input
     * @param out destination for prompts and messages
     */
    public CorruptDataPrompt(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    /**
     * Reports the problem and asks whether to set the file aside.
     *
     * @param error the operator-facing description of what is wrong
     * @param file  the data file that could not be loaded
     * @return true only if the operator chose to set the file aside and typed YES to confirm
     */
    public boolean askToSetAside(String error, Path file) {
        out.println("Error: " + error);
        out.println("  1. Quit and leave the file unchanged (default)");
        out.println("  2. Set the file aside and start with no data");
        if (!"2".equals(read("Choose an option"))) {
            out.println("Data file left unchanged. Goodbye.");
            return false;
        }
        out.println(file.getFileName() + " will be renamed, with its contents kept, and CampusPlan will start empty.");
        if (!"YES".equals(read("Type YES to confirm"))) {
            out.println("Data file left unchanged. Goodbye.");
            return false;
        }
        return true;
    }

    /**
     * Prints a prompt and reads one trimmed line; end of input counts as an empty answer.
     *
     * @param label prompt text, without the trailing colon
     * @return the trimmed answer
     * @throws UncheckedIOException if reading input fails
     */
    private String read(String label) {
        out.print(label + ": ");
        out.flush();
        try {
            String line = in.readLine();
            return line == null ? "" : line.replace("﻿", "").trim();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
