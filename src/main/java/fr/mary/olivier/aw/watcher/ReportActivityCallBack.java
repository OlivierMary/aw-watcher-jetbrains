package fr.mary.olivier.aw.watcher;


import org.openapitools.client.ApiCallback;
import org.openapitools.client.ApiException;
import org.openapitools.client.model.Event;

import java.util.List;
import java.util.Map;

public class ReportActivityCallBack implements ApiCallback<Void> {

    private Event event;

    ReportActivityCallBack(Event event) {
        this.event = event;
    }

    @Override
    public void onFailure(ApiException exp, int statusCode, Map<String, List<String>> responseHeaders) {
        ReportActivity.connexionLost();
    }

    @Override
    public void onSuccess(Void result, int statusCode, Map<String, List<String>> responseHeaders) {
        ReportActivity.connexionResume();
        ReportActivity.eventSent(event);
    }

    @Override
    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
        // no action

    }

    @Override
    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
        // no action
    }
}
