package com.mokatest.platform.demos.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.UserQueryDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.AdminCreateUserDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.AdminResetPwdDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.UpdateUserInfoDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.UserInfoDTO;

/**
 * @author: JingLong
 * @description 针对表【user(用户表)】的数据库操作Service
 * @createDate 2026-03-19 17:00:25
 */
public interface UserService extends IService<User> {

    SaResult register(UserInfoDTO userInfoDTO);

    SaResult login(UserInfoDTO userInfoDTO);

    SaResult getLoginInfo();

    SaResult updateUserInfo(UpdateUserInfoDTO userInfoDTO);

    SaResult updatePwd(String oldPwd, String newPwd);

    SaResult getUserList();

    SaResult getUserListByPage(UserQueryDTO userQueryDTO);

    SaResult getInviteUserList(Long teamId);

    /** 超管新建用户 */
    SaResult adminCreateUser(AdminCreateUserDTO dto);

    /** 超管重置用户密码 */
    SaResult adminResetPwd(AdminResetPwdDTO dto);

    /** 超管启用/禁用用户（禁用时踢出会话） */
    SaResult updateUserStatus(Long userId, Integer status);
}
