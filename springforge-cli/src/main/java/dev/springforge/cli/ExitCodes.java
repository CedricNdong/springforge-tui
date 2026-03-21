package dev.springforge.cli;

/**
 * Exit codes as defined in PRD Section 5.4.
 */
public final class ExitCodes {

    public static final int SUCCESS = 0;
    public static final int GENERAL_ERROR = 1;
    public static final int INVALID_ARGUMENTS = 2;
    public static final int ENTITY_PARSE_ERROR = 3;
    public static final int FILE_WRITE_ERROR = 4;
    public static final int CONFIG_ERROR = 5;

    private ExitCodes() {}
}
