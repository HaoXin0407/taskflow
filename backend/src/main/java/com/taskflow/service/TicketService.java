package com.taskflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taskflow.common.BusinessException;
import com.taskflow.common.PageResult;
import com.taskflow.common.ResultCode;
import com.taskflow.config.TicketProperties;
import com.taskflow.dto.TicketActionDTO;
import com.taskflow.dto.TicketDTO;
import com.taskflow.dto.TicketQueryDTO;
import com.taskflow.entity.SysUser;
import com.taskflow.entity.WfTicket;
import com.taskflow.entity.WfTicketComment;
import com.taskflow.entity.WfTicketFlow;
import com.taskflow.enums.TicketAction;
import com.taskflow.enums.TicketStatus;
import com.taskflow.enums.TicketType;
import com.taskflow.mapper.SysUserMapper;
import com.taskflow.mapper.WfTicketCommentMapper;
import com.taskflow.mapper.WfTicketFlowMapper;
import com.taskflow.mapper.WfTicketMapper;
import com.taskflow.security.SecurityUtils;
import com.taskflow.vo.TicketDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 工单服务 —— 项目的业务核心。
 *
 * <p>核心设计：状态机。所有状态变更都必须经过 {@link #handleAction} 这一个入口，
 * 它会依次完成：状态合法性校验 → 权限校验 → 更新状态 → 记录流转轨迹 → 发通知。
 * 这样无论从哪个业务场景触发流转，都会留下完整的轨迹，不会出现「状态改了但没日志」的情况。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final WfTicketMapper ticketMapper;
    private final WfTicketFlowMapper flowMapper;
    private final WfTicketCommentMapper commentMapper;
    private final NoticeService noticeService;
    private final SysUserMapper userMapper;

    /**
 * 各优先级对应的 SLA 处理时长（小时）。
 *
 * <p>注意：这里不能用 {@code @Value} 注入 Map —— @Value 只能解析单个标量值，
 * 对 YAML 里这种嵌套结构会报「Could not resolve placeholder」。
 * 正确做法是用 {@code @ConfigurationProperties} 做类型安全的配置绑定，
 * Spring Boot 3 推荐用构造器注入 + 不可变对象。</p>
 */
private final TicketProperties ticketProperties;

/**
 * 分页查询工单。
 *
 * <p><b>数据权限与筛选条件的关系（两个独立的维度）</b>：</p>
 *
 * <p><b>维度一：数据权限（你能看到哪些工单）</b> —— 由角色决定，后端强制：</p>
 * <ul>
 *     <li><b>管理员 / 主管</b>（有 ticket:handle 权限）：可见全部工单。
 *         主管的职责就是受理并处理大家提交的工单，看不到就没法干活</li>
 *     <li><b>普通员工</b>：只可见「我创建的 + 指派给我的」</li>
 * </ul>
 *
 * <p><b>维度二：筛选条件（你想看其中的哪些）</b> —— 由用户选择，可选：</p>
 * <ul>
 *     <li>{@code mine = null}（"全部"）：在数据权限允许的范围内看全部</li>
 *     <li>{@code mine = true}（"与我相关"）：只看「我创建的 + 指派给我的」</li>
 * </ul>
 *
 * <p><b>关键：两个维度是「与」关系，不是「或」关系。</b></p>
 * <p>所以对普通员工来说，"全部" 和 "与我相关" 的结果完全相同 ——
 * 因为数据权限已经把他限制在"与我相关"的范围内了。
 * 这不是 bug，而是数据权限的必然结果。
 * 正确的产品做法是：<b>对只能看自己数据的用户，隐藏"全部"这个选项</b>
 * （前端根据 canViewAll 判断），避免给用户一个无意义的开关。</p>
 *
 * <p>早期版本的两个错误：</p>
 * <ol>
 *     <li>写成「非管理员一律收敛」→ 主管有权限却看不到别人的单，死锁</li>
 *     <li>普通员工的"全部"选项形同虚设，用户以为筛选坏了</li>
 * </ol>
 */
public PageResult<WfTicket> page(TicketQueryDTO query) {
    Page<WfTicket> page = new Page<>(query.getPageNum(), query.getPageSize());

    Long currentUserId = SecurityUtils.getUserIdOrNull();

    // 判断当前用户是否具备"处理工单"的职能（管理员或主管）
    boolean canHandleAll = SecurityUtils.isAdmin()
            || SecurityUtils.hasPermission("ticket:handle");

    if (!canHandleAll) {
        // 普通员工：数据权限强制收敛为「我创建的 或 指派给我的」
        // 注意：无论用户选"全部"还是"与我相关"，结果都一样，
        // 因为他本就只能看到这些数据。mine 保持用户原意，不做篡改。
        query.setMine(true);
        query.setCreatorId(null);
        query.setAssigneeId(null);
    }

    IPage<WfTicket> result = ticketMapper.selectTicketPage(
            page,
            query.getCreatorId(),
            query.getAssigneeId(),
            query.getStatus(),
            query.getType(),
            query.getPriority(),
            query.getKeyword(),
            query.getMine(),
            currentUserId
    );

        List<WfTicket> records = result.getRecords();
        fillExtraFields(records);
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 填充非数据库字段（状态中文名、评论数等）。
     * <p>评论数采用一次 IN 查询批量统计，而不是循环里逐条 count，避免 N+1 问题。</p>
     */
    private void fillExtraFields(List<WfTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(tickets.size());
        for (WfTicket t : tickets) {
            t.setStatusDesc(TicketStatus.descOf(t.getStatus()));
            t.setTypeDesc(TicketType.descOf(t.getType()));
            t.setPriorityDesc(priorityDesc(t.getPriority()));
            t.setCommentCount(0);
            ids.add(t.getId());
        }

        List<Map<String, Object>> counts = commentMapper.countGroupByTicketIds(ids);
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> row : counts) {
            Object tid = row.get("ticket_id");
            Object cnt = row.get("cnt");
            if (tid != null && cnt != null) {
                countMap.put(Long.valueOf(tid.toString()), Integer.valueOf(cnt.toString()));
            }
        }
        for (WfTicket t : tickets) {
            t.setCommentCount(countMap.getOrDefault(t.getId(), 0));
        }
    }

    private String priorityDesc(Integer p) {
        if (p == null) {
            return "中";
        }
        return switch (p) {
            case 1 -> "紧急";
            case 2 -> "高";
            case 3 -> "中";
            default -> "低";
        };
    }

    /**
     * 查询工单详情（含流转轨迹与评论）。
     */
    public TicketDetailVO detail(Long id) {
        WfTicket ticket = ticketMapper.selectById(id);
        BusinessException.throwIf(ticket == null, ResultCode.TICKET_NOT_FOUND);

        // 填充展示字段
        ticket.setStatusDesc(TicketStatus.descOf(ticket.getStatus()));
        ticket.setTypeDesc(TicketType.descOf(ticket.getType()));
        ticket.setPriorityDesc(priorityDesc(ticket.getPriority()));

        List<WfTicketFlow> flows = flowMapper.selectList(Wrappers.<WfTicketFlow>lambdaQuery()
                .eq(WfTicketFlow::getTicketId, id)
                .orderByAsc(WfTicketFlow::getId));
        flows.forEach(f -> {
            f.setStatusDesc(TicketStatus.descOf(f.getToStatus()));
            try {
                f.setActionDesc(TicketAction.of(f.getAction()).getDesc());
            } catch (Exception ignored) {
                f.setActionDesc(f.getAction());
            }
        });

        List<WfTicketComment> comments = commentMapper.selectList(Wrappers.<WfTicketComment>lambdaQuery()
                .eq(WfTicketComment::getTicketId, id)
                .orderByAsc(WfTicketComment::getId));

        TicketDetailVO vo = new TicketDetailVO();
        vo.setTicket(ticket);
        vo.setFlows(flows);
        vo.setComments(comments);
        return vo;
    }

    /**
     * 创建工单。
     */
    @Transactional(rollbackFor = Exception.class)
    public WfTicket create(TicketDTO dto) {
        Long userId = SecurityUtils.getUserId();

        WfTicket ticket = new WfTicket();
        ticket.setTicketNo(generateTicketNo());
        ticket.setTitle(dto.getTitle());
        ticket.setContent(dto.getContent());
        ticket.setType(dto.getType() == null ? TicketType.OTHER.getCode() : dto.getType());
        ticket.setPriority(dto.getPriority() == null ? 3 : dto.getPriority());
        ticket.setStatus(TicketStatus.PENDING.getCode());
        ticket.setCreatorId(userId);
        ticket.setDept(SecurityUtils.getLoginUser().getDept());
        ticket.setIsTimeout(0);
        ticket.setDeadline(calcDeadline(ticket.getPriority()));

        // ---------- 指定处理人（仅管理岗） ----------
        // 只有管理员/主管可以直接指定处理人（比如把工单直接派给某位工程师）。
        // 普通员工不能指定，否则可以绕过"受理"环节直接甩给别人，
        // 破坏了工单状态机的流转规则。
        Long assigneeId = resolveInitialAssignee(dto.getAssigneeId());
        if (assigneeId != null) {
            ticket.setAssigneeId(assigneeId);
        }

        ticketMapper.insert(ticket);

        // 记录创建轨迹（from_status 为 null 表示这是首次流转）
        saveFlow(ticket.getId(), null, TicketStatus.PENDING.getCode(), "CREATE", "提交工单");

        // 通知所有有受理权限的人（ticket:handle）
        noticeService.notifyPermissionHolders("ticket:handle",
                "新工单待受理",
                "工单 " + ticket.getTicketNo() + "【" + ticket.getTitle() + "】等待受理",
                "TICKET");

        // 指定了处理人时，额外通知他本人
        if (assigneeId != null) {
            noticeService.send(assigneeId, "您有新的工单待处理",
                    "工单 " + ticket.getTicketNo() + "【" + ticket.getTitle() + "】已指派给您",
                    "TICKET");
        }

        log.info("工单创建成功: {}, 创建人ID={}, 处理人ID={}", ticket.getTicketNo(), userId, assigneeId);
        return ticket;
    }

    /**
     * 解析创建时的处理人。
     *
     * @param requestedId 前端传入的处理人 ID，可为空
     * @return 合法且有处理权限的处理人 ID；不满足条件时返回 null
     */
    private Long resolveInitialAssignee(Long requestedId) {
        if (requestedId == null) {
            return null;
        }

        // 权限校验：只有管理岗（管理员 / 主管）能指定处理人
        boolean canAssign = SecurityUtils.isAdmin()
                || SecurityUtils.hasPermission("ticket:handle");
        if (!canAssign) {
            log.warn("用户 {} 无权指定处理人，assigneeId={} 已忽略", SecurityUtils.getUserId(), requestedId);
            return null;
        }

        // 校验目标用户存在且启用
        SysUser assignee = userMapper.selectById(requestedId);
        if (assignee == null || (assignee.getStatus() != null && assignee.getStatus() != 1)) {
            log.warn("指定的处理人 {} 不存在或已禁用，已忽略", requestedId);
            return null;
        }

        return requestedId;
    }

    /**
     * 修改工单（仅创建人可改，且状态必须为待受理）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(TicketDTO dto) {
        BusinessException.throwIf(dto.getId() == null, "工单ID不能为空");
        WfTicket exist = ticketMapper.selectById(dto.getId());
        BusinessException.throwIf(exist == null, ResultCode.TICKET_NOT_FOUND);
        BusinessException.throwIf(!exist.getCreatorId().equals(SecurityUtils.getUserId()), "只能修改自己创建的工单");
        BusinessException.throwIf(!TicketStatus.PENDING.getCode().equals(exist.getStatus()),
                "工单已被受理，无法修改");

        WfTicket update = new WfTicket();
        update.setId(dto.getId());
        update.setTitle(dto.getTitle());
        update.setContent(dto.getContent());
        update.setType(dto.getType());
        if (dto.getPriority() != null) {
            update.setPriority(dto.getPriority());
            update.setDeadline(calcDeadline(dto.getPriority()));
        }
        ticketMapper.updateById(update);
    }

    /**
     * 删除工单（逻辑删除）。仅管理员或创建人可删。
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WfTicket ticket = ticketMapper.selectById(id);
        BusinessException.throwIf(ticket == null, ResultCode.TICKET_NOT_FOUND);

        boolean isAdmin = SecurityUtils.isAdmin();
        boolean isCreator = ticket.getCreatorId().equals(SecurityUtils.getUserId());
        BusinessException.throwIf(!isAdmin && !isCreator, "无权删除该工单");

        ticketMapper.deleteById(id);
    }

    /**
     * 工单流转 —— 状态机唯一入口。
     *
     * <p>执行顺序很重要：</p>
     * <ol>
     *     <li>查工单</li>
     *     <li>校验当前状态是否允许该动作（状态机拦截非法流转）</li>
     *     <li>校验操作者是否有权做这个动作（数据权限）</li>
     *     <li>更新工单状态与处理人</li>
     *     <li>写入流转轨迹</li>
     *     <li>发送站内通知</li>
     * </ol>
     * 整个方法加了 {@code @Transactional}，任何一步失败都会整体回滚。
     *
     * @param ticketId 工单ID
     * @param dto      动作参数
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleAction(Long ticketId, TicketActionDTO dto) {
        WfTicket ticket = ticketMapper.selectById(ticketId);
        BusinessException.throwIf(ticket == null, ResultCode.TICKET_NOT_FOUND);

        TicketAction action;
        try {
            action = TicketAction.of(dto.getAction());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("不支持的操作：" + dto.getAction());
        }

        Integer currentStatusCode = ticket.getStatus();
        TicketStatus currentStatus = TicketStatus.of(currentStatusCode);
        BusinessException.throwIf(currentStatus == null, "工单状态数据异常");

        // ① 状态机校验：当前状态是否允许执行该动作
        if (!currentStatus.canDo(action.getCode())) {
            throw new BusinessException(ResultCode.TICKET_STATUS_ERROR.getCode(),
                    "当前状态为「" + currentStatus.getDesc() + "」，不能执行「" + action.getDesc() + "」操作");
        }

        // ② 数据权限校验
        checkActionPermission(ticket, action);

        Long userId = SecurityUtils.getUserId();
        String userName = SecurityUtils.getNickname();

        // ③ 更新工单
        WfTicket update = new WfTicket();
        update.setId(ticketId);
        if (action.changesStatus()) {
            update.setStatus(action.getTargetStatus().getCode());
        }

        if (TicketAction.TRANSFER == action) {
            BusinessException.throwIf(dto.getAssigneeId() == null, "转派时请指定新的处理人");
            update.setAssigneeId(dto.getAssigneeId());
            noticeService.send(dto.getAssigneeId(), "工单转派通知",
                    "工单 " + ticket.getTicketNo() + "【" + ticket.getTitle() + "】已转派给您",
                    "TICKET");
        }

        if (TicketAction.ACCEPT == action) {
            // 受理时把自己设为处理人
            update.setAssigneeId(userId);
        }

        if (TicketAction.CONFIRM == action) {
            update.setFinishAt(LocalDateTime.now());
            // 确认时校验是否为提单人
            noticeService.send(ticket.getAssigneeId(), "工单已被确认",
                    "工单 " + ticket.getTicketNo() + " 已被提单人确认验收", "TICKET");
        }

        if (TicketAction.REJECT == action) {
            // 驳回时处理人回退给提单人重新补充材料
            update.setAssigneeId(ticket.getCreatorId());
        }

        if (TicketAction.REOPEN == action) {
            update.setFinishAt(null);
            update.setIsTimeout(0);
        }

        ticketMapper.updateById(update);

        // ④ 记录流转轨迹
        Integer toStatus = action.changesStatus()
                ? action.getTargetStatus().getCode()
                : currentStatusCode;
        saveFlow(ticketId, currentStatusCode, toStatus, action.getCode(), dto.getRemark());

        // ⑤ 通知提单人
        if (!ticket.getCreatorId().equals(userId)) {
            noticeService.send(ticket.getCreatorId(), "工单状态更新",
                    "工单 " + ticket.getTicketNo() + " 已由 " + userName
                            + " 执行「" + action.getDesc() + "」，当前状态：" + TicketStatus.descOf(toStatus),
                    "TICKET");
        }

        log.info("工单流转: {} 状态 {} -> {}, 动作={}, 操作人={}",
                ticket.getTicketNo(), currentStatusCode, toStatus, action.getCode(), userName);
    }

    /**
     * 动作级权限校验。
     */
    private void checkActionPermission(WfTicket ticket, TicketAction action) {
        Long userId = SecurityUtils.getUserId();
        boolean isAdmin = SecurityUtils.isAdmin();

        switch (action) {
            case ACCEPT -> {
                // 受理：需要 ticket:handle 权限（管理员/主管）
                if (!isAdmin && !SecurityUtils.hasPermission("ticket:handle")) {
                    throw new BusinessException("只有主管或管理员才能受理工单");
                }
            }
            case SUBMIT, REJECT -> {
                // 提交/驳回：只有当前处理人能操作
                if (!isAdmin && !userId.equals(ticket.getAssigneeId())) {
                    throw new BusinessException(ResultCode.TICKET_ASSIGN_ERROR);
                }
            }
            case CONFIRM -> {
                // 确认验收：只有提单人（或管理员）
                if (!isAdmin && !userId.equals(ticket.getCreatorId())) {
                    throw new BusinessException("只有工单提交人可以确认验收");
                }
            }
            case TRANSFER, CLOSE, REOPEN -> {
                // 转派/关闭/重开：需要 ticket:handle 权限
                if (!isAdmin && !SecurityUtils.hasPermission("ticket:handle")) {
                    throw new BusinessException("没有该操作权限");
                }
            }
        }
    }

    /**
     * 添加评论。
     */
    public void addComment(Long ticketId, String content) {
        WfTicket ticket = ticketMapper.selectById(ticketId);
        BusinessException.throwIf(ticket == null, ResultCode.TICKET_NOT_FOUND);
        BusinessException.throwIf(content == null || content.trim().isEmpty(), "评论内容不能为空");
        BusinessException.throwIf(content.length() > 1000, "评论不能超过 1000 个字符");

        WfTicketComment comment = new WfTicketComment();
        comment.setTicketId(ticketId);
        comment.setContent(content.trim());
        comment.setUserId(SecurityUtils.getUserId());
        comment.setUserName(SecurityUtils.getNickname());
        commentMapper.insert(comment);

        // 除自己外，通知相关人
        Long creatorId = ticket.getCreatorId();
        if (!creatorId.equals(SecurityUtils.getUserId())) {
            noticeService.send(creatorId, "工单有新评论",
                    "工单 " + ticket.getTicketNo() + " 有新评论：" + trim(content, 50), "TICKET");
        }
    }

    /**
     * 保存流转记录。
     */
    private void saveFlow(Long ticketId, Integer fromStatus, Integer toStatus,
                          String action, String remark) {
        WfTicketFlow flow = new WfTicketFlow();
        flow.setTicketId(ticketId);
        flow.setFromStatus(fromStatus);
        flow.setToStatus(toStatus);
        flow.setAction(action);
        flow.setRemark(remark);
        flow.setOperatorId(SecurityUtils.getUserId());
        flow.setOperatorName(SecurityUtils.getNickname());
        flowMapper.insert(flow);
    }

    /**
     * 生成工单编号：TF + yyyyMMdd + 4位随机数。
     */
    private String generateTicketNo() {
        return "TF" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /**
 * 根据优先级计算 SLA 截止时间。
 * <p>时长配置见 {@link TicketProperties}，取不到时默认 7 天。</p>
 */
private LocalDateTime calcDeadline(Integer priority) {
    int hours = ticketProperties.getSlaHours(priority);
    return LocalDateTime.now().plusHours(hours);
}

    private String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}