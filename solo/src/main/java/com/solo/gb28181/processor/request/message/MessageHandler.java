package com.solo.gb28181.processor.request.message;

import com.solo.gb28181.entity.GbDevice;
import org.dom4j.Element;

import javax.sip.RequestEvent;

/**
 * MESSAGE 子命令处理器
 * <p>GB28181 用 SIP MESSAGE 承载所有业务子命令，最外层 XML 结构形如：</p>
 * <pre>
 *   &lt;Notify&gt;
 *     &lt;CmdType&gt;Keepalive&lt;/CmdType&gt;
 *     ...
 *   &lt;/Notify&gt;
 * </pre>
 * <p>分发方式：先按根元素名（Notify/Query/Control/Response）一级路由，
 * 再按 CmdType 二级路由到本接口的实现。</p>
 */
public interface MessageHandler {

    /** 注册到二级分发表用：根元素名（Notify/Query/Control/Response）+ CmdType */
    String getRootType();

    String getCmdType();

    /**
     * 来自国标下级设备的 MESSAGE
     */
    void handleForDevice(RequestEvent event, GbDevice device, Element root);

    /**
     * 来自国标上级平台的 MESSAGE（后续 Sprint 6 国标级联使用，本期可空实现）
     */
    default void handleForPlatform(RequestEvent event, Object platform, Element root) {
        // 默认空实现
    }
}
