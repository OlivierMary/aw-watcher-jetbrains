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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PlatformUtils;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.messages.MessageBusConnection;
import fr.mary.olivier.aw.watcher.listener.RADocumentListener;
import fr.mary.olivier.aw.watcher.listener.RAEditorMouseListener;
import fr.mary.olivier.aw.watcher.listener.RASaveListener;
import fr.mary.olivier.aw.watcher.listener.RAVisibleAreaListener;
import fr.mary.olivier.aw.watcher.model.EditorActivityEvent;
import org.jetbrains.annotations.SystemIndependent;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.DefaultApi;
import org.openapitools.client.model.Bucket;
import org.openapitools.client.model.CreateBucket;
import org.openapitools.client.model.Event;
import org.threeten.bp.OffsetDateTime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class ReportActivity implements Disposable {

    private static final String ACTIVITY_WATCHER = "Activity Watcher";
    private static final Logger LOG = Logger.getInstance(ReportActivity.class.getName());
    private static final String TYPE = "app.editor.activity";
    private static final BigDecimal MAX_STAY_TIME = new BigDecimal(2 * 60);
    private static final BigDecimal MAX_RETRY_TIME = new BigDecimal(30);
    private static final String AW_WATCHER = "aw-watcher-";

    private static String ide;
    private static String ideVersion;
    private static String bucketClientNamePrefix;
    private static MessageBusConnection connection;
    private static DefaultApi apiClient;
    private static Bucket bucket;
    private static String lastFile = null;
    private static BigDecimal lastTime = getCurrentTimestamp();
    private static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> scheduledFixture;
    private static boolean connexionFailedMessageAlreadySend = false;
    private static boolean connexionLost = false;
    private static BigDecimal lastFailed = getCurrentTimestamp();
    private static List<Event> eventsToSend = new ArrayList<>();
    private static String lastFileType;
    private static Project lastProject;

    ReportActivity() {
        LOG.info("Initializing ActivityWatcher plugin : Start");
        initIDEInfo();
        setupConnexionToApi();
        setupEventListeners();
    }

    private static void initIDEInfo() {
        ideVersion = ApplicationInfo.getInstance().getFullVersion();
        ide = PlatformUtils.getPlatformPrefix();
        bucketClientNamePrefix = AW_WATCHER + ide;
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
            bucket = apiClient.getBucketResource(bucketClientNamePrefix + "_" + hostname);
        } catch (ApiException exp) {
            CreateBucket nb = new CreateBucket();
            nb.setClient(bucketClientNamePrefix);
            try {
                nb.setHostname(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException e1) {
                nb.setHostname("unknown");
            }
            nb.setType(TYPE);
            try {
                apiClient.postBucketResource(bucketClientNamePrefix + "_" + hostname, nb);
                bucket = apiClient.getBucketResource(bucketClientNamePrefix + "_" + hostname);
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

    private static boolean longEnougthToLog(BigDecimal now) {
        return lastTime.add(MAX_STAY_TIME).compareTo(now) < 0;
    }

    private static boolean longEnougthToRetry() {
        return lastFailed.add(MAX_RETRY_TIME).compareTo(getCurrentTimestamp()) < 0;
    }

    public static void addAndSendEvent(final VirtualFile file, Project project, Class clazz) {
        synchronized (eventsToSend) {
            final BigDecimal time = getCurrentTimestamp();
            if (file == null || file.getPath().equals(lastFile) && !longEnougthToLog(time)
                    || eventsToSend.stream().anyMatch(e -> file.getPath().endsWith(((EditorActivityEvent) e.getData()).getFile()))) {
                return;
            }
            if (lastFile == null){
                initLastFile(file, project, time);
                return;
            }

            final BigDecimal duration = time.subtract(lastTime);
            @SystemIndependent final String projectPath = lastProject != null && lastProject.getBasePath() != null ? lastProject.getBasePath() : "";
            Event event = new Event()
                .duration(duration)
                .data(new EditorActivityEvent(
                        lastFile.replace(projectPath,""),
                        lastProject != null ? lastProject.getName() : null,
                        projectPath,
                        lastFileType,
                        ide, ideVersion, clazz.getName()))
                .timestamp(OffsetDateTime.now());
            eventsToSend.add(event);
            sendAllEvents();
            initLastFile(file, project, time);
        }
    }

    private static void initLastFile(VirtualFile file, Project project, BigDecimal time) {
        lastProject = project;
        lastFileType = getLanguage(file);
        lastFile = file.getPath();
        lastTime = time;
    }

    static void eventSent(Event event) {
        synchronized (eventsToSend) {
            eventsToSend.remove(event);
        }
    }

    private static void sendAllEvents() {
        if (bucket == null || ReportActivity.isConnexionLost() && !longEnougthToRetry()) {
            return;
        }
        synchronized (eventsToSend) {
            for (Event event : eventsToSend) {
                try {
                    apiClient.postEventsResourceAsync(bucket.getId(), event, new ReportActivityCallBack(event));
                } catch (ApiException exp) {
                    // nothing
                }
            }
        }
    }

    public static Project getProject(Document document) {
        Editor[] editors = EditorFactory.getInstance().getEditors(document);
        if (editors.length > 0) {
            return editors[0].getProject();
        }
        return null;
    }

    static void connexionResume() {
        if (ReportActivity.isConnexionLost()) {
            ReportActivity.setConnexionLost(false);
            Notifications.Bus.notify(new Notification(ReportActivity.ACTIVITY_WATCHER, ReportActivity.ACTIVITY_WATCHER,
                    "Activity Watcher Server is back!", NotificationType.INFORMATION));
        }
    }

    static void connexionLost() {
        if (!ReportActivity.isConnexionLost()) {
            Notifications.Bus.notify(new Notification(ReportActivity.ACTIVITY_WATCHER, ReportActivity.ACTIVITY_WATCHER,
                    "Activity Watcher Server connexion lost?\n " +
                            "Will try to re-send events when connexion back.", NotificationType.WARNING));
        }
        ReportActivity.setConnexionLost(true);
        lastFailed = getCurrentTimestamp();
    }

    private static synchronized boolean isConnexionLost() {
        return connexionLost;
    }

    private static synchronized void setConnexionLost(boolean connexionLost) {
        ReportActivity.connexionLost = connexionLost;
    }

    private static String getLanguage(final VirtualFile file) {
        FileType type = file.getFileType();
        return type.getName();
    }

    private static BigDecimal getCurrentTimestamp() {
        return new BigDecimal(String.valueOf(System.currentTimeMillis() / 1000.0)).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public void dispose() {
        sendAllEvents(); // Try to send lasts event if connexion failed.
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
}
