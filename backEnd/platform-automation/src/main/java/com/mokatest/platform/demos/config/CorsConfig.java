package com.mokatest.platform.demos.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 跨域配置
 *
 * 使用 FilterRegistrationBean 显式注册 CORS 过滤器，确保在过滤器链最前端处理跨域。
 * 对 OPTIONS 预检请求直接返回 200，不再继续走后续过滤器链。
 */
@Configuration
public class CorsConfig {

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter());
        registration.setOrder(Integer.MIN_VALUE);
        registration.addUrlPatterns("/*");
        registration.setName("corsFilter");
        return registration;
    }

    public static class CorsFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            String origin = request.getHeader("Origin");
            if (origin != null && !origin.isEmpty()) {
                response.setHeader("Access-Control-Allow-Origin", origin);
            } else {
                response.setHeader("Access-Control-Allow-Origin", "*");
            }
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH");
            response.setHeader("Access-Control-Allow-Headers", "*");
//            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Max-Age", "3600");
            response.setHeader("Access-Control-Expose-Headers", "*");

            // OPTIONS 预检请求直接返回 200
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            filterChain.doFilter(request, response);
        }
    }
}
