package com.solo.gb28181.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 上级平台推送目录分组（per-platform 推送树节点）。
 * <p>本平台作为下级向某上级平台推送资源时，先在该平台下建一棵推送目录树，树的节点即本表一行。
 * 一行 = 一个上级平台下的一个推送目录分组；parentId 引用同表 id（雪花），根节点 parent_id = 0。
 * 通道挂载关系暂未持久化（推送保存功能待后续迭代）。</p>
 *
 * <p>本 entity 仅对应 {@code gb_platform_group} 表字段。响应给前端的"带 children 的树形结构"
 * 走 {@link com.solo.gb28181.response.GbPlatformGroupResponse}，不在本类上挂 children，
 * 避免污染 entity 与数据库的对应关系。</p>
 */
@Data
@TableName("gb_platform_group")
public class GbPlatformGroup {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 分组名称（运营可读）
     */
    private String name;

    /**
     * 分组国标编码（GB/T 28181-2016 附录 D 20 位数字）：
     * 前 10 位 = 本平台 domain（行政区划中心编码）；第 11-13 位 = 业务分类（215=业务分组 / 216=虚拟组织）；后 7 位 = 序号。
     * 平台内唯一（uk_platform_code）。
     */
    private String code;

    /**
     * 所属上级平台主键（逻辑关联 gb_parent_platform.id）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long platformId;

    /**
     * 父分组主键（引用同表 id；根节点 = 0）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
