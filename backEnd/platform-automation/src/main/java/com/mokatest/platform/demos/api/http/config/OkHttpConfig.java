package com.mokatest.platform.demos.api.http.config;

import com.mokatest.platform.demos.api.http.executor.impl.AbstractRequestExecutor;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
public class OkHttpConfig {
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                // 整个调用的总超时（含重定向、重试），防止极端情况下请求永久挂起
                .callTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                // 连接池：最大空闲连接数 50，空闲连接存活 5 分钟
                .connectionPool(new ConnectionPool(50, 5, TimeUnit.MINUTES))
                // 注意：不强制指定 protocols，使用 OkHttp 默认行为（HTTPS 自动协商 HTTP/2，HTTP 默认 HTTP/1.1）
                .addNetworkInterceptor(chain -> {
                    Request request = chain.request();

                    // 保存完整请求头到 ThreadLocal
                    Map<String, String> fullHeaders = new HashMap<>();
                    for (int i = 0; i < request.headers().size(); i++) {
                        fullHeaders.put(request.headers().name(i), request.headers().value(i));
                    }
                    AbstractRequestExecutor.FULL_HEADERS.set(fullHeaders);

                    return chain.proceed(request);
                })
                .build();
    }
}
