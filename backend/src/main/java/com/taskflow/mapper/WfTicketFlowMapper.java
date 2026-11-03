package com.taskflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.taskflow.entity.WfTicketFlow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单流转记录 Mapper。
 */
@Mapper
public interface WfTicketFlowMapper extends BaseMapper<WfTicketFlow> {
}