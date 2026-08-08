package com.mokatest.platform.demos.api.http.executor.impl;

import com.mokatest.platform.demos.api.domain.apiEnum.RequestMethod;
import com.mokatest.platform.demos.api.service.FileUploadService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Component
public class PatchRequestExecutor extends AbstractRequestExecutor {

    public PatchRequestExecutor(OkHttpClient okHttpClient, FileUploadService fileUploadService) {
        super(okHttpClient, fileUploadService);
    }

    @Override
    protected void configureRequestMethod(Request.Builder builder, RequestBody requestBody) {
        builder.patch(requestBody);
    }

    @Override
    protected String getMethodName() {
        return RequestMethod.PATCH.name();
    }
}
