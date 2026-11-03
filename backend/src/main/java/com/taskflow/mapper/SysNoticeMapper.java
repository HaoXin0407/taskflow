package com.taskflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskflow.entity.SysNotice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 站内通知 Mapper。
 */
@Mapper
public interface SysNoticeMapper extends BaseMapper<SysNotice> {

    /**
     * 统计未读通知数量。
     */
    @Select("""
            SELECT COUNT(*) FROM sys_notice
            WHERE deleted = 0 AND user_id = #{userId} AND is_read = 0
            """)
    Integer countUnread(@Param("userId") Long userId);
}