package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbPlatformGroup;
import com.solo.gb28181.request.GbPlatformGroupSaveRequest;
import com.solo.gb28181.response.GbPlatformGroupResponse;

import java.util.List;

/**
 * 上级平台推送目录分组服务（per-platform 推送树，admin UI 组织结构）。
 *
 * <p>本服务只管推送分组树的 CRUD，<b>不与 gb_platform_share 耦合</b>——分组是 admin UI 的
 * 组织树，不参与"哪些 catalog item 已共享给上级平台"的判定。挂载/卸载走
 * {@link CatalogShareService}。</p>
 */
public interface PlatformGroupService {

    /**
     * 拉取某上级平台的推送目录分组树（含 children 嵌套）。
     * 根节点 = parent_id = 0；无数据时返回空列表。
     * 树由 {@link com.solo.common.utils.TreeUtils#build} 在 VO 上构建，Entity 不挂树形字段。
     */
    List<GbPlatformGroupResponse> listGroupTree(Long platformId);

    /**
     * 新建分组
     */
    void createGroup(Long platformId, GbPlatformGroupSaveRequest req);

    /**
     * 改分组
     */
    void updateGroup(Long platformId, Long groupId, GbPlatformGroupSaveRequest req);

    /**
     * 删分组（拒绝有子节点）
     */
    void deleteGroup(Long platformId, Long groupId);

    /**
     * 校验分组存在且属于该 platformId。供其他服务复用（避免重复实现 ownership 校验）。
     */
    GbPlatformGroup requireOwnedGroup(Long platformId, Long groupId);
}
