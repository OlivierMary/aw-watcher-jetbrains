package fr.mary.olivier.aw.watcher.jetbrains;

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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import fr.mary.olivier.aw.watcher.jetbrains.listener.RADocumentListener;
import fr.mary.olivier.aw.watcher.jetbrains.listener.RAEditorMouseListener;
import fr.mary.olivier.aw.watcher.jetbrains.listener.RASaveListener;
import fr.mary.olivier.aw.watcher.jetbrains.listener.RAVisibleAreaListener;
import io.swagger.client.ApiException;
import io.swagger.client.api.DefaultApi;
import io.swagger.client.model.Bucket;
import io.swagger.client.model.CreateBucket;
import io.swagger.client.model.EditorActivityEvent;
import io.swagger.client.model.Event;
import org.jetbrains.annotations.SystemIndependent;
import org.threeten.bp.OffsetDateTime;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class ReportActivity implements Disposable {

    private static final Logger LOG = Logger.getInstance(ReportActivity.class.getName());
    private static final String BUCKET_CLIENT_NAME = "aw-watcher-jetbrains";
    private static final String TYPE = "app.editor.activity";
    private static final BigDecimal MAX_STAY_TIME = new BigDecimal(2 * 60);
    private static final ReportActivityCallBack REPORT_ACTIVITY_CALL_BACK = new ReportActivityCallBack();
    public static final String ACTIVITY_WATCHER = "Activity Watcher";

    private static String ide;
    private static String ideVersion;
    private static MessageBusConnection connection;
    private static DefaultApi apiClient;
    private static Bucket bucket;
    private static String lastFile = null;
    private static BigDecimal lastTime = getCurrentTimestamp();
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> scheduledFixture;
    private static boolean connexionFailedMessageAlreadySend = false;

    ReportActivity() {
        LOG.info("Initializing ActivityWatcher plugin : Start");
        initIDEInfo();
        setupConnexionToApi();
    }

    private static void initIDEInfo() {
        ideVersion = ApplicationInfo.getInstance().getFullVersion();
        ide = PlatformUtils.getPlatformPrefix();
    }

    private static void setupConnexionToApi() {
        final Runnable handler = () -> {
            initClient();

            if (bucket == null) {
                LOG.info("Bucket null, no activity will be send.");
                LOG.info("Initializing ActivityWatcher plugin : FAIL");
                if (!connexionFailedMessageAlreadySend) {
                    Notifications.Bus.notify(new Notification(ACTIVITY_WATCHER, ACTIVITY_WATCHER,
                            "Activity Watcher Server not found, server is started ?\n " +
                                    "Will try to reconnect all 60s.", NotificationType.WARNING));
                    connexionFailedMessageAlreadySend = true;
                }
            } else {
                setupEventListeners();
                scheduledFixture.cancel(false);
                Notifications.Bus.notify(new Notification(ACTIVITY_WATCHER, ACTIVITY_WATCHER,
                        "Activity Watcher Server Connected.", NotificationType.INFORMATION));
                LOG.info("Initializing ActivityWatcher plugin : OK");
            }
        };
        scheduledFixture = scheduler.scheduleWithFixedDelay(handler, 0, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void initClient() {
        apiClient = new DefaultApi();

        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exp) {
            LOG.error("Unable to get hostname", exp);
            hostname = "unknown";
        }

        try {
            bucket = apiClient.getBucketResource(BUCKET_CLIENT_NAME + "_" + hostname, null);
        } catch (ApiException exp) {
            CreateBucket nb = new CreateBucket();
            nb.setClient(BUCKET_CLIENT_NAME);
            try {
                nb.setHostname(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException e1) {
                nb.setHostname("unknown");
            }
            nb.setType(TYPE);
            try {
                apiClient.postBucketResource(BUCKET_CLIENT_NAME + "_" + hostname, nb);
                bucket = apiClient.getBucketResource(BUCKET_CLIENT_NAME + "_" + hostname, null);
            } catch (ApiException expBis) {
                LOG.warn("Unable to init bucket", expBis);
            }
        }
    }

    private static void setupEventListeners() {
        ApplicationManager.getApplication().invokeLater(() -> {
            // Auto save
            MessageBus bus = ApplicationManager.getApplication().getMessageBus();
            connection = bus.connect();
            connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new RASaveListener());
            // Switch document or in document
            EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new RADocumentListener());
            EditorFactory.getInstance().getEventMulticaster().addEditorMouseListener(new RAEditorMouseListener());
            EditorFactory.getInstance().getEventMulticaster().addVisibleAreaListener(new RAVisibleAreaListener());
        });
    }

    @Override
    public void dispose() {
        try {
            connection.disconnect();
        } catch (Exception e) {
            LOG.error("Unable to disconnect", e);
        }
        try {
            scheduledFixture.cancel(true);
        } catch (Exception e) {
            LOG.error("Unable to cancel scheduler", e);
        }
    }

    public static void stayOnFile(Editor editor) {
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
        Project project = editor.getProject();
        sendEvent(file, project, false);
    }

    private static boolean longEnougthToLog(BigDecimal now) {
        return lastTime.add(MAX_STAY_TIME).compareTo(now) < 0;
    }

    public static void sendEvent(final VirtualFile file, Project project, final boolean activity) {
        final BigDecimal time = getCurrentTimestamp();
        if (file == null || !activity && file.getPath().equals(lastFile) && !longEnougthToLog(time)) {
            return;
        }
        final BigDecimal duration = time.subtract(lastTime);
        lastFile = file.getPath();
        lastTime = time;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            @SystemIndependent String projectPath = project != null && project.getBasePath() != null ? project.getBasePath() : "";
            Event event = new Event(duration,
                    new EditorActivityEvent(
                            file.getPath().replace(projectPath, ""),
                            project != null ? project.getName() : null,
                            projectPath,
                            getLanguage(file),
                            ide, ideVersion),
                    OffsetDateTime.now());
            try {
                apiClient.postEventsResourceAsync(bucket.getId(), event, REPORT_ACTIVITY_CALL_BACK);
            } catch (ApiException exp) {
                LOG.error("Error sending Activity report event", exp);
            }
        });
    }


    public static Project getProject(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    private static String getLanguage(final VirtualFile file) {
        FileType type = file.getFileType();
        return type.getName();
    }

    private static BigDecimal getCurrentTimestamp() {
        return new BigDecimal(String.valueOf(System.currentTimeMillis() / 1000.0)).setScale(4, BigDecimal.ROUND_HALF_UP);
    }
}
