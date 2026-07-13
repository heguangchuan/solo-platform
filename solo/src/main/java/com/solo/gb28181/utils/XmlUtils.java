package com.solo.gb28181.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * GB28181 MESSAGE 报文 XML 工具
 * <p>编码处理：以<b>字节内容嗅探</b>为准，不盲信报文里的 {@code <?xml encoding="..."?>} 声明——
 * 大量国标设备（尤其海康/大华）会声明 GB2312 却实际发 UTF-8，或干脆不声明。
 * 策略：能按 UTF-8 严格解码则判 UTF-8，否则回退 GBK（GB2312 超集，避免丢字）。
 * 这样无论设备是否声明、声明是否撒谎，中文都不会乱码。</p>
 * <p>包含海康设备的两个兼容补丁：</p>
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

    /** UTF-8 无法解码时的回退编码：GBK 是 GB2312 超集，覆盖更全避免丢字 */
    private static final Charset FALLBACK_CHARSET = Charset.forName("GBK");
    /** 匹配并移除 XML 声明中的 encoding="xxx"（我们已用 Reader 传入解码后的字符，声明反而会干扰 dom4j） */
    private static final Pattern ENCODING_DECL =
            Pattern.compile("\\s+encoding\\s*=\\s*[\"'][^\"']*[\"']", Pattern.CASE_INSENSITIVE);

    /** 从字节数组解析 XML，返回根元素；编码按内容嗅探（UTF-8 优先，否则 GBK）。失败返回 null */
    public static Element parseRoot(byte[] content) {
        return parseRoot(content, null);
    }

    /**
     * @param charset 显式指定编码；传 null 则按内容嗅探。注意：即使传入声明，也以“能否严格解码”为准，
     *                不盲信设备自报的 encoding。
     */
    public static Element parseRoot(byte[] content, String charset) {
        if (content == null || content.length == 0) return null;
        byte[] sanitized = stripBareAmpersand(content);
        Charset cs = resolveCharset(charset, sanitized);
        // 用我们判定的编码解码为字符串，并抹掉 XML 声明里的 encoding，交给 dom4j 按字符解析
        String xml = stripEncodingDecl(new String(sanitized, cs));
        SAXReader reader = new SAXReader();
        try {
            Document doc = reader.read(new StringReader(xml));
            return doc.getRootElement();
        } catch (DocumentException firstFail) {
            // 兼容海康 <Address ...< ...> 破坏结构：删掉 <Address 行后重试
            log.warn("[报文解析] XML 解析失败，尝试兜底（移除 <Address 行）。charset={} 原文：\n{}", cs, xml);
            StringBuilder fixed = new StringBuilder();
            for (String line : xml.split("\\r?\\n")) {
                if (line.startsWith("<Address")) continue;
                fixed.append(line);
            }
            try {
                return new SAXReader().read(new StringReader(fixed.toString())).getRootElement();
            } catch (DocumentException e2) {
                log.error("[报文解析] XML 兜底解析仍失败", e2);
                return null;
            }
        }
    }

    /**
     * 判定报文真实编码：显式指定优先（若能解码），否则按字节嗅探。
     * <p>核心：能按 UTF-8 <b>严格</b>解码（无非法字节序列）就判 UTF-8，否则 GBK。
     * 不采信 {@code <?xml encoding?>} 声明——国标设备经常声明与实际不符。</p>
     */
    private static Charset resolveCharset(String charset, byte[] content) {
        if (charset != null && !charset.isBlank()) {
            try {
                return Charset.forName(charset.trim());
            } catch (Exception e) {
                log.warn("[报文解析] 不支持的编码 {}，改按内容嗅探", charset);
            }
        }
        return isValidUtf8(content) ? StandardCharsets.UTF_8 : FALLBACK_CHARSET;
    }

    /** 严格判断字节是否为合法 UTF-8（含非法序列则判否） */
    private static boolean isValidUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 移除 XML 声明里的 encoding 属性，避免与已解码字符流冲突 */
    private static String stripEncodingDecl(String xml) {
        int declEnd = xml.indexOf("?>");
        if (declEnd < 0 || !xml.stripLeading().startsWith("<?xml")) return xml;
        String decl = xml.substring(0, declEnd);
        String rest = xml.substring(declEnd);
        return ENCODING_DECL.matcher(decl).replaceAll("") + rest;
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
