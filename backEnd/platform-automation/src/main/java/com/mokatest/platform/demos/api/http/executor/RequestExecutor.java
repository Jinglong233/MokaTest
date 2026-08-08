package com.mokatest.platform.demos.api.http.executor;


import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;

public interface RequestExecutor {
    TestHttpResponse execute(ApiRequest request);
}
