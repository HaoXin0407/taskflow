package com.taskflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskflow.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户 Mapper。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 查询用户拥有的角色标识集合。
     * <p>RBAC 链路：user → user_role → role</p>
     */
    @Select("""
            SELECT r.role_code
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户拥有的权限标识集合。
     * <p>RBAC 链路：user → user_role → role_permission → permission</p>
     */
    @Select("""
            SELECT DISTINCT p.perm_code
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            INNER JOIN sys_role_permission rp ON rp.role_id = r.id
            INNER JOIN sys_permission p ON rp.permission_id = p.id
            WHERE ur.user_id = #{userId}
              AND r.status = 1
              AND r.deleted = 0
              AND p.deleted = 0
              AND p.perm_code IS NOT NULL
            """)
    List<String> selectPermCodesByUserId(@Param("userId") Long userId);

    /**
     * 查询用户角色ID列表（用于后台分配角色）。
     */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    /**
     * 统计各状态的工单数量，用于数据看板。
     */
    @Select("""
            SELECT u.username AS name,
                   COUNT(t.id) AS value
            FROM sys_user u
            LEFT JOIN wf_ticket t ON t.assignee_id = u.id AND t.deleted = 0
            WHERE u.deleted = 0
            GROUP BY u.id, u.username
            ORDER BY value DESC
            LIMIT 8
            """)
    List<java.util.Map<String, Object>> countTicketGroupByAssignee();
}