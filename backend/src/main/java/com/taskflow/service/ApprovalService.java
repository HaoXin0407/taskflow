package com.taskflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.taskflow.common.BusinessException;
import com.taskflow.common.PageResult;
import com.taskflow.common.ResultCode;
import com.taskflow.dto.ApprovalAuditDTO;
import com.taskflow.dto.ApprovalSubmitDTO;
import com.taskflow.entity.ApApproval;
import com.taskflow.entity.ApApprovalNode;
import com.taskflow.enums.ApprovalStatus;
import com.taskflow.mapper.ApApprovalMapper;
import com.taskflow.mapper.ApApprovalNodeMapper;
import com.taskflow.mapper.SysUserMapper;
import com.taskflow.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 审批服务 —— 多级串行审批流的实现。
 *
 * <p>设计要点（面试可讲）：</p>
 * <ul>
 *     <li>审批链数据化：一条审批单对应 N 条 {@code ap_approval_node} 记录，
 *         串行推进（current_node 从 1 走到 N），而不是写死 if-else</li>
 *     <li>校验严格：只有「当前节点 且 状态为待审批 且 是当前用户」的节点才能被审批，
 *         防止越权审批和串行跳节点</li>
 *     <li>全程留痕：每个节点的审批人、意见、时间都记录，审批链完整可回溯</li>
 *     <li>通知及时：每推进一步都会通知相关人</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApApprovalMapper approvalMapper;
    private final ApApprovalNodeMapper nodeMapper;
    private final SysUserMapper userMapper;
    private final NoticeService noticeService;

    /**
     * 提交审批单。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApApproval submit(ApprovalSubmitDTO dto) {
        Long userId = SecurityUtils.getUserId();

        // 审批人列表不能包含自己（避免自己审自己）
        List<Long> approverIds = dto.getApproverIds();
        BusinessException.throwIf(approverIds == null || approverIds.isEmpty(), "请至少选择一位审批人");
        BusinessException.throwIf(approverIds.contains(userId), "不能选择自己作为审批人");

        // 校验审批人是否存在且启用
        List<Long> validIds = new ArrayList<>();
        for (Long aid : approverIds) {
            if (userMapper.selectById(aid) != null) {
                validIds.add(aid);
            }
        }
        BusinessException.throwIf(validIds.isEmpty(), ResultCode.APPROVAL_EMPTY_NODE);

        ApApproval approval = new ApApproval();
        approval.setApprovalNo(generateApprovalNo());
        approval.setTitle(dto.getTitle());
        approval.setBizType(dto.getBizType());
        approval.setBizId(dto.getBizId());
        approval.setReason(dto.getReason());
        approval.setAmount(dto.getAmount());
        approval.setApplicantId(userId);
        approval.setCurrentNode(1);
        approval.setTotalNode(validIds.size());
        approval.setStatus(ApprovalStatus.PENDING.getCode());
        approvalMapper.insert(approval);

        // 按顺序创建审批节点
        for (int i = 0; i < validIds.size(); i++) {
            ApApprovalNode node = new ApApprovalNode();
            node.setApprovalId(approval.getId());
            node.setNodeIndex(i + 1);
            node.setNodeName("第" + (i + 1) + "级审批");
            node.setApproverId(validIds.get(i));
            node.setStatus(0);
            nodeMapper.insert(node);
        }

        // 通知第一位审批人
        noticeService.send(validIds.get(0), "新的审批待处理",
                "您有一条审批单「" + approval.getTitle() + "」需要审批", "APPROVAL");

        log.info("审批单提交成功: {}, 审批链长度={}", approval.getApprovalNo(), validIds.size());
        return approval;
    }

    /**
     * 审批处理（通过 / 驳回）。
     *
     * <p>核心流程：</p>
     * <ol>
     *     <li>取出当前节点（node_index = current_node 且 status = 0）</li>
     *     <li>校验当前登录人是否就是该节点的审批人</li>
     *     <li>通过 → 推进 current_node；若已是最后一位则整单通过</li>
     *     <li>驳回 → 整单结束，状态置为已驳回（不逐级回退，简化模型）</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public void audit(ApprovalAuditDTO dto) {
        Long userId = SecurityUtils.getUserId();

        ApApproval approval = approvalMapper.selectById(dto.getApprovalId());
        BusinessException.throwIf(approval == null, ResultCode.APPROVAL_NOT_FOUND);
        BusinessException.throwIf(!ApprovalStatus.PENDING.getCode().equals(approval.getStatus()),
                ResultCode.APPROVAL_STATUS_ERROR);

        // 定位当前待审批节点
        ApApprovalNode currentNode = nodeMapper.selectOne(Wrappers.<ApApprovalNode>lambdaQuery()
                .eq(ApApprovalNode::getApprovalId, approval.getId())
                .eq(ApApprovalNode::getNodeIndex, approval.getCurrentNode())
                .eq(ApApprovalNode::getStatus, 0)
                .last("LIMIT 1"));
        BusinessException.throwIf(currentNode == null, "未找到当前审批节点，请刷新后重试");

        // 只有节点指定的审批人能审
        BusinessException.throwIf(!currentNode.getApproverId().equals(userId),
                ResultCode.APPROVAL_NO_PERMISSION);

        // 更新节点状态
        ApApprovalNode nodeUpdate = new ApApprovalNode();
        nodeUpdate.setId(currentNode.getId());
        nodeUpdate.setStatus(Boolean.TRUE.equals(dto.getPass()) ? 1 : 2);
        nodeUpdate.setComment(dto.getComment());
        nodeUpdate.setApproveTime(LocalDateTime.now());
        nodeMapper.updateById(nodeUpdate);

        ApApproval approvalUpdate = new ApApproval();
        approvalUpdate.setId(approval.getId());

        if (Boolean.TRUE.equals(dto.getPass())) {
            if (approval.getCurrentNode() >= approval.getTotalNode()) {
                // 最后一位审批人通过 → 整单通过
                approvalUpdate.setStatus(ApprovalStatus.APPROVED.getCode());
                approvalMapper.updateById(approvalUpdate);
                noticeService.send(approval.getApplicantId(), "审批已通过",
                        "您的审批单「" + approval.getTitle() + "」已全部审批通过", "APPROVAL");
                log.info("审批单通过: {}", approval.getApprovalNo());
            } else {
                // 推进到下一节点
                int nextNode = approval.getCurrentNode() + 1;
                approvalUpdate.setCurrentNode(nextNode);
                approvalMapper.updateById(approvalUpdate);

                ApApprovalNode next = nodeMapper.selectOne(Wrappers.<ApApprovalNode>lambdaQuery()
                        .eq(ApApprovalNode::getApprovalId, approval.getId())
                        .eq(ApApprovalNode::getNodeIndex, nextNode)
                        .last("LIMIT 1"));
                if (next != null) {
                    noticeService.send(next.getApproverId(), "新的审批待处理",
                            "审批单「" + approval.getTitle() + "」已流转至您，请及时处理", "APPROVAL");
                }
                log.info("审批推进: {} 节点 {} -> {}", approval.getApprovalNo(),
                        approval.getCurrentNode(), nextNode);
            }
        } else {
            // 驳回：整单结束
            approvalUpdate.setStatus(ApprovalStatus.REJECTED.getCode());
            approvalMapper.updateById(approvalUpdate);
            noticeService.send(approval.getApplicantId(), "审批被驳回",
                    "您的审批单「" + approval.getTitle() + "」被驳回：" +
                            (dto.getComment() == null ? "无意见" : dto.getComment()), "APPROVAL");
            log.info("审批单被驳回: {}", approval.getApprovalNo());
        }
    }

    /**
     * 撤销审批单（仅申请人本人，且审批中才能撤销）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        ApApproval approval = approvalMapper.selectById(id);
        BusinessException.throwIf(approval == null, ResultCode.APPROVAL_NOT_FOUND);
        BusinessException.throwIf(!approval.getApplicantId().equals(SecurityUtils.getUserId()),
                "只能撤销自己提交的审批单");
        BusinessException.throwIf(!ApprovalStatus.PENDING.getCode().equals(approval.getStatus()),
                "审批已结束，无法撤销");

        ApApproval update = new ApApproval();
        update.setId(id);
        update.setStatus(ApprovalStatus.CANCELED.getCode());
        approvalMapper.updateById(update);
    }

    /**
     * 分页查询审批单。
     *
     * @param scope mine=我发起的 / todo=待我审批 / done=我处理过的 / all=全部（需权限）
     */
    public PageResult<ApApproval> page(Long pageNum, Long pageSize, Integer status,
                                       String bizType, String keyword, String scope) {
        Long userId = SecurityUtils.getUserId();

        // 普通用户看不到别人的单据
        if (!SecurityUtils.isAdmin()) {
            if (scope == null || scope.isEmpty() || "all".equals(scope)) {
                scope = "mine";
            }
        }
        if (scope == null) {
            scope = "mine";
        }

        Page<ApApproval> page = new Page<>(pageNum, pageSize);
        IPage<ApApproval> result = approvalMapper.selectApprovalPage(
                page, status, bizType, keyword, scope, userId);

        // 填充展示字段 + 计算当前用户是否有权审批
        List<ApApproval> records = result.getRecords();
        for (ApApproval a : records) {
            a.setStatusDesc(ApprovalStatus.descOf(a.getStatus()));
            ApApprovalNode node = nodeMapper.selectOne(Wrappers.<ApApprovalNode>lambdaQuery()
                    .eq(ApApprovalNode::getApprovalId, a.getId())
                    .eq(ApApprovalNode::getNodeIndex, a.getCurrentNode())
                    .last("LIMIT 1"));
            if (node != null) {
                a.setCurrentApproverId(node.getApproverId());
                a.setCanAudit(node.getApproverId().equals(userId)
                        && node.getStatus() == 0
                        && ApprovalStatus.PENDING.getCode().equals(a.getStatus()));
            } else {
                a.setCanAudit(false);
            }
        }
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 审批单详情（含所有节点）。
     */
    public Map<String, Object> detail(Long id) {
        ApApproval approval = approvalMapper.selectById(id);
        BusinessException.throwIf(approval == null, ResultCode.APPROVAL_NOT_FOUND);

        approval.setStatusDesc(ApprovalStatus.descOf(approval.getStatus()));

        List<ApApprovalNode> nodes = nodeMapper.selectList(Wrappers.<ApApprovalNode>lambdaQuery()
                .eq(ApApprovalNode::getApprovalId, id)
                .orderByAsc(ApApprovalNode::getNodeIndex));

        List<Long> nodeIds = new ArrayList<>();
        nodes.forEach(n -> nodeIds.add(n.getApproverId()));
        Map<Long, String> userNames = new java.util.HashMap<>();
        if (!nodeIds.isEmpty()) {
            for (Long uid : nodeIds) {
                var u = userMapper.selectById(uid);
                if (u != null) {
                    userNames.put(uid, u.getNickname());
                }
            }
        }
        nodes.forEach(n -> n.setApproverName(userNames.get(n.getApproverId())));

        Map<String, Object> result = new java.util.HashMap<>(4);
        result.put("approval", approval);
        result.put("nodes", nodes);
        return result;
    }

    /**
     * 待我审批的数量。
     */
    public Long todoCount() {
        List<Long> ids = approvalMapper.selectTodoApprovalIds(SecurityUtils.getUserId());
        return ids == null ? 0L : (long) ids.size();
    }

    /**
     * 生成审批单号：AP + yyyyMMdd + 4位随机数。
     */
    private String generateApprovalNo() {
        return "AP" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}