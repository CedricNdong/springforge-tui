package dev.springforge.tui.state;

/**
 * State for the splash / scan screen (S1).
 */
public record SplashState(
    int totalFiles,
    int scannedFiles,
    String currentFile,
    boolean scanComplete,
    String errorMessage,
    boolean configFound
) {

    public static SplashState initial() {
        return new SplashState(0, 0, "", false, null, false);
    }

    public SplashState withProgress(int scannedFiles, String currentFile) {
        return new SplashState(totalFiles, scannedFiles, currentFile, false, null, configFound);
    }

    public SplashState withComplete(int totalFiles) {
        return new SplashState(totalFiles, totalFiles, "", true, null, configFound);
    }

    public SplashState withError(String errorMessage) {
        return new SplashState(totalFiles, scannedFiles, currentFile, false, errorMessage, configFound);
    }

    public SplashState withConfigFound(boolean found) {
        return new SplashState(totalFiles, scannedFiles, currentFile, scanComplete, errorMessage, found);
    }

    public int progressPercent() {
        if (totalFiles == 0) return 0;
        return (scannedFiles * 100) / totalFiles;
    }
}
