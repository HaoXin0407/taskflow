package com.taskflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskflow.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 权限 Mapper。
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询用户可见的菜单权限（目录 + 菜单，不含按钮）。
     * <p>用于登录后返回给前端构建侧边栏与动态路由。</p>
     */
    @Select("""
            SELECT DISTINCT p.*
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            INNER JOIN sys_role_permission rp ON rp.role_id = r.id
            INNER JOIN sys_permission p ON rp.permission_id = p.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
              AND p.deleted = 0
              AND p.perm_type IN ('M', 'C')
              AND p.visible = 1
            ORDER BY p.parent_id, p.sort
            """)
    List<SysPermission> selectMenuByUserId(@Param("userId") Long userId);

    /**
     * 查询所有菜单权限（管理员用）。
     */
    @Select("""
            SELECT * FROM sys_permission
            WHERE deleted = 0
            ORDER BY parent_id, sort
            """)
    List<SysPermission> selectAllMenus();
}