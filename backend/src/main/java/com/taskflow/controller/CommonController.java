package com.taskflow.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taskflow.common.Result;
import com.taskflow.entity.SysUser;
import com.taskflow.mapper.SysUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 公共用户查询接口 —— 供「选择审批人」等业务场景使用。
 *
 * <p><b>为什么需要这个接口？</b>（本项目的真实设计缺陷）</p>
 *
 * <p>发起审批时需要选择审批人，前端最初调用的是
 * {@code GET /system/user/list}。但那个接口挂在 UserController 上，
 * 受 {@code system:user} 权限保护 —— 那是「用户管理」模块的权限，
 * 只有管理员才有。结果普通员工发起审批时：</p>
 * <ul>
 *     <li>接口返回 403</li>
 *     <li>审批人下拉框「无数据」</li>
 *     <li>不选审批人又无法提交 → 死锁，功能完全不可用</li>
 * </ul>
 *
 * <p><b>正确的设计</b>：把「业务场景需要的用户查询」和「管理功能的用户管理」分开。</p>
 * <ul>
 *     <li>{@code /system/user/list} —— 管理场景，需要 {@code system:user}，
 *         返回用户完整信息</li>
 *     <li>{@code /common/users} —— 业务场景，所有登录用户可用，
 *         <b>只返回 id / username / nickname / dept 四个必要字段</b></li>
 * </ul>
 *
 * <p><b>安全性说明</b>：这不构成权限绕过，因为返回的是脱敏后的公开信息，
 * 且真正的操作安全仍由审批流的状态机校验保障 ——
 * 能不能审批某张单子，取决于 {@code ap_approval_node} 里该用户是否为当前节点审批人，
 * 而不是他能不能看到用户列表。</p>
 *
 * <p><b>面试可讲</b>：这是典型的「接口粒度设计」问题。
 * 一个接口承担了两种语义（管理 vs 业务），导致该授权的没授权、不该授权的被误伤。
 * 正确做法是按使用场景拆分接口，而不是靠角色硬套。</p>
 */
@Tag(name = "02-公共查询")
@RestController
@RequestMapping("/common")
@RequiredArgsConstructor
public class CommonController {

    private final SysUserMapper userMapper;

    /**
     * 查询启用中的用户（供选择审批人、指派人等业务场景使用）。
     *
     * <p>只返回必要字段，规避隐私泄露。</p>
     */
    @Operation(summary = "查询启用中的用户（选择审批人/指派人用，所有登录用户可访问）")
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> users() {
        List<SysUser> users = userMapper.selectList(
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getId));

        List<Map<String, Object>> result = users.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            // 只暴露业务必需字段，不返回手机号、邮箱、密码等
            m.put("userId", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("dept", u.getDept());
            return m;
        }).collect(Collectors.toList());

        return Result.success(result);
    }
}