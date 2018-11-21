package fr.mary.olivier.aw.watcher.jetbrains.listener;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import fr.mary.olivier.aw.watcher.jetbrains.ReportActivity;
import org.jetbrains.annotations.NotNull;

public class RASaveListener implements FileDocumentManagerListener {

    @Override
    public void beforeDocumentSaving(@NotNull Document document) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        ReportActivity.sendEvent(file, ReportActivity.getProject(document), true);
    }

    @Override
    public void beforeAllDocumentsSaving() {
        // no action
    }

    @Override
    public void beforeFileContentReload(@NotNull VirtualFile file, @NotNull Document document) {
        // no action
    }

    @Override
    public void fileWithNoDocumentChanged(@NotNull VirtualFile file) {
        // no action
    }

    @Override
    public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
        // no action
    }

    @Override
    public void fileContentLoaded(@NotNull VirtualFile file, @NotNull Document document) {
        // no action
    }

    @Override
    public void unsavedDocumentsDropped() {
        // no action
    }
}
