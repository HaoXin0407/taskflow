package com.taskflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.taskflow.common.Result;
import com.taskflow.entity.SysPermission;
import com.taskflow.entity.SysRole;
import com.taskflow.entity.SysUser;
import com.taskflow.mapper.SysPermissionMapper;
import com.taskflow.mapper.SysRoleMapper;
import com.taskflow.mapper.SysUserMapper;
import com.taskflow.mapper.SysUserRoleMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 【调试接口】权限与菜单排查工具。
 *
 * <p><b>用途</b>：当用户登录后左侧功能栏空白 / 菜单不显示时，
 * 调这个接口能看到完整的数据链路，而不是在代码里瞎猜。</p>
 *
 * <p>用法：<code>GET /api/debug/permission?username=zhangsan</code>
 * 不传 username 则一次性输出所有用户。</p>
 *
 * <p><b>安全提示</b>：本接口会暴露权限表结构，
 * 生产环境务必通过 profile 或注解关闭（例如类上加
 * {@code @Profile("dev")}），切勿直接上线。</p>
 */
@Tag(name = "00-调试工具")
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
public class DebugController {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    /**
     * 前端路由表，与 frontend/src/router/index.js 的 businessRoutes 保持一致。
     * <p>放在这里是为了让接口能模拟前端 getMenuRoutes 的过滤过程，
     * 从而直接告诉开发者「某菜单为什么没显示」。</p>
     */
    private static final Map<String, String[]> FRONTEND_ROUTES = new LinkedHashMap<>();
    /** 前端菜单 → 所需权限（数组为「满足其一即可」） */
    private static final Map<String, Boolean> ROUTE_HIDDEN = new LinkedHashMap<>();

    static {
        //  key=路径, value=所需权限（逗号分隔，空表示所有人可见）
        FRONTEND_ROUTES.put("dashboard", new String[]{});
        FRONTEND_ROUTES.put("ticket/list", new String[]{"ticket:list"});
        FRONTEND_ROUTES.put("approval/list", new String[]{"approval:list"});
        FRONTEND_ROUTES.put("notice/list", new String[]{"notice:list"});
        FRONTEND_ROUTES.put("system/user", new String[]{"system:user"});
        FRONTEND_ROUTES.put("system/log", new String[]{"system:log"});
        // 内部路由，前端 hidden=true，不显示在侧边栏
        ROUTE_HIDDEN.put("ticket/detail/:id", true);
        ROUTE_HIDDEN.put("profile", true);
    }

    @Operation(summary = "调试：查看用户的角色/权限/菜单过滤结果")
    @GetMapping("/permission")
    public Result<Map<String, Object>> permissionDebug(
            @RequestParam(required = false) String username) {

        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> userReports = new ArrayList<>();

        List<SysUser> users = (username == null || username.isBlank())
                ? userMapper.selectList(new QueryWrapper<SysUser>().orderByAsc("id"))
                : List.of(userMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username)));

        for (SysUser user : users) {
            if (user == null) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "用户不存在: " + username);
                userReports.add(err);
                continue;
            }
            userReports.add(buildReport(user));
        }

        result.put("说明",
                "menuVisible=true 表示前端侧边栏会显示；false 表示被权限过滤掉了。"
                        + "若所有菜单都是 false，说明该用户 permissions 为空，"
                        + "请检查 sys_role_permission 表中是否为其角色分配了权限。");
        result.put("users", userReports);
        return Result.success(result);
    }

    /**
     * 组装单个用户的权限诊断报告。
     */
    private Map<String, Object> buildReport(SysUser user) {
        Map<String, Object> r = new LinkedHashMap<>();

        r.put("userId", user.getId());
        r.put("username", user.getUsername());
        r.put("nickname", user.getNickname());
        r.put("status", user.getStatus());

        // ---- 1. 角色 ----
        // 用关联查询而非 inSql 拼接，避免字符串拼 SQL 的写法
        List<SysRole> roles = roleMapper.selectRolesByUserId(user.getId());
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).toList();
        r.put("roles", roleCodes);
        r.put("roleIds", roles.stream().map(x -> x.getId()).toList());
        r.put("isAdmin", roleCodes.contains("ADMIN"));

        // ---- 2. 权限标识 ----
        List<String> permCodes = userMapper.selectPermCodesByUserId(user.getId());
        r.put("permissions", permCodes);
        r.put("permissionCount", permCodes.size());

        if (permCodes.isEmpty()) {
            r.put("警告", "该用户没有任何权限标识！侧边栏将完全空白。"
                    + "请检查 sys_role_permission 表中是否为其角色 role_id="
                    + roles.stream().map(x -> String.valueOf(x.getId())).toList()
                    + " 配置了权限记录。");
        }

        // ---- 3. 菜单权限（M/C 类型，前端菜单树用） ----
        List<SysPermission> menus = permissionMapper.selectMenuByUserId(user.getId());
        r.put("menuPermCount", menus.size());

        // ---- 4. 逐个菜单模拟前端过滤 ----
        List<Map<String, Object>> routeChecks = new ArrayList<>();
        boolean isAdmin = roleCodes.contains("ADMIN");

        FRONTEND_ROUTES.forEach((path, requiredPerms) -> {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("route", path);

            boolean visible;
            String reason;
            if (requiredPerms.length == 0) {
                visible = true;
                reason = "无权限要求，所有登录用户可见";
            } else if (isAdmin) {
                visible = true;
                reason = "管理员放行全部权限";
            } else if (permCodes.contains(requiredPerms[0])) {
                visible = true;
                reason = "拥有权限 " + requiredPerms[0];
            } else {
                visible = false;
                reason = "缺少权限 " + requiredPerms[0] + " → 被过滤";
            }

            c.put("requiredPerm", requiredPerms.length == 0 ? "(无)" : requiredPerms[0]);
            c.put("menuVisible", visible);
            c.put("reason", reason);
            routeChecks.add(c);
        });
        r.put("routeChecks", routeChecks);

        return r;
    }
}