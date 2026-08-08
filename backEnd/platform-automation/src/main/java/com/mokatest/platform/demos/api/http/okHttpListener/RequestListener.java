package com.mokatest.platform.demos.api.http.okHttpListener;

import okhttp3.Request;

import java.util.Map;

public interface RequestListener {
    void onRequestBeforeSend(Request request, Map<String, String> requestHeaders);
}
