package fr.mary.olivier.aw.watcher.listener;


import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import fr.mary.olivier.aw.watcher.ReportActivity;
import org.jetbrains.annotations.NotNull;

public class RAEditorMouseListener implements EditorMouseListener {
    @Override
    public void mousePressed(@NotNull EditorMouseEvent editorMouseEvent) {
        ReportActivity.stayOnFile(editorMouseEvent.getEditor());
    }

    @Override
    public void mouseClicked(@NotNull EditorMouseEvent editorMouseEvent) {
        // no action
    }

    @Override
    public void mouseReleased(@NotNull EditorMouseEvent editorMouseEvent) {
        // no action
    }

    @Override
    public void mouseEntered(@NotNull EditorMouseEvent editorMouseEvent) {
        // no action
    }

    @Override
    public void mouseExited(@NotNull EditorMouseEvent editorMouseEvent) {
        // no action
    }
}
