package fr.mary.olivier.aw.watcher;

import com.intellij.openapi.diagnostic.Logger;
import io.swagger.client.ApiCallback;
import io.swagger.client.ApiException;

import java.util.List;
import java.util.Map;

public class ReportActivityCallBack implements ApiCallback<Void> {
    private static final Logger LOG = Logger.getInstance(ReportActivity.class.getName());

    @Override
    public void onFailure(ApiException exp, int statusCode, Map<String, List<String>> responseHeaders) {
        LOG.error("ReportActivity callback failure.", exp);
    }

    @Override
    public void onSuccess(Void result, int statusCode, Map<String, List<String>> responseHeaders) {
        // no action
    }

    @Override
    public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
        // no action

    }

    @Override
    public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
        // no action
    }
};