package com.solo.gb28181.response;

import com.solo.gb28181.entity.GbGroup;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 资源目录分组响应：在 {@link GbGroup} entity 之上承载"树形子节点"、"是否默认分组"等仅视图使用的字段，
 * 避免 entity 直接挂 {@code @TableField(exist=false)} 污染 ORM 模型。
 * <p>{@link #children} 由 service 组装树时回填；{@link #defaultGroup} 由 service 按
 * {@code gb28181.default-group.id} 回填，供前端禁用删除 / 限制编辑。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GbGroupResponse extends GbGroup {

    /** 子节点（树形接口用） */
    private List<GbGroupResponse> children;

    /** 是否默认分组：默认分组不可删除、只可改名称/排序 */
    private Boolean defaultGroup;
}
