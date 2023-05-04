package fr.mary.olivier.aw.watcher;

import com.intellij.AppTopics;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import fr.mary.olivier.aw.watcher.listener.RADocumentListener;
import fr.mary.olivier.aw.watcher.listener.RAEditorMouseListener;
import fr.mary.olivier.aw.watcher.listener.RASaveListener;
import fr.mary.olivier.aw.watcher.listener.RAVisibleAreaListener;
import git4idea.GitUtil;
import git4idea.repo.GitRepositoryManager;
import org.openapitools.client.ApiCallback;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.DefaultApi;
import org.openapitools.client.model.Bucket;
import org.openapitools.client.model.CreateBucket;
import org.openapitools.client.model.Event;

import java.awt.*;
import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ReportActivity implements Disposable {

    private static final String ACTIVITY_WATCHER = "Activity Watcher";
    private static final Logger LOG = Logger.getInstance(ReportActivity.class.getName());
    private static final String TYPE = "app.editor.activity";
    private static final String AW_WATCHER = "aw-watcher-";

    private static final String IDE_NAME = ApplicationInfo.getInstance().getVersionName().toLowerCase().replaceAll("\\s", "-");
    private static final String IDE_VERSION = ApplicationInfo.getInstance().getFullVersion();
    public static final int HEARTBEAT_PULSETIME = 20;
    public static final int CHECK_CONNEXION_DELAY = 10;
    private static final DefaultApi API_CLIENT = new DefaultApi();
    public static final int CONNECTION_TIMEOUT = 200000;
    public static final int READ_WRITE_TIMEOUT = 100000;
    private static Bucket bucket;
    private static final ScheduledExecutorService connexionScheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> scheduledConnexion;
    private static boolean connexionFailedMessageAlreadySend = false;
    private static boolean connexionLost = false;
    private static boolean initialCheckDone = false;
    private static MessageBusConnection connection;
    private static ReportActivity instance;


    private ReportActivity() {
        LOG.info("Initializing ActivityWatcher plugin : Start");
        setupConnexionToApi();
        setupEventListeners();
        instance = this;
    }

    private static void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(() -> {
            // Auto save
            MessageBus bus = ApplicationManager.getApplication().getMessageBus();
            connection = bus.connect();
            connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new RASaveListener());
            // Switch document or in document
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new RADocumentListener(), instance);
            EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(new RAEditorMouseListener(), instance);
            EditorFactory.getInstance().getEventMulticaster().addVisibleAreaListener(new RAVisibleAreaListener(), instance);
        });
    }

    public static void sendHeartBeat(VirtualFile file, Document document) {
        sendHeartBeat(file, getProject(document));
    }

    private static Project getProject(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    public static void sendHeartBeat(VirtualFile file, Project project) {
        if (bucket != null && isAppActive() && !connexionLost) {
            if (project == null || !project.isInitialized()) {
                return;
            }
            if (file == null) {
                return;
            }

            String language = file.getFileType().getDisplayName();
            if (file.getFileType() instanceof LanguageFileType) {
                language = ((LanguageFileType) file.getFileType()).getLanguage().getDisplayName();
            }

            HeartBeatData.HeartBeatDataBuilder dataBuilder = HeartBeatData.builder().projectPath(project.getPresentableUrl()).editorVersion(IDE_VERSION).editor(IDE_NAME)
                    //.eventType(classz.getName()) disabled for heartbeat because data change and create multiples of event
                    .file(file.getPresentableName()).fileFullPath(file.getPath()).project(project.getName()).language(language);
            GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
            repositoryManager.getRepositories().stream().findFirst().ifPresent(r -> {
                dataBuilder.branch(r.getCurrentBranchName());
                dataBuilder.commit(r.getCurrentRevision());
                dataBuilder.state(r.getState().name());
                r.getInfo().getRemotes().stream().findFirst().ifPresent(gitRemote -> dataBuilder.sourceUrl(gitRemote.getFirstUrl()));
            });

            HeartBeatData data = dataBuilder.build();
            Event event = new Event().data(data).timestamp(OffsetDateTime.now());

            try {
                API_CLIENT.postHeartbeatResourceAsync(bucket.getId(), event, "" + HEARTBEAT_PULSETIME, new ApiCallback<>() {
                    @Override
                    public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                        LOG.warn("Unable to send heartbeat:", e);
                        ReportActivity.connexionLost();
                    }

                    @Override
                    public void onSuccess(Void result, int statusCode, Map<String, List<String>> responseHeaders) {
                        // nothing to do
                    }

                    @Override
                    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                        // nothing to do
                    }

                    @Override
                    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                        // nothing to do
                    }
                });
            } catch (Exception e) {
                LOG.warn("Unable to send heartbeat:", e);
                ReportActivity.connexionLost();
            }
        }
    }

    private static void initBucket() {
        String bucketClientNamePrefix = AW_WATCHER + IDE_NAME;
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception exp) {
            LOG.warn("Unable to get hostname:", exp);
            hostname = "unknown";
        }

        try {
            String finalHostname = hostname;
            API_CLIENT.getBucketResourceAsync(bucketClientNamePrefix + "_" + hostname, new ApiCallback<>() {

                @Override
                public void onSuccess(Bucket result, int statusCode, Map<String, List<String>> responseHeaders) {
                    LOG.info("Bucket found");
                    bucket = result;
                    initialCheckDone = true;
                }

                @Override
                public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
                    LOG.info("Bucket not found, create it");
                    CreateBucket nb = new CreateBucket();
                    nb.setClient(bucketClientNamePrefix);
                    nb.setHostname(finalHostname);
                    nb.setType(TYPE);
                    try {
                        API_CLIENT.postBucketResourceAsync(bucketClientNamePrefix + "_" + finalHostname, nb, new ApiCallback<>() {

                                    @Override
                                    public void onSuccess(Void result, int statusCode, Map<String, List<String>> responseHeaders) {
                                        LOG.info("Bucket created");
                                        try {
                                            API_CLIENT.getBucketResourceAsync(bucketClientNamePrefix + "_" + finalHostname, new ApiCallback<>() {
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


                                                @Override
                                                public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                                                    // nothing to do
                                                }

                                                @Override
                                                public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                                                    // nothing to do
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

                                    @Override
                                    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                                        // nothing to do
                                    }

                                    @Override
                                    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                                        // nothing to do
                                    }
                                }

                        );
                    } catch (Exception expB) {
                        LOG.warn("Unable to create bucket:", expB);
                        initialCheckDone = true;
                    }
                }


                @Override
                public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
                    // nothing to do
                }

                @Override
                public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
                    // nothing to do
                }
            });
        } catch (Exception exp) {
            LOG.warn("Unable to init bucket:", exp);
            connexionLost = true;
        }
    }

    private static void setupConnexionToApi() {
        API_CLIENT.getApiClient().setConnectTimeout(CONNECTION_TIMEOUT);
        API_CLIENT.getApiClient().setReadTimeout(READ_WRITE_TIMEOUT);
        API_CLIENT.getApiClient().setWriteTimeout(READ_WRITE_TIMEOUT);
        final Runnable handler = () -> {
            if (bucket == null) {
                initBucket();
            }

            if (initialCheckDone && bucket == null && !connexionFailedMessageAlreadySend) {
                connexionLost();
            }

            if (initialCheckDone && bucket != null && connexionLost) {
                connexionResume();
            }
        };
        scheduledConnexion = connexionScheduler.scheduleWithFixedDelay(handler, 0, CHECK_CONNEXION_DELAY, TimeUnit.SECONDS);
    }

    private static boolean isAppActive() {
        return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null;
    }

    @Override
    public void dispose() {
        try {
            connection.disconnect();
        } catch (Exception e) {
            LOG.warn("Unable to disconnect to message bus:", e);
        }
        try {
            scheduledConnexion.cancel(true);
        } catch (Exception e) {
            LOG.warn("Unable to cancel schedulers:", e);
        }
    }

    private static synchronized void connexionResume() {
        if (connexionLost) {
            connexionLost = false;
            connexionFailedMessageAlreadySend = false;
            Notifications.Bus.notify(new Notification(ReportActivity.ACTIVITY_WATCHER, ReportActivity.ACTIVITY_WATCHER, "Activity Watcher Server is back!", NotificationType.INFORMATION));
        }
    }

    protected static synchronized void connexionLost() {
        if (!connexionFailedMessageAlreadySend) {
            connexionFailedMessageAlreadySend = true;
            Notifications.Bus.notify(new Notification(ReportActivity.ACTIVITY_WATCHER, ReportActivity.ACTIVITY_WATCHER, "Activity Watcher Server connection lost?\nWill try to re-send events when connection back.", NotificationType.WARNING));
        }
        connexionLost = true;
        bucket = null;
    }
}
