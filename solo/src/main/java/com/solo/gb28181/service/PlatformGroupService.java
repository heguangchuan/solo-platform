package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbPlatformGroup;
import com.solo.gb28181.request.GbPlatformGroupSaveRequest;
import com.solo.gb28181.response.GbPlatformGroupResponse;

import java.util.List;

/**
 * <p>本服务只管推送分组树的 CRUD，<b>不与 gb_platform_share 耦合</b>——分组是 admin UI 的
 * 组织树，不参与"哪些 catalog item 已共享给上级平台"的判定。挂载/卸载走
 * {@link CatalogShareService}。</p>
 */
public interface PlatformGroupService {

    List<GbPlatformGroupResponse> listGroupTree(Long platformId);

    void createGroup(Long platformId, GbPlatformGroupSaveRequest req);

    void updateGroup(Long platformId, Long groupId, GbPlatformGroupSaveRequest req);

    void deleteGroup(Long platformId, Long groupId);

    GbPlatformGroup requireOwnedGroup(Long platformId, Long groupId);
}
