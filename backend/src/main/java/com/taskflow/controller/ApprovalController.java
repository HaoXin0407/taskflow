package com.taskflow.controller;

import com.taskflow.annotation.OperLog;
import com.taskflow.common.PageResult;
import com.taskflow.common.Result;
import com.taskflow.dto.ApprovalAuditDTO;
import com.taskflow.dto.ApprovalSubmitDTO;
import com.taskflow.entity.ApApproval;
import com.taskflow.service.ApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 审批接口。
 *
 * <p><b>权限说明</b>：</p>
 * <ul>
 *     <li>{@code approval:list} —— 查看审批单（普通员工也能看自己发起的）</li>
 *     <li>{@code approval:submit} —— <b>发起</b>审批单（所有员工的基本权限，
 *         因为请假/加班是人人都会有的需求）</li>
 *     <li>{@code approval:audit} —— <b>审核</b>审批单（管理岗专属，
 *         决定单据是通过还是驳回）</li>
 * </ul>
 *
 * <p>之前把「发起审批」和「审批操作」都挂在 approval:audit 上，
 * 导致普通员工无法发起审批；而 submit 接口又没有后端校验，
 * 绕过前端直接调接口又能提交 —— 前后端权限不一致。
 * 现在拆成两个独立权限，并补齐后端校验。</p>
 */
@Tag(name = "03-审批管理")
@RestController
@RequestMapping("/approval")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('approval:list') or hasRole('ADMIN')")
public class ApprovalController {

    private final ApprovalService approvalService;

    @Operation(summary = "分页查询审批单（scope: mine/todo/done/all）")
    @GetMapping("/page")
    public Result<PageResult<ApApproval>> page(@RequestParam(defaultValue = "1") Long pageNum,
                                               @RequestParam(defaultValue = "10") Long pageSize,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String bizType,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "mine") String scope) {
        return Result.success(approvalService.page(pageNum, pageSize, status, bizType, keyword, scope));
    }

    @Operation(summary = "审批单详情（含审批链）")
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        return Result.success(approvalService.detail(id));
    }

    @Operation(summary = "提交审批单")
    @PostMapping
    @PreAuthorize("hasAuthority('approval:submit') or hasRole('ADMIN')")
    @OperLog(title = "审批管理", businessType = OperLog.BusinessType.INSERT)
    public Result<ApApproval> submit(@Valid @RequestBody ApprovalSubmitDTO dto) {
        return Result.success("提交成功", approvalService.submit(dto));
    }

    @Operation(summary = "审批（通过/驳回）")
    @PostMapping("/audit")
    // ⚠️ 这里刻意不要求 approval:audit 权限！
    //
    // 原因：审批权不是「角色权限」，而是「数据权限」——
    //       能不能审批某张单，取决于该用户是否是这张单当前节点的审批人。
    //       典型场景：manager 向员工发起审批，指定员工审批，
    //       此时审批人是普通员工，他不可能有 approval:audit 权限，
    //       但他必须能审批这张单 —— 否则流程直接卡死。
    //
    // 真正的安全边界在 ApprovalService.audit()：
    //       校验 currentNode.getApproverId().equals(userId)，
    //       不是该单的审批人一律拒绝（业务异常 APPROVAL_NO_PERMISSION）。
    //       所以放开接口入口不会造成越权。
    //
    // 保留 approval:list 作为最低门槛：只有能进入审批模块的角色才能调这个接口。
    @PreAuthorize("hasAuthority('approval:list') or hasRole('ADMIN')")
    @OperLog(title = "审批操作", businessType = OperLog.BusinessType.AUDIT)
    public Result<Void> audit(@Valid @RequestBody ApprovalAuditDTO dto) {
        approvalService.audit(dto);
        return Result.success("审批成功", null);
    }

    @Operation(summary = "撤销审批单")
    @PostMapping("/{id}/cancel")
    @OperLog(title = "撤销审批", businessType = OperLog.BusinessType.UPDATE)
    public Result<Void> cancel(@PathVariable Long id) {
        approvalService.cancel(id);
        return Result.success("已撤销", null);
    }

    @Operation(summary = "待我审批数量")
    @GetMapping("/todo/count")
    public Result<Long> todoCount() {
        return Result.success(approvalService.todoCount());
    }
}