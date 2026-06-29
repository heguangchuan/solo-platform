package com.solo.gb28181.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * GB28181 MESSAGE 报文 XML 解析工具
 * <p>国标设备 XML 可能用 GB2312 或 UTF-8 编码，先按 GB2312 尝试，失败回落 UTF-8</p>
 */
@Slf4j
@UtilityClass
public class XmlUtils {

    /**
     * 解析 MESSAGE 报文体
     */
    public static Document parse(byte[] content) {
        if (content == null || content.length == 0) {
            return null;
        }
        for (Charset cs : new Charset[]{Charset.forName("GB2312"), StandardCharsets.UTF_8}) {
            try {
                return DocumentHelper.parseText(new String(content, cs));
            } catch (Exception ignore) {
                // 换下一个编码再试
            }
        }
        log.warn("[GB28181] XML 解析失败，content size={}", content.length);
        return null;
    }

    /**
     * 取根元素下指定子元素文本，缺失则返回空串
     */
    public static String getChildText(Element root, String name) {
        if (root == null) return "";
        Element child = root.element(name);
        return child == null ? "" : child.getTextTrim();
    }
}
