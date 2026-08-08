package com.mokatest.platform.demos.api.http.config;

public class ProxyConfig {
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    public ProxyConfig(String host, int port) {
        this(host, port, null, null);
    }

    public ProxyConfig(String host, int port, String username, String password) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    // getters...
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean hasAuth() { return username != null && password != null; }
}
