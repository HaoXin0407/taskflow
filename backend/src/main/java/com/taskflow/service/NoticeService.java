package com.taskflow.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.taskflow.common.PageResult;
import com.taskflow.entity.SysNotice;
import com.taskflow.mapper.SysNoticeMapper;
import com.taskflow.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 站内通知服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysUserRoleMapper userRoleMapper;

    /**
     * 发送通知给指定用户。
     */
    public void send(Long userId, String title, String content, String type) {
        if (userId == null) {
            return;
        }
        SysNotice notice = new SysNotice();
        notice.setUserId(userId);
        notice.setTitle(title);
        notice.setContent(content);
        notice.setType(type);
        notice.setIsRead(0);
        noticeMapper.insert(notice);
    }

    /**
     * 通知所有拥有指定权限的用户。
     * <p>用于「新工单提醒主管受理」这类场景 —— 不用在业务代码里硬编码「哪些人该收到」，
     * 而是根据权限反查，权限调整后通知范围自动跟着变。</p>
     */
    public void notifyPermissionHolders(String permCode, String title, String content, String type) {
        try {
            List<Long> userIds = userRoleMapper.selectUserIdsByPermCode(permCode);
            if (userIds != null) {
                userIds.forEach(uid -> send(uid, title, content, type));
            }
        } catch (Exception e) {
            // 通知失败不能影响主业务流程
            log.warn("发送通知失败，已忽略: {}", e.getMessage());
        }
    }

    /**
     * 分页查询我的通知。
     */
    public PageResult<SysNotice> page(Long current, Long size, Integer isRead) {
        Page<SysNotice> page = new Page<>(current, size);
        IPage<SysNotice> result = noticeMapper.selectPage(page, Wrappers.<SysNotice>lambdaQuery()
                .eq(SysNotice::getUserId, com.taskflow.security.SecurityUtils.getUserId())
                .eq(isRead != null, SysNotice::getIsRead, isRead)
                .orderByDesc(SysNotice::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    /**
     * 未读数量。
     */
    public Integer unreadCount() {
        return noticeMapper.countUnread(com.taskflow.security.SecurityUtils.getUserId());
    }

    /**
     * 标记已读。
     */
    public void markRead(Long id) {
        SysNotice notice = new SysNotice();
        notice.setId(id);
        notice.setIsRead(1);
        noticeMapper.updateById(notice);
    }

    /**
     * 全部标记已读。
     */
    public void markAllRead() {
        SysNotice notice = new SysNotice();
        notice.setIsRead(1);
        noticeMapper.update(notice, Wrappers.<SysNotice>lambdaUpdate()
                .eq(SysNotice::getUserId, com.taskflow.security.SecurityUtils.getUserId())
                .eq(SysNotice::getIsRead, 0));
    }
}