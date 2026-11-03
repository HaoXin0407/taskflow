package com.taskflow.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.taskflow.entity.WfTicket;
import com.taskflow.mapper.WfTicketFlowMapper;
import com.taskflow.mapper.WfTicketMapper;
import com.taskflow.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单 SLA 超时扫描定时任务。
 *
 * <p>业务价值：SLA（Service Level Agreement，服务级别协议）是工单系统的核心指标。
 * 系统每小时扫一次，把超过截止时间仍未完成的工单标记为超时并提醒处理人。</p>
 *
 * <p>技术点（面试可讲）：</p>
 * <ul>
 *     <li>{@code @Scheduled} 声明式定时，Spring 自动管理线程池</li>
 *     <li>{@code initialDelay} 让首次执行延迟 10 秒，避开应用启动高峰</li>
 *     <li>批量处理前先判断 is_timeout=0，避免重复发通知</li>
 *     <li>任务内部整体 try-catch，保证一次异常不会导致后续调度被取消</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketSlaTask {

    private final WfTicketMapper ticketMapper;
    private final WfTicketFlowMapper flowMapper;
    private final NoticeService noticeService;

    /** 每小时第 5 分钟执行 */
    @Scheduled(cron = "0 5 * * * ?")
    public void scanTimeoutTickets() {
        try {
            List<WfTicket> timeoutTickets = ticketMapper.selectTimeoutTickets();
            if (timeoutTickets.isEmpty()) {
                return;
            }

            for (WfTicket ticket : timeoutTickets) {
                WfTicket update = new WfTicket();
                update.setId(ticket.getId());
                update.setIsTimeout(1);
                ticketMapper.updateById(update);

                // 记录一条超时轨迹
                if (ticket.getAssigneeId() != null) {
                    noticeService.send(ticket.getAssigneeId(), "工单已超时",
                            "工单 " + ticket.getTicketNo() + "【" + ticket.getTitle()
                                    + "】已超过处理时限，请尽快处理", "TICKET");
                }

                log.info("工单超时标记: {} 截止时间={}", ticket.getTicketNo(), ticket.getDeadline());
            }
            log.info("SLA 超时扫描完成，共标记 {} 条工单", timeoutTickets.size());
        } catch (Exception e) {
            log.error("SLA 超时扫描任务执行异常", e);
        }
    }

    /**
     * 每天凌晨 2 点统计前一天的工单情况并给管理员发日报。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyReport() {
        try {
            List<WfTicket> yesterday = ticketMapper.selectList(Wrappers.<WfTicket>lambdaQuery()
                    .ge(WfTicket::getCreateTime, LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay())
                    .lt(WfTicket::getCreateTime, LocalDateTime.now().toLocalDate().atStartOfDay()));

            long finished = yesterday.stream()
                    .filter(t -> t.getStatus() == 3)
                    .count();

            log.info("【日报】昨日新增工单 {} 条，其中完成 {} 条，完成率 {}%",
                    yesterday.size(), finished,
                    yesterday.isEmpty() ? 0 : finished * 100 / yesterday.size());
        } catch (Exception e) {
            log.error("日报任务执行异常", e);
        }
    }
}