package com.taskflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 */
@Data
public class LoginDTO {

    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 20, message = "用户名长度为 3-20 个字符")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}