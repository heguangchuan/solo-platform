package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbPlatformGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 推送目录分组树响应 VO
 *
 * <p>字段与 Entity {@link GbPlatformGroup} 完全一致，仅外加 children 字段。
 * 走继承而非字段重写，避免 7 个字段 + 注解（{@code @JsonSerialize} / {@code @JsonFormat}）
 * 在两处分别维护。Entity 上的 MyBatis-Plus 注解（{@code @TableId} / {@code @TableField} /
 * {@code @TableName}）会被子类继承，但只对 MyBatis 解析生效，不影响 Jackson 序列化。</p>
 *
 * <p>Service 通过 {@link com.solo.common.utils.TreeUtils#build} 构树后返回给前端。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GbPlatformGroupResponse extends GbPlatformGroup {

    /**
     * 子节点（构树后由 TreeUtils 填充）
     */
    private List<GbPlatformGroupResponse> children = new ArrayList<>();
}
