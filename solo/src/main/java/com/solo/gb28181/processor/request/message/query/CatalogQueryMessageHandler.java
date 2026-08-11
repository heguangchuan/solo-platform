package com.solo.gb28181.processor.request.message.query;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbDeviceChannel;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.processor.SipRequestProcessorBase;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.CatalogShareService;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.utils.XmlUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;
import javax.sip.message.Response;
import java.util.List;

/**
 * 上级平台 Catalog 查询处理
 * <p>本平台作为下级挂上级时，上级发来
 * {@code <Query><CmdType>Catalog</CmdType>...</Query>} 索要资源目录，本 handler：</p>
 * <ol>
 *   <li>立刻回 200 OK（SIP 事务层）</li>
 *   <li>把 {@code gb_platform_share} 里勾选给该上级的 catalog item（分组 + 通道合一，
 *       按 {@code parental} 区分）分片，经 {@link PlatformCommander#sendCatalogResponse} 发送</li>
 * </ol>
 * <p>Query/Catalog 只走上级平台侧（{@link #handleForPlatform}）；来自下级设备的
 * Catalog 分片响应由 {@code CatalogResponseMessageHandler}（Response/Catalog）处理，二者井水不犯河水。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CatalogQueryMessageHandler extends SipRequestProcessorBase implements MessageHandler {

    private final CatalogShareService catalogShareService;
    private final PlatformCommander platformCommander;

    @Override
    public String getRootType() { return "Query"; }

    @Override
    public String getCmdType() { return "Catalog"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        // 下级设备不会向本平台发起 Catalog Query（本平台不作为它的上级角色被查目录），忽略即可
        try {
            responseAck((SIPRequest) event.getRequest(), Response.OK);
        } catch (Exception e) {
            log.warn("[级联目录] 回 200 失败: {}", e.getMessage());
        }
    }

    @Override
    public void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        SIPRequest request = (SIPRequest) event.getRequest();
        String sn = XmlUtils.childText(root, "SN");

        // 无论有无共享数据，都先回 200 OK 释放 SIP 事务
        try {
            responseAck(request, Response.OK);
        } catch (Exception e) {
            log.warn("[级联目录] 回 200 失败 platform={} SN={}: {}",
                    platform.getServerGbId(), sn, e.getMessage());
            return;
        }

        // ADR-023 合并后：单方法拉所有共享的 catalog item（分组 + 通道）
        List<GbDeviceChannel> items = catalogShareService.loadSharedCatalogItems(platform.getId());

        log.info("[级联目录] 收到查询 platform={} SN={} 待推送 catalog item={}",
                platform.getServerGbId(), sn, items.size());

        platformCommander.sendCatalogResponse(platform, sn, items);
    }
}

