package com.taskflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskflow.entity.WfTicketComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单评论 Mapper。
 */
@Mapper
public interface WfTicketCommentMapper extends BaseMapper<WfTicketComment> {

    /**
     * 批量查询工单的评论数量，按工单ID分组。
     * <p>避免列表页对每条工单单独 count 造成 N+1 查询。</p>
     */
    @Select("""
            <script>
            SELECT ticket_id, COUNT(*) AS cnt
            FROM wf_ticket_comment
            WHERE deleted = 0
            <if test="ticketIds != null and ticketIds.size() > 0">
                AND ticket_id IN
                <foreach collection="ticketIds" item="id" open="(" separator="," close=")">
                    #{id}
                </foreach>
            </if>
            GROUP BY ticket_id
            </script>
            """)
    List<java.util.Map<String, Object>> countGroupByTicketIds(@Param("ticketIds") List<Long> ticketIds);
}