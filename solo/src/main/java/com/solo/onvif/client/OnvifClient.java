package com.solo.onvif.client;

import com.solo.common.ServiceException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.SimpleTimeZone;


@Slf4j
public class OnvifClient {

    private static final String NS_ENV = "http://www.w3.org/2003/05/soap-envelope";
    private static final String NS_WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final String NS_WSU = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    private static final String NS_DEVICE = "http://www.onvif.org/ver10/device/wsdl";
    private static final String NS_MEDIA = "http://www.onvif.org/ver10/media/wsdl";
    private static final String NS_SCHEMA = "http://www.onvif.org/ver10/schema";
    private static final String PASSWORD_DIGEST_TYPE =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest";
    private static final String ENCODING_BASE64 =
            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String[] commonPaths;

    public OnvifClient(String host, int port, String username, String password) {
        this.host = host;
        this.port = port <= 0 ? 80 : port;
        this.username = username;
        this.password = password;
        // 不同厂商 ONVIF  endpoint 路径不统一，按常见度尝试
        this.commonPaths = new String[] {
                "http://" + host + ":" + this.port + "/onvif/device_service",
//                "http://" + host + ":" + this.port + "/onvif/deviceservice",
//                "http://" + host + ":" + this.port + "/onvif/DeviceService",
//                "http://" + host + ":" + this.port + "/device_service",
//                "http://" + host + ":" + this.port + "/onvif",
//                "http://" + host + ":" + this.port + "/",
        };
    }


