package com.taskflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskflow.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户角色关联 Mapper。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    /**
     * 反查：拥有指定权限标识的所有用户ID。
     * <p>用于「把新工单通知给所有能受理的人」这类场景。</p>
     */
    @Select("""
            SELECT DISTINCT ur.user_id
            FROM sys_user_role ur
            INNER JOIN sys_role r ON ur.role_id = r.id
            INNER JOIN sys_role_permission rp ON rp.role_id = r.id
            INNER JOIN sys_permission p ON rp.permission_id = p.id
            WHERE p.perm_code = #{permCode}
              AND r.status = 1
              AND r.deleted = 0
              AND p.deleted = 0
            """)
    List<Long> selectUserIdsByPermCode(@Param("permCode") String permCode);
}