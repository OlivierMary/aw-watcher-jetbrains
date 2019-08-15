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

    public EditorActivityEvent(String file, String project, String projectPath, String language, String editor, String editorVersion) {
        this.file = file;
        this.project = project;
        this.projectPath = projectPath;
        this.language = language;
        this.editor = editor;
        this.editorVersion = editorVersion;
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class EditorActivityEvent {\n");

        sb.append("    file: ").append(toIndentedString(file)).append("\n");
        sb.append("    project: ").append(toIndentedString(project)).append("\n");
        sb.append("    projectPath: ").append(toIndentedString(projectPath)).append("\n");
        sb.append("    language: ").append(toIndentedString(language)).append("\n");
        sb.append("    editor: ").append(toIndentedString(editor)).append("\n");
        sb.append("    editorVersion: ").append(toIndentedString(editorVersion)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}

