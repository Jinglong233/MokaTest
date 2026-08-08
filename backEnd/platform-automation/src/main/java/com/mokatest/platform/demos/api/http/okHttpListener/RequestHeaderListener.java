package com.mokatest.platform.demos.api.http.okHttpListener;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Request;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 请求头监听器
 */
public class RequestHeaderListener extends EventListener {
    private final  Map<String, String> allRequestHeaders = new ConcurrentHashMap<>();

    @Override
    public void requestHeadersStart(Call call) {
        System.out.println("请求即将发送，可以在此时获取请求头");
    }

    @Override
    public void requestHeadersEnd(Call call, Request request) {
        // 捕获请求头
        for (int i = 0; i < request.headers().size(); i++) {
            allRequestHeaders.put(request.headers().name(i), request.headers().value(i));
        }
    }

    public Map<String, String> getRequestHeaders() {
        return allRequestHeaders;
    }
}
