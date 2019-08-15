package fr.mary.olivier.aw.watcher.model;

import com.google.gson.annotations.SerializedName;

public class EditorActivityEvent {
    @SerializedName("file")
    private String file;

    @SerializedName("project")
    private String project;

    @SerializedName("projectPath")
    private String projectPath;

    @SerializedName("language")
    private String language;

    @SerializedName("editor")
    private String editor;

    @SerializedName("editorVersion")
    private String editorVersion;

    @SerializedName("eventType")
    private String eventType;

    public EditorActivityEvent(String file, String project, String projectPath, String language, String editor, String editorVersion, String eventType) {
        this.file = file;
        this.project = project;
        this.projectPath = projectPath;
        this.language = language;
        this.editor = editor;
        this.editorVersion = editorVersion;
        this.eventType = eventType;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getEditor() {
        return editor;
    }

    public void setEditor(String editor) {
        this.editor = editor;
    }

    public String getEditorVersion() {
        return editorVersion;
    }

    public void setEditorVersion(String editorVersion) {
        this.editorVersion = editorVersion;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    @Override
    public String toString() {
        return "EditorActivityEvent{" +
                "file='" + file + '\'' +
                ", project='" + project + '\'' +
                ", projectPath='" + projectPath + '\'' +
                ", language='" + language + '\'' +
                ", editor='" + editor + '\'' +
                ", editorVersion='" + editorVersion + '\'' +
                ", eventType='" + eventType + '\'' +
                '}';
    }
}

