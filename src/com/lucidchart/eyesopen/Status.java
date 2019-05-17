package com.lucidchart.eyesopen;

import java.awt.*;

/** Possible tags for an image comparison */
public enum Status {

    // Initial comparisons results
    PASSED ("Passed", Color.GREEN, ConsoleColor.ANSI_GREEN),
    FAILED ("Failed", Color.RED, ConsoleColor.ANSI_RED),
    FAILED_TO_FIND_IMAGE_IN_REGION("Failed To Find Image In Region", Color.RED, ConsoleColor.ANSI_RED),
    DIFFERENT_SIZE("Different Size", Color.RED, ConsoleColor.ANSI_RED),
    MISSING ("Missing", Color.DARK_GRAY, ConsoleColor.ANSI_RED),
    NEEDS_APPROVAL ("Needs Approval", Color.MAGENTA, ConsoleColor.ANSI_PURPLE),

    // Triage actions
    COPY_TO_MASTER("Copy To Master", Color.YELLOW, ConsoleColor.ANSI_YELLOW),
    REJECTED ("Rejected", Color.RED, ConsoleColor.ANSI_RED),
    REMOVE_FROM_MASTER ("Remove From Master", Color.RED, ConsoleColor.ANSI_RED),

    // Performed Triage actions
    COPIED_TO_MASTER("Copied To Master", Color.YELLOW, ConsoleColor.ANSI_YELLOW),
    REMOVED_FROM_MASTER("Removed From Master", Color.RED, ConsoleColor.ANSI_RED),

    // Image is a master image
    MASTER_IMAGE("Master", Color.DARK_GRAY, ConsoleColor.ANSI_GREEN);

    public final Color tagColor;
    public final String text;
    public final String ansiColor;

    Status(String text, Color tagColor, String ansiColor) {
        this.text = text;
        this.tagColor = tagColor;
        this.ansiColor = ansiColor;
    }

    /** Prepends counter and appends tag to the name
     */
    public String annotate(int counter, String original) {
        return counter + "__" + this + "__" + original;
    }


    /** Obtain Status from string.  Null is return if no match is found */
    public static Status parseStatus(String status) {
        switch (status.toLowerCase().replaceAll("[_ ]","")) {
            case "passed": return Status.PASSED;
            case "failed": return Status.FAILED;
            case "missing": return Status.MISSING;
            case "needsapproval": return Status.NEEDS_APPROVAL;
            case "copytomaster": return Status.COPY_TO_MASTER;
            case "copiedtomaster": return Status.COPIED_TO_MASTER;
            case "removedfrommaster": return Status.REMOVED_FROM_MASTER;
            case "rejected": return Status.REJECTED;
            case "master": return Status.MASTER_IMAGE;
            case "removefrommaster": return Status.REMOVE_FROM_MASTER;
            default:
                throw new IllegalArgumentException("Unable to parse status: " + status);
        }
    }

}