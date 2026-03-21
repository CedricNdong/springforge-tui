package dev.springforge.tui.state;

/**
 * State for the error screen (S8).
 */
public record ErrorState(
    String errorMessage,
    String filePath,
    boolean canRetry
) {

    public static ErrorState of(String errorMessage) {
        return new ErrorState(errorMessage, null, false);
    }

    public static ErrorState ofFile(String errorMessage, String filePath) {
        return new ErrorState(errorMessage, filePath, true);
    }
}
