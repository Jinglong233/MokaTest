package com.mokatest.platform.demos.api.http.executor;

import com.mokatest.platform.demos.api.domain.apiEnum.RequestMethod;
import com.mokatest.platform.demos.api.http.executor.impl.*;
import com.mokatest.platform.demos.api.service.FileUploadService;
import okhttp3.OkHttpClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class RequestExecutorFactory {
    private final Map<RequestMethod, RequestExecutor> executorMap;

    public RequestExecutorFactory(OkHttpClient okHttpClient, FileUploadService fileUploadService) {
        executorMap = new EnumMap<>(RequestMethod.class);
        executorMap.put(RequestMethod.GET, new GetRequestExecutor(okHttpClient, fileUploadService));
        executorMap.put(RequestMethod.POST, new PostRequestExecutor(okHttpClient, fileUploadService));
        executorMap.put(RequestMethod.PUT, new PutRequestExecutor(okHttpClient, fileUploadService));
        executorMap.put(RequestMethod.DELETE, new DeleteRequestExecutor(okHttpClient, fileUploadService));
        executorMap.put(RequestMethod.PATCH, new PatchRequestExecutor(okHttpClient, fileUploadService));
    }

    public RequestExecutor getExecutor(RequestMethod method) {
        return executorMap.get(method);
    }

    public void registerExecutor(RequestMethod method, RequestExecutor executor) {
        executorMap.put(method, executor);
    }
}
