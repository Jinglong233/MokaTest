package com.mokatest.platform.demos.listener.userListener;

import org.springframework.context.ApplicationEvent;

/**
 * 新增用户事件
 */
public class UserAddEvent extends ApplicationEvent {
    private final String userId;


    public UserAddEvent(Object source, String userId) {
        super(source);
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
