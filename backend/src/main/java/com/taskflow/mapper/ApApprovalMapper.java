package com.taskflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taskflow.entity.ApApproval;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 审批单 Mapper。
 */
@Mapper
public interface ApApprovalMapper extends BaseMapper<ApApproval> {

    /**
     * 审批单分页查询。
     *
     * @param scope 查询范围：mine=我发起的 / todo=待我审批 / done=我处理过的 / all=全部
     */
    @Select("""
            <script>
            SELECT a.*, u.nickname AS applicant_name
            FROM ap_approval a
            LEFT JOIN sys_user u ON a.applicant_id = u.id
            WHERE a.deleted = 0
            <if test="status != null">
                AND a.status = #{status}
            </if>
            <if test="bizType != null and bizType != ''">
                AND a.biz_type = #{bizType}
            </if>
            <if test="keyword != null and keyword != ''">
                AND (a.title LIKE CONCAT('%', #{keyword}, '%')
                     OR a.approval_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <choose>
                <when test="scope != null and scope == 'mine'">
                    AND a.applicant_id = #{currentUserId}
                </when>
                <when test="scope != null and scope == 'todo'">
                    AND a.status = 0
                    AND EXISTS (
                        SELECT 1 FROM ap_approval_node n
                        WHERE n.approval_id = a.id
                          AND n.node_index = a.current_node
                          AND n.status = 0
                          AND n.approver_id = #{currentUserId}
                    )
                </when>
                <when test="scope != null and scope == 'done'">
                    AND EXISTS (
                        SELECT 1 FROM ap_approval_node n
                        WHERE n.approval_id = a.id
                          AND n.approver_id = #{currentUserId}
                          AND n.status IN (1, 2)
                    )
                </when>
            </choose>
            ORDER BY a.id DESC
            </script>
            """)
    IPage<ApApproval> selectApprovalPage(Page<ApApproval> page,
                                         @Param("status") Integer status,
                                         @Param("bizType") String bizType,
                                         @Param("keyword") String keyword,
                                         @Param("scope") String scope,
                                         @Param("currentUserId") Long currentUserId);

    /**
     * 查询待我审批的审批单ID列表。
     */
    @Select("""
            SELECT a.id
            FROM ap_approval a
            INNER JOIN ap_approval_node n ON n.approval_id = a.id
            WHERE a.deleted = 0
              AND a.status = 0
              AND n.node_index = a.current_node
              AND n.status = 0
              AND n.approver_id = #{userId}
            """)
    List<Long> selectTodoApprovalIds(@Param("userId") Long userId);

    /**
     * 看板：审批单状态统计。
     */
    @Select("""
            SELECT status AS name, COUNT(*) AS value
            FROM ap_approval
            WHERE deleted = 0
            GROUP BY status
            """)
    List<Map<String, Object>> countGroupByStatus();
}