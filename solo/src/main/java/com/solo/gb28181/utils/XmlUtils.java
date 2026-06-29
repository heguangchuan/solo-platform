package com.solo.gb28181.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GB28181 MESSAGE 报文 XML 工具
 * <p>支持 GB2312 / UTF-8 双编码；包含海康设备的两个兼容补丁：</p>
 * <ol>
 *   <li>未转义的 {@code &} 字符（仅允许 {@code &lt;/&gt;/&amp;/&apos;/&quot;}）</li>
 *   <li>{@code <Address>} 字段含未转义 {@code <} 导致 XML 解析失败 → 整行删除重试</li>
 * </ol>
 */
@Slf4j
@UtilityClass
public class XmlUtils {

    private static final byte AMPERSAND = (byte) '&';
    private static final byte[][] ENTITY_PREFIXES = new byte[][]{
            "&lt;".getBytes(),
            "&gt;".getBytes(),
            "&amp;".getBytes(),
            "&apos;".getBytes(),
            "&quot;".getBytes()
    };

    /** 从字节数组解析 XML，返回根元素；失败返回 null */
    public static Element parseRoot(byte[] content) {
        return parseRoot(content, "GB2312");
    }

    public static Element parseRoot(byte[] content, String charset) {
        if (content == null || content.length == 0) return null;
        byte[] sanitized = stripBareAmpersand(content);
        SAXReader reader = new SAXReader();
        reader.setEncoding(charset);
        try {
            Document doc = reader.read(new ByteArrayInputStream(sanitized));
            return doc.getRootElement();
        } catch (DocumentException firstFail) {
            // 兼容海康 <Address ...< ...> 破坏结构
            String text = new String(sanitized, StandardCharsets.UTF_8);
            log.warn("[报文解析] XML 解析失败，尝试兜底（移除 <Address 行）。原文：\n{}", text);
            StringBuilder fixed = new StringBuilder();
            for (String line : text.split("\\r?\\n")) {
                if (line.startsWith("<Address")) continue;
                fixed.append(line);
            }
            try {
                return reader.read(new ByteArrayInputStream(fixed.toString().getBytes(StandardCharsets.UTF_8))).getRootElement();
            } catch (DocumentException e2) {
                log.error("[报文解析] XML 兜底解析仍失败", e2);
                return null;
            }
        }
    }

    /** 取根元素下指定子元素文本，缺失返回空串 */
    public static String childText(Element root, String name) {
        if (root == null) return "";
        Element child = root.element(name);
        return child == null ? "" : child.getTextTrim();
    }

    /** 移除报文中不合法的裸 {@code &} 字符 */
    private static byte[] stripBareAmpersand(byte[] raw) {
        List<Byte> out = new ArrayList<>(raw.length);
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] != AMPERSAND) {
                out.add(raw[i]);
                continue;
            }
            boolean isValid = false;
            for (byte[] prefix : ENTITY_PREFIXES) {
                if (i + prefix.length <= raw.length) {
                    byte[] window = Arrays.copyOfRange(raw, i, i + prefix.length);
                    if (Arrays.equals(window, prefix)) {
                        isValid = true;
                        break;
                    }
                }
            }
            if (isValid) {
                out.add(raw[i]);
            }
            // else 丢弃这个孤立的 &
        }
        byte[] result = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) result[i] = out.get(i);
        return result;
    }
}
