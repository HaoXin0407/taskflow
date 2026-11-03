package com.taskflow.controller;

import com.taskflow.annotation.OperLog;
import com.taskflow.common.Result;
import com.taskflow.dto.LoginDTO;
import com.taskflow.dto.PasswordDTO;
import com.taskflow.dto.RegisterDTO;
import com.taskflow.entity.SysUser;
import com.taskflow.service.AuthService;
import com.taskflow.service.UserService;
import com.taskflow.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 认证接口。
 */
@Tag(name = "01-认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success("登录成功", authService.login(dto));
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    @OperLog(title = "用户注册", businessType = OperLog.BusinessType.INSERT)
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        authService.register(dto);
        return Result.success("注册成功，请登录", null);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/me")
    public Result<SysUser> me() {
        return Result.success(userService.currentUser());
    }

    @Operation(summary = "修改自己的密码")
    @PostMapping("/password")
    @OperLog(title = "修改密码", businessType = OperLog.BusinessType.UPDATE)
    public Result<Void> changePassword(@Valid @RequestBody PasswordDTO dto) {
        userService.changePassword(dto);
        return Result.success("密码修改成功", null);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        // JWT 无状态，前端删除本地 token 即可，后端无需额外处理
        return Result.success("退出成功", null);
    }
}