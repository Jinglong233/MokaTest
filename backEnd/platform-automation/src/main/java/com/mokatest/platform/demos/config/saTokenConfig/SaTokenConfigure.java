package com.mokatest.platform.demos.config.saTokenConfig;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SaToken 全局拦截器配置
 *
 * 注册顺序：
 * <ol>
 *   {@link TeamContextInterceptor}：先解析并设置当前请求的 teamId，供后续 @SaCheckPermission 使用
 *   {@link SaInterceptor}：登录校验，同时触发 @SaCheckPermission 权限注解
 * </ol>
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Autowired
    private TeamContextInterceptor teamContextInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 团队上下文拦截器：最先执行，确保 @SaCheckPermission 能读到 teamId
        registry.addInterceptor(teamContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/user/register")
                .excludePathPatterns("/error")
                .excludePathPatterns("/file/image/**")
                .excludePathPatterns("/file/preview/**")
                .excludePathPatterns("/file/**");

        // 2. SaToken 登录校验拦截器，同时处理 @SaCheckPermission 权限注解
        //    注意：SSE（SseEmitter）完成时容器会以 ASYNC 类型二次分发请求，
        //    此时 SaToken ThreadLocal 上下文已不存在，必须直接放行（初次分发已校验过）
        registry.addInterceptor(new SaInterceptor(handle -> {
                    SaRouter.notMatch(SaHttpMethod.OPTIONS)
                            .check(r -> StpUtil.checkLogin());
                }) {
                    @Override
                    public boolean preHandle(jakarta.servlet.http.HttpServletRequest request,
                                             jakarta.servlet.http.HttpServletResponse response,
                                             Object handler) throws Exception {
                        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC) {
                            return true;
                        }
                        return super.preHandle(request, response, handler);
                    }
                })
                .addPathPatterns("/**")
                .excludePathPatterns("/user/login")
                .excludePathPatterns("/user/register")
                .excludePathPatterns("/error")
                .excludePathPatterns("/file/image/**")
                .excludePathPatterns("/file/preview/**")
                .excludePathPatterns("/file/**");
    }
}
