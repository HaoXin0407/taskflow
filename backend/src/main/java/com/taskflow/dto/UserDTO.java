package com.taskflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户新增 / 修改参数。
 */
@Data
public class UserDTO {

    /** 修改时必填 */
    private Long id;

    @NotBlank(message = "请输入用户名")
    private String username;

    /** 新增时必填；修改时留空表示不修改密码 */
    private String password;

    @NotBlank(message = "请输入昵称")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String dept;

    /** 1 正常 0 禁用 */
    private Integer status;

    private String remark;

    /** 角色ID列表 */
    private java.util.List<Long> roleIds;
}