package com.solo.openapi.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * 字面量 IP / CIDR。
 * <p>只接受点分 IPv4 与 IPv6 字面量，解析过程不触发 DNS——白名单条目一旦允许域名，
 * 每个请求都会查一次 DNS，名单内容还会被对方 DNS 记录间接改写。
 * <p>IPv4-mapped IPv6（{@code ::ffff:1.2.3.4}）与回环 {@code ::1} 统一折算成 IPv4，
 * 否则双栈监听时客户端地址和配置永远对不上。
 */
final class IpRange {

    private final byte[] network;
    private final int prefix;
    private final String source;

    private IpRange(byte[] network, int prefix, String source) {
        this.network = network;
        this.prefix = prefix;
        this.source = source;
    }

    /** 解析单条目（IP 或 CIDR），非法条目抛 IllegalArgumentException，消息可直接展示给管理员 */
    static IpRange parse(String entry) {
        String value = entry == null ? "" : entry.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("空条目");
        }
        int slash = value.indexOf('/');
        String addressPart = slash < 0 ? value : value.substring(0, slash).trim();
        byte[] address = tryParse(addressPart);
        if (address == null) {
            throw new IllegalArgumentException(value + "（不是合法 IP）");
        }
        int maxPrefix = address.length * 8;
        int prefix = maxPrefix;
        if (slash >= 0) {
            String prefixPart = value.substring(slash + 1).trim();
            try {
                prefix = Integer.parseInt(prefixPart);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(value + "（掩码位不是数字）");
            }
            if (prefix < 0 || prefix > maxPrefix) {
                throw new IllegalArgumentException(value + "（掩码位应为 0-" + maxPrefix + "）");
            }
        }
        return new IpRange(mask(address, prefix), prefix, value);
    }

    boolean contains(String clientIp) {
        byte[] target = tryParse(clientIp);
        if (target == null || target.length != network.length) {
            return false;
        }
        int fullBytes = prefix / 8;
        int remainBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (network[i] != target[i]) {
                return false;
            }
        }
        if (remainBits == 0) {
            return true;
        }
        int mask = 0xFF << (8 - remainBits);
        return (network[fullBytes] & mask) == (target[fullBytes] & mask);
    }

    @Override
    public String toString() {
        return source;
    }

    /** 解析字面量地址；不是合法 IP 时返回 null，绝不查 DNS */
    static byte[] tryParse(String ip) {
        if (ip == null) {
            return null;
        }
        String value = ip.trim();
        int scope = value.indexOf('%');
        if (scope >= 0) {
            value = value.substring(0, scope);
        }
        if (value.isEmpty()) {
            return null;
        }
        byte[] raw = value.indexOf(':') >= 0 ? parseIpv6(value) : parseIpv4(value);
        return raw == null ? null : normalize(raw);
    }

    /** 字节地址转规范写法；入参必须是 4 或 16 字节 */
    static String format(byte[] address) {
        try {
            return InetAddress.getByAddress(address).getHostAddress();
        } catch (UnknownHostException e) {
            return "";
        }
    }

    private static byte[] parseIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return null;
        }
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                return null;
            }
            int octet = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') {
                    return null;
                }
                octet = octet * 10 + (c - '0');
            }
            if (octet > 255) {
                return null;
            }
            out[i] = (byte) octet;
        }
        return out;
    }

    private static byte[] parseIpv6(String value) {
        if (value.indexOf(':') < 0) {
            return null;
        }
        int doubleColon = value.indexOf("::");
        if (doubleColon >= 0 && value.indexOf("::", doubleColon + 1) >= 0) {
            return null;
        }
        String head = doubleColon >= 0 ? value.substring(0, doubleColon) : value;
        String tail = doubleColon >= 0 ? value.substring(doubleColon + 2) : null;

        int[] groups = new int[8];
        int filled = fillGroups(groups, 0, head);
        if (filled < 0) {
            return null;
        }
        if (tail == null) {
            if (filled != 8) {
                return null;
            }
        } else {
            int[] tailGroups = new int[8];
            int tailFilled = fillGroups(tailGroups, 0, tail);
            if (tailFilled < 0) {
                return null;
            }
            int zeros = 8 - filled - tailFilled;
            // "::" 至少要折叠一个 16 位组
            if (zeros < 1) {
                return null;
            }
            System.arraycopy(tailGroups, 0, groups, filled + zeros, tailFilled);
        }
        byte[] out = new byte[16];
        for (int i = 0; i < 8; i++) {
            out[i * 2] = (byte) (groups[i] >> 8);
            out[i * 2 + 1] = (byte) groups[i];
        }
        return out;
    }

    /** 把不带 "::" 的一段写进 groups，返回写入的 16 位组数；内嵌 IPv4 占两组；非法返回 -1 */
    private static int fillGroups(int[] groups, int offset, String part) {
        if (part.isEmpty()) {
            return 0;
        }
        String[] items = part.split(":", -1);
        int written = 0;
        for (int i = 0; i < items.length; i++) {
            String item = items[i];
            if (item.isEmpty()) {
                return -1;
            }
            if (item.indexOf('.') >= 0) {
                if (i != items.length - 1) {
                    return -1;
                }
                byte[] v4 = parseIpv4(item);
                if (v4 == null || offset + written + 2 > groups.length) {
                    return -1;
                }
                groups[offset + written] = ((v4[0] & 0xFF) << 8) | (v4[1] & 0xFF);
                groups[offset + written + 1] = ((v4[2] & 0xFF) << 8) | (v4[3] & 0xFF);
                written += 2;
            } else {
                int hextet = parseHextet(item);
                if (hextet < 0 || offset + written + 1 > groups.length) {
                    return -1;
                }
                groups[offset + written] = hextet;
                written += 1;
            }
        }
        return written;
    }

    private static int parseHextet(String value) {
        if (value.isEmpty() || value.length() > 4) {
            return -1;
        }
        int result = 0;
        for (int i = 0; i < value.length(); i++) {
            int digit = Character.digit(value.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            result = (result << 4) | digit;
        }
        return result;
    }

    private static byte[] normalize(byte[] address) {
        if (address.length != 16) {
            return address;
        }
        if (isIpv4Mapped(address)) {
            return Arrays.copyOfRange(address, 12, 16);
        }
        if (isIpv6Loopback(address)) {
            return new byte[]{127, 0, 0, 1};
        }
        return address;
    }

    private static boolean isIpv4Mapped(byte[] address) {
        for (int i = 0; i < 10; i++) {
            if (address[i] != 0) {
                return false;
            }
        }
        return address[10] == (byte) 0xFF && address[11] == (byte) 0xFF;
    }

    private static boolean isIpv6Loopback(byte[] address) {
        for (int i = 0; i < 15; i++) {
            if (address[i] != 0) {
                return false;
            }
        }
        return address[15] == 1;
    }

    private static byte[] mask(byte[] address, int prefix) {
        byte[] out = Arrays.copyOf(address, address.length);
        int fullBytes = prefix / 8;
        int remainBits = prefix % 8;
        if (remainBits > 0) {
            out[fullBytes] &= (byte) (0xFF << (8 - remainBits));
        }
        int keep = remainBits > 0 ? fullBytes + 1 : fullBytes;
        Arrays.fill(out, keep, out.length, (byte) 0);
        return out;
    }
}
