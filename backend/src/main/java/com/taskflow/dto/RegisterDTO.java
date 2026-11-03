package com.taskflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数。
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "请输入用户名")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{2,19}$", message = "用户名需以字母开头，可包含字母、数字、下划线，长度 3-20")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 20, message = "密码长度为 6-20 个字符")
    private String password;

    @NotBlank(message = "请输入昵称")
    @Size(max = 20, message = "昵称不能超过 20 个字符")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String dept;
}