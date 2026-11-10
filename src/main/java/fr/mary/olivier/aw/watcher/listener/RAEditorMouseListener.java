package fr.mary.olivier.aw.watcher.listener;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import fr.mary.olivier.aw.watcher.ReportActivity;
import org.jetbrains.annotations.NotNull;

public class RAEditorMouseListener implements EditorMouseListener {
    @Override
    public void mousePressed(@NotNull EditorMouseEvent editorMouseEvent) {
        Editor editor = editorMouseEvent.getEditor();
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        Project project = editor.getProject();
        ReportActivity.sendHeartBeat(file, project);
    }
}
