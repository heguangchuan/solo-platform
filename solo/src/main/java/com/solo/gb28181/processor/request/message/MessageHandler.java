package com.solo.gb28181.processor.request.message;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbParentPlatform;
import org.dom4j.Element;

import javax.sip.RequestEvent;

/**
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

    String getRootType();

    String getCmdType();

    void handleForDevice(RequestEvent event, GbDevice device, Element root);

    default void handleForPlatform(RequestEvent event, GbParentPlatform platform, Element root) {
        // 默认空实现
    }
}
