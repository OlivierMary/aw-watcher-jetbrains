package fr.mary.olivier.aw.watcher.jetbrains;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;

public class InfoAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        Messages.showMessageDialog("Activity Watcher for JetBrains IDEs\nVersion: " + Version.getVersion(),
                "Activity Watcher for JetBrains IDEs", Messages.getInformationIcon());
    }
}
