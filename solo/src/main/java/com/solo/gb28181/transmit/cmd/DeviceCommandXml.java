package com.solo.gb28181.transmit.cmd;

/**
 * GB28181 MESSAGE 报文体（XML）构造
 */
public final class DeviceCommandXml {

    private DeviceCommandXml() {}

    /**
     * 构造一个 Query 报文
     * <p>报文示例：</p>
     * <pre>
     * &lt;?xml version="1.0" encoding="GB2312"?&gt;
     * &lt;Query&gt;
     *   &lt;CmdType&gt;Catalog&lt;/CmdType&gt;
     *   &lt;SN&gt;123456&lt;/SN&gt;
     *   &lt;DeviceID&gt;34020000001320000001&lt;/DeviceID&gt;
     * &lt;/Query&gt;
     * </pre>
     */
    public static String buildQuery(String cmdType, int sn, String deviceId, String charset) {
        StringBuilder sb = new StringBuilder(220);
        sb.append("<?xml version=\"1.0\" encoding=\"").append(charset).append("\"?>\r\n");
        sb.append("<Query>\r\n");
        sb.append("<CmdType>").append(cmdType).append("</CmdType>\r\n");
        sb.append("<SN>").append(sn).append("</SN>\r\n");
        sb.append("<DeviceID>").append(deviceId).append("</DeviceID>\r\n");
        sb.append("</Query>\r\n");
        return sb.toString();
    }
}
