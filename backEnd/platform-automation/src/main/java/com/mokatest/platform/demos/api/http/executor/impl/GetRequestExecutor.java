package com.mokatest.platform.demos.api.http.executor.impl;

import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.service.FileUploadService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component
public class GetRequestExecutor extends AbstractRequestExecutor {

    public GetRequestExecutor(OkHttpClient okHttpClient, FileUploadService fileUploadService) {
        super(okHttpClient, fileUploadService);
    }

    @Override
    protected void configureRequestMethod(Request.Builder builder, RequestBody requestBody) {
        builder.get();
    }

    @Override
    protected RequestBody buildRequestBody(ApiRequest request) {
        return RequestBody.create(null, new byte[0]);
    }

    @Override
    protected String getMethodName() {
        return "GET";
    }
}
