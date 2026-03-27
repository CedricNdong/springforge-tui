package dev.springforge.tui.state;

import java.util.List;

import dev.springforge.engine.model.GeneratedFile;

/**
 * State for the code preview screen (S4).
 */
public record PreviewState(
    List<GeneratedFile> files,
    int selectedFileIndex,
    int scrollOffset
) {

    public static PreviewState initial(List<GeneratedFile> files) {
        return new PreviewState(files, 0, 0);
    }

    public PreviewState selectPrevious() {
        if (files.isEmpty()) return this;
        int newIndex = Math.max(0, selectedFileIndex - 1);
        return new PreviewState(files, newIndex, 0);
    }

    public PreviewState selectNext() {
        if (files.isEmpty()) return this;
        int newIndex = Math.min(files.size() - 1, selectedFileIndex + 1);
        return new PreviewState(files, newIndex, 0);
    }

    public PreviewState scrollUp() {
        return new PreviewState(files, selectedFileIndex, Math.max(0, scrollOffset - 1));
    }

    public PreviewState scrollDown() {
        return new PreviewState(files, selectedFileIndex, scrollOffset + 1);
    }

    public PreviewState withSelectedFileIndex(int index) {
        return new PreviewState(files, index, 0);
    }

    public GeneratedFile selectedFile() {
        if (files.isEmpty() || selectedFileIndex >= files.size()) return null;
        return files.get(selectedFileIndex);
    }
}
