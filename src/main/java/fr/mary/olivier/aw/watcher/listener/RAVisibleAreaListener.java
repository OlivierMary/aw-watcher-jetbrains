package fr.mary.olivier.aw.watcher.listener;


import com.intellij.openapi.editor.event.VisibleAreaEvent;
import com.intellij.openapi.editor.event.VisibleAreaListener;
import fr.mary.olivier.aw.watcher.ReportActivity;
import org.jetbrains.annotations.NotNull;

public class RAVisibleAreaListener implements VisibleAreaListener {
    @Override
    public void visibleAreaChanged(@NotNull VisibleAreaEvent visibleAreaEvent) {
        ReportActivity.stayOnFile(visibleAreaEvent.getEditor());
    }
}