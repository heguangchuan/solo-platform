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
 * XML 解析工具，专门对付国标设备各种千奇百怪的报文。
 *
 * 主要坑点：
 *  - 编码乱七八糟：UTF-8、GB2312、GBK、GB18030 都有，海康还爱声明 GB18030 实发 GBK
 *  - 海康的报文里 & 不转义，直接就塞进来了，dom4j 会炸
 *  - XML 声明的 encoding 不能信，得自己嗅探
 *
 * 解析策略（ADR-009）：
 *  1. 先清理裸 &（转成 &amp;，已转义的不动）
 *  2. 解码优先级：显式 charset > 声明是 GB 族就直接用 GB18030 > 否则嗅探
 *  3. 解码后抹掉 XML 声明里的 encoding，再丢给 dom4j
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

    // UTF-8 解不出来就回退 GBK，GBK 是 GB2312 超集，覆盖全一点
    private static final Charset FALLBACK_CHARSET = Charset.forName("GBK");
    // 把 XML 声明里的 encoding 去掉，因为我们已经用自己解码好的字符串传给 dom4j 了，声明反而会干扰
    private static final Pattern ENCODING_DECL =
            Pattern.compile("\\s+encoding\\s*=\\s*[\"'][^\"']*[\"']", Pattern.CASE_INSENSITIVE);
    // 从 XML 声明里把 encoding 值抠出来，没声明就返回 null
    private static final Pattern ENCODING_VALUE =
            Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    // 字节数组解析 XML，返回根元素。编码自己判定，解析失败返回 null
    public static Element parseRoot(byte[] content) {
        return parseRoot(content, null);
    }

    // charset 传了就用指定编码（最高优先级），传 null 就自己嗅探
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
     * 判定报文真实编码。优先级：
     * <ol>
     *   <li>显式传入的 charset（若可解析）</li>
     *   <li>XML 声明里的 encoding，且为中文族（GB2312/GBK/GB18030）时直接采信——
     *       这类编码的中文字节常常<b>恰好</b>是合法 UTF-8 序列，纯嗅探会误判成 UTF-8 导致乱码
     *       （典型：海康声明 GB18030，中文被错解成"韩文"）。GB18030 是 GB2312/GBK 的超集，统一按它解码更稳。</li>
     *   <li>其余情况按字节嗅探：能严格 UTF-8 解码则 UTF-8，否则回退 GBK。</li>
     * </ol>
     * <p>之所以只对中文族声明采信、不采信 UTF-8 声明：UTF-8 可用严格解码验证真伪（见 {@link #isValidUtf8}），
     * 而声明 UTF-8 实发 GBK 的设备也不少，嗅探反而更可靠。</p>
     */
    private static Charset resolveCharset(String charset, byte[] content) {
        if (charset != null && !charset.isBlank()) {
            try {
                return Charset.forName(charset.trim());
            } catch (Exception e) {
                log.warn("[报文解析] 不支持的编码 {}，改按内容嗅探", charset);
            }
        }
        // 采信 XML 声明中的中文族编码（GB2312/GBK/GB18030），避开 UTF-8 误判
        Charset declared = declaredChineseCharset(content);
        if (declared != null) {
            return declared;
        }
        return isValidUtf8(content) ? StandardCharsets.UTF_8 : FALLBACK_CHARSET;
    }

    /**
     * 若报文 XML 声明的 encoding 属于中文族（GB2312/GBK/GB18030），返回对应 Charset；否则 null。
     * <p>GB2312/GBK 一律升格为 GB18030 解码（后者是前两者的严格超集，不会丢字）。</p>
     */
    private static Charset declaredChineseCharset(byte[] content) {
        // 只需嗅探声明所在的头部，用 ASCII 兼容方式读取（encoding 值必为 ASCII 字符）
        int probeLen = Math.min(content.length, 128);
        String head = new String(content, 0, probeLen, StandardCharsets.ISO_8859_1);
        var m = ENCODING_VALUE.matcher(head);
        if (!m.find()) return null;
        String enc = m.group(1).trim().toUpperCase();
        if (enc.startsWith("GB")) {
            // GB2312 / GBK / GB18030 统一用 GB18030 解码
            try {
                return Charset.forName("GB18030");
            } catch (Exception ignore) {
                return FALLBACK_CHARSET;
            }
        }
        return null;
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

    /**
     * 按报文真实编码把字节体解码成字符串（供日志显示等场景）。
     * <p>编码判定同 {@link #resolveCharset}：中文族声明优先，否则按字节嗅探 UTF-8/GBK。
     * 与 {@link #parseRoot} 用同一套判定，避免日志与入库数据编码不一致。</p>
     */
    public static String decodeBody(byte[] content) {
        if (content == null || content.length == 0) return "";
        return new String(content, resolveCharset(null, content));
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
