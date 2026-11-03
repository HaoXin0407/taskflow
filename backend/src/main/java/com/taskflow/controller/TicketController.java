package com.taskflow.controller;

import com.taskflow.annotation.OperLog;
import com.taskflow.common.PageResult;
import com.taskflow.common.Result;
import com.taskflow.dto.TicketActionDTO;
import com.taskflow.dto.TicketDTO;
import com.taskflow.dto.TicketQueryDTO;
import com.taskflow.entity.WfTicket;
import com.taskflow.service.TicketService;
import com.taskflow.vo.TicketDetailVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 工单接口。
 */
@Tag(name = "02-工单管理")
@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @Operation(summary = "分页查询工单")
    @GetMapping("/page")
    public Result<PageResult<WfTicket>> page(TicketQueryDTO query) {
        return Result.success(ticketService.page(query));
    }

    @Operation(summary = "查询工单详情（含流转轨迹与评论）")
    @GetMapping("/{id}")
    public Result<TicketDetailVO> detail(@PathVariable Long id) {
        return Result.success(ticketService.detail(id));
    }

    @Operation(summary = "创建工单")
    @PostMapping
    @OperLog(title = "工单管理", businessType = OperLog.BusinessType.INSERT)
    public Result<WfTicket> create(@Valid @RequestBody TicketDTO dto) {
        return Result.success("工单创建成功", ticketService.create(dto));
    }

    @Operation(summary = "修改工单")
    @PutMapping
    @OperLog(title = "工单管理", businessType = OperLog.BusinessType.UPDATE)
    public Result<Void> update(@Valid @RequestBody TicketDTO dto) {
        ticketService.update(dto);
        return Result.success("修改成功", null);
    }

    @Operation(summary = "删除工单")
    @DeleteMapping("/{id}")
    @OperLog(title = "工单管理", businessType = OperLog.BusinessType.DELETE)
    public Result<Void> delete(@PathVariable Long id) {
        ticketService.delete(id);
        return Result.success("删除成功", null);
    }

    @Operation(summary = "工单流转（受理/提交/确认/驳回/关闭/重开/转派）")
    @PostMapping("/{id}/action")
    @OperLog(title = "工单流转", businessType = OperLog.BusinessType.UPDATE)
    public Result<Void> handleAction(@PathVariable Long id, @Valid @RequestBody TicketActionDTO dto) {
        ticketService.handleAction(id, dto);
        return Result.success("操作成功", null);
    }

    @Operation(summary = "添加评论")
    @PostMapping("/{id}/comment")
    @OperLog(title = "工单评论", businessType = OperLog.BusinessType.INSERT)
    public Result<Void> comment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        ticketService.addComment(id, body.get("content"));
        return Result.success("评论成功", null);
    }

    @Operation(summary = "查询当前状态下我允许执行的动作")
    @GetMapping("/{id}/actions")
    public Result<List<String>> availableActions(@PathVariable Long id) {
        WfTicket ticket = ticketService.detail(id).getTicket();
        Integer status = ticket.getStatus();
        List<String> actions = switch (status) {
            case 0 -> List.of("ACCEPT", "CLOSE");
            case 1 -> List.of("TRANSFER", "SUBMIT", "REJECT");
            case 2 -> List.of("CONFIRM", "REJECT", "TRANSFER");
            case 3 -> List.of("CLOSE", "REOPEN");
            default -> List.of("REOPEN");
        };
        return Result.success(actions);
    }
}