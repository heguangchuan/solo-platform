package com.solo.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "系统消息")
@Data
@TableName("sys_message")
public class SysMessage {

    @Schema(description = "主键 ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "接收用户名")
    private String username;

    @Schema(description = "消息标题")
    private String title;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息类型（SYSTEM=系统通知，NOTICE=公告，REMINDER=提醒）")
    private String type;

    @Schema(description = "消息级别（INFO=普通，WARNING=警告，ERROR=错误，IMPORTANT=重要）")
    private String level;

    @Schema(description = "已读（0=未读，1=已读）")
    private Integer readFlag;

    @Schema(description = "跳转路由")
    private String route;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
