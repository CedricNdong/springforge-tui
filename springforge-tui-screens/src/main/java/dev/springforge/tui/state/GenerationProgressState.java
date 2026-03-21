package dev.springforge.tui.state;

import java.util.ArrayList;
import java.util.List;

import dev.springforge.engine.model.FileGenerationResult;
import dev.springforge.engine.model.GenerationStatus;

/**
 * State for the generation progress screen (S5).
 */
public record GenerationProgressState(
    int totalFiles,
    int completedFiles,
    int skippedFiles,
    int errorFiles,
    String currentFile,
    List<FileGenerationResult> log,
    OverallStatus overallStatus
) {

    public enum OverallStatus {
        IN_PROGRESS, DONE, ERROR
    }

    public static GenerationProgressState initial(int totalFiles) {
        return new GenerationProgressState(
            totalFiles, 0, 0, 0, "", List.of(), OverallStatus.IN_PROGRESS);
    }

    public GenerationProgressState withFileResult(FileGenerationResult result) {
        List<FileGenerationResult> newLog = new ArrayList<>(log);
        newLog.add(result);
        int newCompleted = completedFiles;
        int newSkipped = skippedFiles;
        int newError = errorFiles;
        if (result.status() == GenerationStatus.CREATED) {
            newCompleted++;
        } else if (result.status() == GenerationStatus.SKIPPED) {
            newSkipped++;
        } else {
            newError++;
        }
        String nextFile = "";
        OverallStatus status = OverallStatus.IN_PROGRESS;
        if (newCompleted + newSkipped + newError >= totalFiles) {
            status = newError > 0 ? OverallStatus.ERROR : OverallStatus.DONE;
        }
        return new GenerationProgressState(
            totalFiles, newCompleted, newSkipped, newError,
            nextFile, newLog, status);
    }

    public GenerationProgressState withCurrentFile(String currentFile) {
        return new GenerationProgressState(
            totalFiles, completedFiles, skippedFiles, errorFiles,
            currentFile, log, overallStatus);
    }

    public int progressPercent() {
        if (totalFiles == 0) return 100;
        return ((completedFiles + skippedFiles + errorFiles) * 100) / totalFiles;
    }
}
