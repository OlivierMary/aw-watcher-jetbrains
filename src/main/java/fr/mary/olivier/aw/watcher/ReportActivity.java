package fr.mary.olivier.aw.watcher;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import fr.mary.olivier.aw.watcher.listener.RAVisibleAreaListener;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.openapitools.client.ApiCallback;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.DefaultApi;
import org.openapitools.client.model.Bucket;
import org.openapitools.client.model.CreateBucket;
import org.openapitools.client.model.Event;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Application service sending the editor activity to ActivityWatch.
 * <p>
 * Created on the first editor event (see {@code plugin.xml}), disposed by the platform when the IDE closes
 * or the plugin is unloaded.
 */
@Service
public final class ReportActivity implements Disposable {

    private static final String ACTIVITY_WATCHER = "Activity Watcher";
    private static final Logger LOG = Logger.getInstance(ReportActivity.class);
    private static final String TYPE = "app.editor.activity";
    private static final String AW_WATCHER = "aw-watcher-";

    private static final String IDE_NAME = ApplicationInfo.getInstance().getVersionName().toLowerCase().replaceAll("\\s", "-");
    private static final String IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();
    public static final int HEARTBEAT_PULSETIME = 20;
    public static final int CHECK_CONNEXION_DELAY = 10;
    public static final int CONNECTION_TIMEOUT = 200000;
    public static final int READ_WRITE_TIMEOUT = 100000;

    private final DefaultApi apiClient = new DefaultApi();
    private final ScheduledFuture<?> scheduledConnexion;
    private volatile Bucket bucket;
    private volatile boolean connexionFailedMessageAlreadySend = false;
    private volatile boolean connexionLost = false;
    private volatile boolean initialCheckDone = false;

    public ReportActivity() {
        LOG.info("Initializing ActivityWatcher plugin : Start");
        apiClient.getApiClient().setConnectTimeout(CONNECTION_TIMEOUT);
        apiClient.getApiClient().setReadTimeout(READ_WRITE_TIMEOUT);
        apiClient.getApiClient().setWriteTimeout(READ_WRITE_TIMEOUT);
        scheduledConnexion = AppExecutorUtil.getAppScheduledExecutorService()
                .scheduleWithFixedDelay(this::checkConnexion, 0, CHECK_CONNEXION_DELAY, TimeUnit.SECONDS);
        // No extension point for visible area listeners: registered here, removed when this service is disposed
        EditorFactory.getInstance().getEventMulticaster().addVisibleAreaListener(new RAVisibleAreaListener(), this);
    }

    public static ReportActivity getInstance() {
        return ApplicationManager.getApplication().getService(ReportActivity.class);
    }

    public static void sendHeartBeat(VirtualFile file, Document document) {
        sendHeartBeat(file, getProject(document));
    }

    public static void sendHeartBeat(VirtualFile file, Project project) {
        getInstance().heartBeat(file, project);
    }

    private static Project getProject(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    private void heartBeat(VirtualFile file, Project project) {
        if (bucket == null || !ApplicationManager.getApplication().isActive() || connexionLost) {
            return;
        }
        if (project == null || project.isDisposed() || !project.isInitialized() || file == null) {
            return;
        }

        String language = file.getFileType().getDisplayName();
        if (file.getFileType() instanceof LanguageFileType) {
            language = ((LanguageFileType) file.getFileType()).getLanguage().getDisplayName();
        }

        HeartBeatData.HeartBeatDataBuilder dataBuilder = HeartBeatData.builder().projectPath(project.getPresentableUrl()).editorVersion(IDE_VERSION).editor(IDE_NAME)
                //.eventType(classz.getName()) disabled for heartbeat because data change and create multiples of event
                .file(file.getPresentableName()).fileFullPath(file.getPath()).project(project.getName()).language(language);
        // Report the repository containing the file (not the first one of a multi-repository project), if any
        GitRepository repository = GitRepositoryManager.getInstance(project).getRepositoryForFileQuick(file);
        if (repository != null) {
            dataBuilder.branch(repository.getCurrentBranchName());
            dataBuilder.commit(repository.getCurrentRevision());
            dataBuilder.state(repository.getState().name());
            repository.getInfo().getRemotes().stream().findFirst().ifPresent(gitRemote -> dataBuilder.sourceUrl(gitRemote.getFirstUrl()));
        }

        Event event = new Event().data(dataBuilder.build()).timestamp(OffsetDateTime.now());

        try {
            apiClient.postHeartbeatResourceAsync(bucket.getId(), event, "" + HEARTBEAT_PULSETIME, new Callback<>() {
                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                    LOG.warn("Unable to send heartbeat:", e);
                    connexionLost();
                }

                @Override
                public void onSuccess(Void result, int statusCode, Map<String, List<String>> responseHeaders) {
                    // nothing to do
                }
            });
        } catch (Exception e) {
            LOG.warn("Unable to send heartbeat:", e);
            connexionLost();
        }
    }

