package com.taskflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taskflow.entity.SysUser;
import com.taskflow.mapper.ApApprovalMapper;
import com.taskflow.mapper.SysNoticeMapper;
import com.taskflow.mapper.SysUserMapper;
import com.taskflow.mapper.WfTicketMapper;
import com.taskflow.security.SecurityUtils;
import com.taskflow.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 数据看板服务。
 *
 * <p>性能考量（面试可讲）：所有统计都走数据库聚合（GROUP BY），
 * 而不是把数据全查出来在 Java 里遍历统计 —— 数据量万级时两者差距是数量级的。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final WfTicketMapper ticketMapper;
    private final SysUserMapper userMapper;
    private final ApApprovalMapper approvalMapper;
    private final SysNoticeMapper noticeMapper;
    private final ApprovalService approvalService;

    /**
     * 查询看板数据。
     */
    public DashboardVO getDashboard() {
        DashboardVO vo = new DashboardVO();

        // ---- 核心指标：一次聚合查询搞定，避免多次往返 ----
        Map<String, Object> summary = ticketMapper.selectSummary();
        DashboardVO.Summary s = new DashboardVO.Summary();
        s.setTotal(toLong(summary.get("total")));
        s.setProcessing(toLong(summary.get("processing")));
        s.setFinished(toLong(summary.get("finished")));
        s.setClosed(toLong(summary.get("closed")));
        s.setTimeout(toLong(summary.get("timeout")));
        s.setUrgent(toLong(summary.get("urgent")));

        // 完成率：已完成 / 总数，保留两位小数
        if (s.getTotal() > 0) {
            double rate = s.getFinished().doubleValue() / s.getTotal() * 100;
            s.setFinishRate(Math.round(rate * 100) / 100.0);
        }

        // ---- 与当前用户相关的指标 ----
        Long userId = SecurityUtils.getUserIdOrNull();
        if (userId != null) {
            Long userCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery());
            s.setUserCount(userCount);

            Long myTickets = ticketMapper.selectCount(Wrappers.<com.taskflow.entity.WfTicket>lambdaQuery()
                    .and(w -> w.eq(com.taskflow.entity.WfTicket::getCreatorId, userId)
                            .or()
                            .eq(com.taskflow.entity.WfTicket::getAssigneeId, userId)));
            s.setMyTicketCount(myTickets);

            s.setMyTodoApproval(approvalService.todoCount());
            s.setUnreadNotice(noticeMapper.countUnread(userId).longValue());
        }
        vo.setSummary(s);

        // ---- 图表数据 ----
        vo.setStatusCount(ticketMapper.countGroupByStatus());
        vo.setTypeCount(ticketMapper.countGroupByType());
        vo.setDailyTrend(ticketMapper.countDailyTrend(30));
        vo.setAssigneeRank(userMapper.countTicketGroupByAssignee());
        vo.setApprovalCount(approvalMapper.countGroupByStatus());

        return vo;
    }

    private Long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(o.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 查询工单状态分布（前端下拉筛选用）。
     */
    public List<Map<String, Object>> statusCount() {
        return ticketMapper.countGroupByStatus();
    }
}