package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.request.GbPlatformShareSaveRequest;
import com.solo.gb28181.response.GbPlatformSharedResponse;
import com.solo.gb28181.response.GbShareTreeNodeResponse;

import java.util.List;

/**
 * 目录共享服务（ADR-023 合并版）。
 * <p>本平台作为下级向上级共享 catalog item（分组 + 通道合一，按 parental 区分）的编排与持久化。
 * 后续 S6.b 的 Catalog 查询响应 / 变更 Notify 会以此处保存的共享关系为数据源。</p>
 */
public interface CatalogShareService {

    /**
     * 本平台可共享的 catalog item 树：parental=1 分组作枝干，parental=0 通道按 parentId 挂分组下。
     */
    List<GbShareTreeNodeResponse> shareTree();

    /**
     * 查询某上级平台已共享的 catalog item 节点 id（回填前端勾选态）。
     */
    GbPlatformSharedResponse getShared(Long platformId);

    /**
     * 全量覆盖保存某上级平台的共享 catalog item 列表。
     * 入参 channelIds 混合 g_（parental=1 分组）和 c_（parental=0 通道）前缀的节点 id，
     * 后端按前缀分类入库（同一张 gb_platform_share 表）。
     */
    void saveShared(Long platformId, GbPlatformShareSaveRequest request);

    /**
     * 查询某上级平台已共享的所有 catalog item 明细（含分组 parental=1 + 通道 parental=0）。
     * <p>供 S6.b-2 向上级回 Catalog Response 时构造 Item 使用。Item 顺序：分组（parental=1）
     * 优先于通道（parental=0），同 parental 内按 channel_id 升序。</p>
     */
    List<GbDeviceChannel> loadSharedCatalogItems(Long platformId);

    /**
     * 查询某上级平台已共享的所有通道明细（仅 parental=0，剔除分组）。
     * <p>供上级 INVITE 点播反查真实通道用——GB 协议 INVITE 只能点播通道（parental=0），
     * 分组无 exposed_code 也不会被点播，靠 mapper 的 {@code WHERE ch.parental=0} 过滤。</p>
     */
    List<GbDeviceChannel> loadSharedChannels(Long platformId);
}