    private void checkConnexion() {
        if (bucket == null) {
            initBucket();
        }

        if (initialCheckDone && bucket == null && !connexionFailedMessageAlreadySend) {
            connexionLost();
        }

        if (initialCheckDone && bucket != null && connexionLost) {
            connexionResume();
        }
    }

    private void initBucket() {
        String bucketClientNamePrefix = AW_WATCHER + IDE_NAME;
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception exp) {
            LOG.warn("Unable to get hostname:", exp);
            hostname = "unknown";
        }
        String bucketId = bucketClientNamePrefix + "_" + hostname;

        try {
            String finalHostname = hostname;
            apiClient.getBucketResourceAsync(bucketId, new Callback<>() {

                @Override
                public void onSuccess(Bucket result, int statusCode, Map<String, List<String>> responseHeaders) {
                    LOG.info("Bucket found");
                    bucket = result;
                    initialCheckDone = true;
                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                    LOG.info("Bucket not found, create it");
                    createBucket(bucketId, bucketClientNamePrefix, finalHostname);
                }
            });
        } catch (Exception exp) {
            LOG.warn("Unable to init bucket:", exp);
            connexionLost = true;
        }
    }

    private void createBucket(String bucketId, String client, String hostname) {
        CreateBucket nb = new CreateBucket();
        nb.setClient(client);
        nb.setHostname(hostname);
        nb.setType(TYPE);
        try {
            apiClient.postBucketResourceAsync(bucketId, nb, new Callback<>() {

                @Override
                public void onSuccess(Void result, int statusCode, Map<String, List<String>> responseHeaders) {
                    LOG.info("Bucket created");
                    try {
                        apiClient.getBucketResourceAsync(bucketId, new Callback<>() {
                            @Override
                            public void onSuccess(Bucket result, int statusCode, Map<String, List<String>> responseHeaders) {
                                LOG.info("Bucket found");
                                bucket = result;
                                initialCheckDone = true;
                            }

                            @Override
                            public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                                LOG.warn("Unable to init bucket:", e);
                                connexionLost = true;
                            }
                        });
                    } catch (ApiException ex) {
                        LOG.warn("Unable to init bucket:", ex);
                    }
                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                    LOG.warn("Unable to create bucket:", e);
                    initialCheckDone = true;
                }
            });
        } catch (Exception expB) {
            LOG.warn("Unable to create bucket:", expB);
            initialCheckDone = true;
        }
    }

    @Override
    public void dispose() {
        scheduledConnexion.cancel(true);
    }

    private synchronized void connexionResume() {
        if (connexionLost) {
            connexionLost = false;
            connexionFailedMessageAlreadySend = false;
            notify("Activity Watcher Server is back!", NotificationType.INFORMATION);
        }
    }

    private synchronized void connexionLost() {
        if (!connexionFailedMessageAlreadySend) {
            connexionFailedMessageAlreadySend = true;
            notify("Activity Watcher Server connection lost?\nWill try to re-send events when connection back.", NotificationType.WARNING);
        }
        connexionLost = true;
        bucket = null;
    }

    private static void notify(String content, NotificationType type) {
        NotificationGroupManager.getInstance().getNotificationGroup(ACTIVITY_WATCHER)
                .createNotification(ACTIVITY_WATCHER, content, type)
                .notify(null);
    }

    /**
     * {@link ApiCallback} ignoring upload/download progress.
     */
    private abstract static class Callback<T> implements ApiCallback<T> {
        @Override
        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
            // nothing to do
        }

        @Override
        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
            // nothing to do
        }
    }
}
