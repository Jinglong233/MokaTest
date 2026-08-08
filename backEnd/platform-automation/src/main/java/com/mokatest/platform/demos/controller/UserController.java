package com.mokatest.platform.demos.controller;


import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.service.FileUploadService;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.UserQueryDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.AdminCreateUserDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.AdminResetPwdDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.UpdateUserInfoDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.UserInfoDTO;
import com.mokatest.platform.demos.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/user/")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private com.mokatest.platform.demos.operationlog.service.SysLoginLogService sysLoginLogService;

    @Resource
    private com.mokatest.platform.demos.mapper.UserMapper userMapper;

    @Resource
    private FileUploadService fileUploadService;


    /**
     * 注册
     *
     * @return
     */
    @RequestMapping("register")
    public SaResult register(@RequestBody UserInfoDTO userInfoDTO) {
        return userService.register(userInfoDTO);
    }

    @RequestMapping("login")
    public SaResult login(@RequestBody UserInfoDTO userInfoDTO) {
        return userService.login(userInfoDTO);
    }

    /**
     * 注销登录
     *
     * @return
     */
    @RequestMapping("logout")
    public SaResult logout() {
        try {
            Long userId = StpUtil.getLoginIdAsLong();
            com.mokatest.platform.demos.domain.ui.User user = userMapper.selectById(userId);
            sysLoginLogService.record("LOGOUT", userId,
                    user != null ? user.getUsername() : null,
                    user != null ? user.getNickname() : null, true, null);
        } catch (Exception ignored) {
            // 登出日志失败不影响登出
        }
        StpUtil.logout();
        return SaResult.ok();
    }


    /**
     * 获取用户信息
     */
    @RequestMapping("getInfo")
    public SaResult getInfo() {
        return userService.getLoginInfo();
    }

    /**
     * 更新用户信息
     */
    @RequestMapping("updateUserInfo")
    public SaResult updateUserInfo(@RequestBody UpdateUserInfoDTO userInfoDTO) {
        return userService.updateUserInfo(userInfoDTO);
    }

    /**
     * 修改密码
     */
    @RequestMapping("updatePwd")
    public SaResult updatePwd(@RequestParam String oldPwd, @RequestParam String newPwd) {
        return userService.updatePwd(oldPwd, newPwd);
    }

    /**
     * 获取用户列表
     */
    @RequestMapping("getUserList")
    public SaResult getUserList() {
        return userService.getUserList();
    }

    /**
     * 分页获取用户列表
     */
    @RequestMapping("getUserListByPage")
    public SaResult getUserListByPage(@RequestBody UserQueryDTO userQueryDTO) {
        return userService.getUserListByPage(userQueryDTO);
    }

    /**
     * 获取可邀请加入团队的用户列表（排除已在团队中的成员）
     * 权限：team:member:manage
     */
    @SaCheckPermission("team:member:manage")
    @RequestMapping("getInviteUserList")
    public SaResult getInviteUserList(@RequestParam Long teamId) {
        return userService.getInviteUserList(teamId);
    }

    /**
     * 超管新建用户
     */
    @RequestMapping("adminCreateUser")
    public SaResult adminCreateUser(@RequestBody AdminCreateUserDTO dto) {
        return userService.adminCreateUser(dto);
    }

    /**
     * 超管重置用户密码
     */
    @RequestMapping("adminResetPwd")
    public SaResult adminResetPwd(@RequestBody AdminResetPwdDTO dto) {
        return userService.adminResetPwd(dto);
    }

    /**
     * 超管启用/禁用用户（禁用后无法登录，现有会话立即失效）
     */
    @PostMapping("updateUserStatus")
    public SaResult updateUserStatus(@RequestParam Long userId, @RequestParam Integer status) {
        return userService.updateUserStatus(userId, status);
    }

    /**
     * 上传头像
     */
    @RequestMapping("uploadAvatar")
    public SaResult uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            // 头像文件大小校验：最大 1MB
            if (file.getSize() > 1 * 1024 * 1024) {
                return SaResult.error("头像文件大小不能超过 1MB");
            }
            // 头像文件格式校验：仅允许图片
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return SaResult.error("头像仅支持图片格式（jpg、png、gif 等）");
            }
            String fileId = fileUploadService.upload(file);
            String avatarUrl = "/api/file/download?fileId=" + java.net.URLEncoder.encode(fileId, java.nio.charset.StandardCharsets.UTF_8);
            Long userId = StpUtil.getLoginIdAsLong();
            com.mokatest.platform.demos.domain.ui.User user = new com.mokatest.platform.demos.domain.ui.User();
            user.setId(userId);
            user.setAvatar(avatarUrl);
            user.setUpdateTime(new java.util.Date());
            userMapper.updateById(user);
            return SaResult.ok("头像上传成功").setData(avatarUrl);
        } catch (Exception e) {
            log.error("头像上传失败", e);
            return SaResult.error("头像上传失败：" + e.getMessage());
        }
    }
}
