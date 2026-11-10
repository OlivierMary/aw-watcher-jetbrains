package fr.mary.olivier.aw.watcher.listener;

import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import fr.mary.olivier.aw.watcher.ReportActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Starts the {@link ReportActivity} service (connection to ActivityWatch) when the first editor opens,
 * without waiting for a first keystroke or click.
 */
public class RAEditorFactoryListener implements EditorFactoryListener {

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        ReportActivity.getInstance();
    }
}
