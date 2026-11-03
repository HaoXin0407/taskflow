package com.taskflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taskflow.entity.WfTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 工单 Mapper。
 */
@Mapper
public interface WfTicketMapper extends BaseMapper<WfTicket> {

    /**
     * 工单分页查询（联表带出创建人 / 处理人昵称）。
     *
     * <p>用 @Select 手工写 SQL 而非 MP 的 Wrapper，是因为需要 LEFT JOIN 昵称；
     * 其余简单查询仍走 BaseMapper 的单表方法，体现「能简则简」的原则。</p>
     *
     * @param page      分页对象
     * @param creatorId 创建人ID，可为空表示不限
     * @param assigneeId 处理人ID，可为空表示不限
     * @param status    状态，可为空表示不限
     * @param type      类型，可为空表示不限
     * @param priority  优先级，可为空表示不限
     * @param keyword   关键字（标题模糊），可为空
     */
    @Select("""
            <script>
            SELECT t.*,
                   cu.nickname AS creator_name,
                   au.nickname AS assignee_name
            FROM wf_ticket t
            LEFT JOIN sys_user cu ON t.creator_id = cu.id
            LEFT JOIN sys_user au ON t.assignee_id = au.id
            WHERE t.deleted = 0
            <if test="creatorId != null">
                AND t.creator_id = #{creatorId}
            </if>
            <if test="assigneeId != null">
                AND t.assignee_id = #{assigneeId}
            </if>
            <if test="status != null">
                AND t.status = #{status}
            </if>
            <if test="type != null and type != ''">
                AND t.type = #{type}
            </if>
            <if test="priority != null">
                AND t.priority = #{priority}
            </if>
            <if test="keyword != null and keyword != ''">
                AND (t.title LIKE CONCAT('%', #{keyword}, '%')
                     OR t.content LIKE CONCAT('%', #{keyword}, '%')
                     OR t.ticket_no LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="mine == true">
                AND (t.creator_id = #{currentUserId} OR t.assignee_id = #{currentUserId})
            </if>
            ORDER BY t.id DESC
            </script>
            """)
    IPage<WfTicket> selectTicketPage(Page<WfTicket> page,
                                     @Param("creatorId") Long creatorId,
                                     @Param("assigneeId") Long assigneeId,
                                     @Param("status") Integer status,
                                     @Param("type") String type,
                                     @Param("priority") Integer priority,
                                     @Param("keyword") String keyword,
                                     @Param("mine") Boolean mine,
                                     @Param("currentUserId") Long currentUserId);

    /**
     * 看板：按状态分组统计。
     */
    @Select("""
            SELECT status AS name, COUNT(*) AS value
            FROM wf_ticket
            WHERE deleted = 0
            GROUP BY status
            ORDER BY status
            """)
    List<Map<String, Object>> countGroupByStatus();

    /**
     * 看板：按类型分组统计。
     */
    @Select("""
            SELECT type AS name, COUNT(*) AS value
            FROM wf_ticket
            WHERE deleted = 0
            GROUP BY type
            """)
    List<Map<String, Object>> countGroupByType();

    /**
     * 看板：近 N 天每日工单量趋势。
     */
    @Select("""
            SELECT DATE_FORMAT(create_time, '%m-%d') AS name,
                   COUNT(*) AS value
            FROM wf_ticket
            WHERE deleted = 0
              AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
            GROUP BY DATE_FORMAT(create_time, '%m-%d')
            ORDER BY DATE_FORMAT(create_time, '%m-%d')
            """)
    List<Map<String, Object>> countDailyTrend(@Param("days") Integer days);

    /**
     * 看板：核心指标汇总（一条 SQL 查完，避免多次往返）。
     */
    @Select("""
            SELECT
                COUNT(*)                                              AS total,
                SUM(CASE WHEN status IN (0,1,2) THEN 1 ELSE 0 END)    AS processing,
                SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END)           AS finished,
                SUM(CASE WHEN status IN (4,5) THEN 1 ELSE 0 END)      AS closed,
                SUM(CASE WHEN is_timeout = 1 THEN 1 ELSE 0 END)       AS timeout,
                SUM(CASE WHEN priority = 1 THEN 1 ELSE 0 END)         AS urgent
            FROM wf_ticket
            WHERE deleted = 0
            """)
    Map<String, Object> selectSummary();

    /**
     * 统计各状态数量，用于用户首页快捷筛选的角标。
     */
    @Select("""
            SELECT status, COUNT(*) AS cnt
            FROM wf_ticket
            WHERE deleted = 0
              AND (creator_id = #{userId} OR assignee_id = #{userId})
            GROUP BY status
            """)
    List<Map<String, Object>> countGroupByStatusForUser(@Param("userId") Long userId);

    /**
     * 查询超时工单（供定时任务调用）。
     */
    @Select("""
            SELECT * FROM wf_ticket
            WHERE deleted = 0
              AND is_timeout = 0
              AND status IN (0, 1, 2)
              AND deadline IS NOT NULL
              AND deadline < NOW()
            """)
    List<WfTicket> selectTimeoutTickets();
}