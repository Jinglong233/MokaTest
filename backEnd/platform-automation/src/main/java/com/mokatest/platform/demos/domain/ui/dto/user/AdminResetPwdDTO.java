package com.mokatest.platform.demos.domain.ui.dto.user;

import lombok.Data;

@Data
public class AdminResetPwdDTO {
    private Long userId;
    private String newPassword;
}
