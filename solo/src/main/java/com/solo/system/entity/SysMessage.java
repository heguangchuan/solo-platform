package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统消息实体
 */
@Schema(description = "系统消息")
@Data
@TableName("sys_message")
public class SysMessage {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收用户名
     */
    @Schema(description = "接收用户名")
    private String username;

    /**
     * 消息标题
     */
    @Schema(description = "消息标题")
    private String title;

    /**
     * 消息内容
     */
    @Schema(description = "消息内容")
    private String content;

    /**
     * 消息类型：SYSTEM-系统通知，NOTICE-公告，REMINDER-提醒
     */
    @Schema(description = "消息类型（SYSTEM=系统通知，NOTICE=公告，REMINDER=提醒）")
    private String type;

    /**
     * 消息级别：INFO-普通，WARNING-警告，ERROR-错误，IMPORTANT-重要
     */
    @Schema(description = "消息级别（INFO=普通，WARNING=警告，ERROR=错误，IMPORTANT=重要）")
    private String level;

    /**
     * 是否已读：0-未读，1-已读
     */
    @Schema(description = "已读（0=未读，1=已读）")
    private Integer readFlag;

    /**
     * 跳转路由（可选）
     */
    @Schema(description = "跳转路由")
    private String route;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
