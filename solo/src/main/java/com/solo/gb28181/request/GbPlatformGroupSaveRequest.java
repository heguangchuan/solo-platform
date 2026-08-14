package com.solo.gb28181.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 新建 / 编辑上级平台推送分组请求
 *
 * <p>platformId 从 URL 路径传入，body 只携带分组自身属性。
 * parentId = 0 表示根节点；非 0 时必须属于同一 platformId。</p>
 */
@Data
public class GbPlatformGroupSaveRequest {

    /**
     * 分组名称
     */
    @NotBlank(message = "分组名称不能为空")
    private String name;

    /**
     * 分组国标编码（GB/T 28181-2016 附录 D 20 位数字）：
     * <ul>
     *   <li>前 10 位 = 本平台 domain（行政区划中心编码）</li>
     *   <li>第 11-13 位 = 业务分类（215=业务分组 / 216=虚拟组织）</li>
     *   <li>后 7 位 = 序号</li>
     * </ul>
     * 平台内唯一（uk_platform_code）。
     */
    @NotBlank(message = "分组编码不能为空")
    @Pattern(regexp = "^\\d{10}(215|216)\\d{7}$", message = "分组编码必须是 20 位数字，且第 11-13 位为 215（业务分组）或 216（虚拟组织）")
    private String code;

    /**
     * 父分组主键（引用同表 id；根 = 0）
     */
    @NotNull(message = "父分组不能为空，根节点请填 0")
    private Long parentId;
}
