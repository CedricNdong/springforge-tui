package dev.springforge.tui.state;

/**
 * State for the splash / scan screen (S1).
 */
public record SplashState(
    int totalFiles,
    int scannedFiles,
    String currentFile,
    boolean scanComplete,
    String errorMessage
) {

    public static SplashState initial() {
        return new SplashState(0, 0, "", false, null);
    }

    public SplashState withProgress(int scannedFiles, String currentFile) {
        return new SplashState(totalFiles, scannedFiles, currentFile, false, null);
    }

    public SplashState withComplete(int totalFiles) {
        return new SplashState(totalFiles, totalFiles, "", true, null);
    }

    public SplashState withError(String errorMessage) {
        return new SplashState(totalFiles, scannedFiles, currentFile, false, errorMessage);
    }

    public int progressPercent() {
        if (totalFiles == 0) return 0;
        return (scannedFiles * 100) / totalFiles;
    }
}
