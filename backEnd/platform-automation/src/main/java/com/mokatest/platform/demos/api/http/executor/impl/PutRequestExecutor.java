package com.mokatest.platform.demos.api.http.executor.impl;

import com.mokatest.platform.demos.api.service.FileUploadService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component
public class PutRequestExecutor extends AbstractRequestExecutor {

    public PutRequestExecutor(OkHttpClient okHttpClient, FileUploadService fileUploadService) {
        super(okHttpClient, fileUploadService);
    }

    @Override
    protected void configureRequestMethod(Request.Builder builder, RequestBody requestBody) {
        builder.put(requestBody);
    }

    @Override
    protected String getMethodName() {
        return "PUT";
    }
}
