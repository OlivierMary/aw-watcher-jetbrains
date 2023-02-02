package fr.mary.olivier.aw.watcher;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode()
@ToString(callSuper = true)
@Builder
@Data
public class HeartBeatData {
    private String file;
    private String fileFullPath;
    private String project;
    private String projectPath;
    private String language;
    private String editor;
    private String editorVersion;
    private String eventType;
    private String branch;
    private String commit;
    private String state;
    private String sourceUrl;

}

