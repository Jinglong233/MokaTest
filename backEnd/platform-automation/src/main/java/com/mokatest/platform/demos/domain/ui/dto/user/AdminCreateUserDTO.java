package com.mokatest.platform.demos.domain.ui.dto.user;

import lombok.Data;

@Data
public class AdminCreateUserDTO {
    private String username;
    private String password;
    private String nickname;
    private String email;
    private String phone;
}
