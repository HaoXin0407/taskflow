package com.taskflow.controller;

import com.taskflow.annotation.OperLog;
import com.taskflow.common.PageResult;
import com.taskflow.common.Result;
import com.taskflow.dto.UserDTO;
import com.taskflow.entity.SysRole;
import com.taskflow.entity.SysUser;
import com.taskflow.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理接口（需 system:user 权限）。
 */
@Tag(name = "04-用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('system:user') or hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @Operation(summary = "分页查询用户")
    @GetMapping("/page")
    public Result<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") Long pageNum,
                                            @RequestParam(defaultValue = "10") Long pageSize,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status) {
        return Result.success(userService.page(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "查询用户详情")
    @GetMapping("/{id}")
    public Result<SysUser> detail(@PathVariable Long id) {
        return Result.success(userService.detail(id));
    }

    @Operation(summary = "查询用户已分配的角色")
    @GetMapping("/{id}/roles")
    public Result<List<Long>> roles(@PathVariable Long id) {
        return Result.success(userService.roleIds(id));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @OperLog(title = "用户管理", businessType = OperLog.BusinessType.INSERT)
    public Result<Void> create(@Valid @RequestBody UserDTO dto) {
        userService.create(dto);
        return Result.success("新增成功", null);
    }

    @Operation(summary = "修改用户")
    @PutMapping
    @OperLog(title = "用户管理", businessType = OperLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody UserDTO dto) {
        userService.update(dto);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    @OperLog(title = "用户管理", businessType = OperLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "重置密码")
    @PostMapping("/{id}/reset")
    @OperLog(title = "重置密码", businessType = OperLog.BusinessType.UPDATE)
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        userService.resetPassword(id, body.getOrDefault("password", "123456"));
        return Result.success("重置成功", null);
    }

    @Operation(summary = "查询所有启用用户（下拉选择用）")
    @GetMapping("/list")
    public Result<List<SysUser>> list() {
        return Result.success(userService.listAllEnabled());
    }

    @Operation(summary = "查询所有启用角色")
    @GetMapping("/roles")
    public Result<List<SysRole>> roles() {
        return Result.success(userService.listRoles());
    }
}