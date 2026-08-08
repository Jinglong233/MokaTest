package com.mokatest.platform.demos.api.http.config;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

public class SSLConfig {
    private final boolean trustAll;
    private final SSLContext sslContext;

    private SSLConfig(boolean trustAll, SSLContext sslContext) {
        this.trustAll = trustAll;
        this.sslContext = sslContext;
    }

    public static SSLConfig defaultConfig() {
        return new SSLConfig(false, null);
    }

    public static SSLConfig trustAll() {
        return new SSLConfig(true, null);
    }

    public static SSLConfig custom(SSLContext context) {
        return new SSLConfig(false, context);
    }

    public boolean isTrustAll() { return trustAll; }
    public SSLContext getSslContext() { return sslContext; }

    public static SSLContext createTrustAllSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        return sc;
    }
}