    public static boolean ping(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port <= 0 ? 80 : port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 探测设备：抓厂商/型号/固件/序列号 + 抓 RTSP 流地址（profile 固定取主码流，TCP 传输）
     */
    public OnvifProbeResult probe() {
        OnvifProbeResult result = new OnvifProbeResult();
        fillDeviceInformation(result);
        String profileToken = pickProfileToken();
        result.setStreamUri(getStreamUri(profileToken));
        return result;
    }


    private void fillDeviceInformation(OnvifProbeResult out) {
        String body = """
                <tds:GetDeviceInformation xmlns:tds="%s"/>
                """.formatted(NS_DEVICE);
        Element resp = callDevice(body);
        out.setManufacturer(text(resp, "//*[local-name()='Manufacturer']"));
        out.setModel(text(resp, "//*[local-name()='Model']"));
        out.setFirmware(text(resp, "//*[local-name()='FirmwareVersion']"));
        out.setSerialNumber(text(resp, "//*[local-name()='SerialNumber']"));
    }

    /** 从 device_service 探测 media 服务地址；同一 host 一般不用重定向，直接沿用 host 换 wsdl 后缀 */
    private String pickProfileToken() {
        String body = """
                <trt:GetProfiles xmlns:trt="%s"/>
                """.formatted(NS_MEDIA);
        Element resp = callMedia(body, "GetProfiles");
        java.util.List<?> profiles = resp.selectNodes("//*[local-name()='Profiles']");
        if (profiles.isEmpty()) {
            throw new ServiceException("ONVIF GetProfiles 返回空");
        }
        // 固定取 profile[0]（主码流）
        Node profile = (Node) profiles.get(0);
        Node tokenAttr = profile.selectSingleNode("@token");
        if (tokenAttr == null || tokenAttr.getText().isBlank()) {
            throw new ServiceException("ONVIF Profile 未带 token 属性");
        }
        return tokenAttr.getText();
    }

    private String getStreamUri(String profileToken) {
        // 拉流传输统一 TCP（用户选定），Stream=RTP-Unicast
        String body = """
                <trt:GetStreamUri xmlns:trt="%s" xmlns:tt="%s">
                    <trt:StreamSetup>
                        <tt:Stream>RTP-Unicast</tt:Stream>
                        <tt:Transport>
                            <tt:Protocol>TCP</tt:Protocol>
                        </tt:Transport>
                    </trt:StreamSetup>
                    <trt:ProfileToken>%s</trt:ProfileToken>
                </trt:GetStreamUri>
                """.formatted(NS_MEDIA, NS_SCHEMA, profileToken);
        Element resp = callMedia(body, "GetStreamUri");
        String uri = text(resp, "//*[local-name()='Uri']");
        if (uri == null || uri.isBlank()) {
            throw new ServiceException("ONVIF GetStreamUri 未返回 Uri");
        }
        return uri;
    }


    private Element callDevice(String bodyXml) {
        return callWithPathFallback(bodyXml, "GetDeviceInformation");
    }

    private Element callMedia(String bodyXml, String action) {
        // 大部分一体机 IPC 把所有 endpoint 都挂在 /onvif/device_service 下；
        // 更严格的 NVR 会要求走 GetCapabilities 拿到的 media XAddr。当前场景默认走 device_service 更稳。
        return callWithPathFallback(bodyXml, action);
    }

    /**
     * 自动尝试多个常见路径，找到一个能成功响应的 endpoint。
     * 不同厂商 ONVIF 路径约定差异大，硬编码一个路径兼容性差。
     */
    private Element callWithPathFallback(String bodyXml, String action) {
        ServiceException lastEx = null;
        for (String url : commonPaths) {
            try {
                return call(url, bodyXml, action);
            } catch (ServiceException e) {
                lastEx = e;
                // 400/404 继续试下一个路径，其他错误直接抛
                if (e.getMessage() != null && (e.getMessage().contains("HTTP 400") || e.getMessage().contains("HTTP 404"))) {
                    continue;
                }
                throw e;
            }
        }
        // 所有路径都试完了都失败
        if (lastEx != null) {
            throw lastEx;
        }
        return null;
    }

    private Element call(String url, String bodyXml, String action) {
        String envelope = buildEnvelope(bodyXml);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"" + action + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String body = resp.body();
            if (resp.statusCode() >= 400) {
                throw new ServiceException("ONVIF HTTP " + resp.statusCode() + " " + action
                        + " body=" + shorten(body));
            }
            // 清理响应：移除 UTF-8 BOM 和开头空白，某些设备会返回 BOM 导致解析失败
            String cleaned = body;
            if (cleaned.startsWith("﻿")) {
                cleaned = cleaned.substring(1);
            }
            cleaned = stripLeadingWhitespace(cleaned);
            Document doc = new SAXReader().read(new ByteArrayInputStream(cleaned.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getRootElement();
            // 检查 SOAP Fault
            Node fault = root.selectSingleNode("//*[local-name()='Fault']");
            if (fault != null) {
                Node reason = fault.selectSingleNode(".//*[local-name()='Text']");
                throw new ServiceException("ONVIF " + action + " 失败: "
                        + (reason == null ? "unknown" : reason.getText()));
            }
            return root;
        } catch (ServiceException e) {
            throw e;
        } catch (DocumentException e) {
            throw new ServiceException("ONVIF 响应解析失败 " + action + ": " + e.getMessage());
        } catch (Exception e) {
            log.debug("[ONVIF探测] SOAP 调用异常 地址: {} 操作: {}", url, action, e);
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) detail = e.getClass().getSimpleName();
            throw new ServiceException("ONVIF 调用异常 " + action + " @ " + url + " - " + detail);
        }
    }

    private String buildEnvelope(String bodyXml) {
        String securityHeader = "";
        if (username != null && !username.isBlank()) {
            // WS-Security 规范：nonce 是 16 字节随机数，原始字节参与 digest，然后 Base64 编码放入报文
            byte[] nonceBytes = new byte[16];
            RANDOM.nextBytes(nonceBytes);
            String encodedNonce = Base64.getEncoder().encodeToString(nonceBytes);
            String created = utcNow();
            String digest = passwordDigest(nonceBytes, created, password == null ? "" : password);
            securityHeader = """
                    <s:Header>
                      <wsse:Security xmlns:wsse="%s" xmlns:wsu="%s">
                        <wsse:UsernameToken>
                          <wsse:Username>%s</wsse:Username>
                          <wsse:Password Type="%s">%s</wsse:Password>
                          <wsse:Nonce EncodingType="%s">%s</wsse:Nonce>
                          <wsu:Created>%s</wsu:Created>
                        </wsse:UsernameToken>
                      </wsse:Security>
                    </s:Header>
                    """.formatted(NS_WSSE, NS_WSU,
                    xmlEscape(username), PASSWORD_DIGEST_TYPE, digest,
                    ENCODING_BASE64, encodedNonce, created);
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="%s">
                %s<s:Body>%s</s:Body>
                </s:Envelope>
                """.formatted(NS_ENV, securityHeader, bodyXml);
    }


    private static String passwordDigest(byte[] nonceBytes, String created, String password) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.update(nonceBytes);
            sha1.update(created.getBytes(StandardCharsets.UTF_8));
            sha1.update(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sha1.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException("SHA-1 不可用: " + e.getMessage());
        }
    }

    private static String utcNow() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(new SimpleTimeZone(SimpleTimeZone.UTC_TIME, "UTC"));
        return sdf.format(new Date());
    }

    private static String text(Element root, String xpath) {
        Node node = root.selectSingleNode(xpath);
        return node == null ? null : node.getText();
    }

    private static String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String stripLeadingWhitespace(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i == 0 ? s : s.substring(i);
    }

    private static String shorten(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }


    @Getter
    public static class OnvifProbeResult {
        private String manufacturer;
        private String model;
        private String firmware;
        private String serialNumber;
        private String streamUri;

        void setManufacturer(String v) { this.manufacturer = v; }
        void setModel(String v) { this.model = v; }
        void setFirmware(String v) { this.firmware = v; }
        void setSerialNumber(String v) { this.serialNumber = v; }
        void setStreamUri(String v) { this.streamUri = v; }
    }
}
