package fr.mary.olivier.aw.watcher.listener;


import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import fr.mary.olivier.aw.watcher.ReportActivity;
import org.jetbrains.annotations.NotNull;

public class RADocumentListener implements DocumentListener {
    @Override
    public void beforeDocumentChange(@NotNull DocumentEvent documentEvent) {
        // no action
    }

    @Override
    public void documentChanged(@NotNull DocumentEvent documentEvent) {
        Document document = documentEvent.getDocument();
        VirtualFile file = FileDocumentManager.getInstance().getFile(document);
        ReportActivity.sendEvent(file, ReportActivity.getProject(document), false);
    }
}
