package fr.mary.olivier.aw.watcher;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public class InfoAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Messages.showMessageDialog("Activity Watcher\nVersion: " + Version.getVersion(),
                "Activity Watcher", Messages.getInformationIcon());
    }
}
