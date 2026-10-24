package com.solo.gb28181.sip;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.sip.parser.GbStringMsgParserFactory;
import gov.nist.javax.sip.SipProviderImpl;
import gov.nist.javax.sip.SipStackImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.PeerUnavailableException;
import javax.sip.SipFactory;
import javax.sip.TransportNotSupportedException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TooManyListenersException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIP 协议栈启动器。
 *
 * 用 CommandLineRunner + @Order(10) 启动，确保 Spring 上下文加载完再启栈。
 * 多 IP 场景：配置留空就自动扫本机网卡（跳过 loopback 和 docker 虚拟网卡），
 * 配置写死的话就按逗号分隔逐 IP 建栈。
 *
 * 每个 IP 对应一个 SipStack，里面建 UDP + TCP 两个 ListeningPoint 和 Provider。
 *
 * 踩坑记录：JAIN-SIP 不能在构造函数里初始化，必须等 Spring 全起来再弄，
 * 不然 StackLogger 注入会有问题（具体见 ADR-001）。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class SipLayer implements CommandLineRunner {

    private final Gb28181Properties properties;

    /**
     * 用 ObjectProvider 延迟解析：SipProcessorObserver 持有所有请求处理器，
     * 而 DeviceControlMessageHandler 又依赖 SipCommander → SipLayer，
     * 直接构造注入会形成环。监听器只在 run() 启栈时才需要，此时所有 bean 已就绪。
     */
    private final ObjectProvider<SipProcessorObserver> processorObserverProvider;

    // key = ip，存对应 provider
    private final Map<String, SipProviderImpl> udpProviderMap = new ConcurrentHashMap<>();
    private final Map<String, SipProviderImpl> tcpProviderMap = new ConcurrentHashMap<>();
    private final List<String> monitorIps = new ArrayList<>();

    @Override
    public void run(String @NonNull ... args) {
        resolveMonitorIps();
        if (monitorIps.isEmpty()) {
            log.error("[协议栈] 未解析到任何可监听 IP，请配置 gb28181.ip 后重启");
            return;
        }

        SipFactory.getInstance().setPathName("gov.nist");
        SipProcessorObserver sipProcessorObserver = processorObserverProvider.getObject();
        for (String ip : monitorIps) {
            addListeningPoint(ip, properties.getPort(), sipProcessorObserver);
        }
        if (udpProviderMap.isEmpty() && tcpProviderMap.isEmpty()) {
            log.error("[协议栈] 所有监听点启动失败");
            return;
        }
        log.info("[协议栈] SIP 协议栈启动完成 ips={} port={} sipId={} domain={}",
                monitorIps, properties.getPort(), properties.getId(), properties.getDomain());
    }

    // 解析监听 IP：配置了就按逗号拆，没配置就自动扫网卡
    private void resolveMonitorIps() {
        String cfg = properties.getIp();
        if (cfg != null && !cfg.isBlank()) {
            monitorIps.addAll(Arrays.stream(cfg.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
            return;
        }
        try {
            Enumeration<NetworkInterface> nifs = NetworkInterface.getNetworkInterfaces();
            while (nifs.hasMoreElements()) {
                NetworkInterface nif = nifs.nextElement();
                if (nif.getName().startsWith("docker") || nif.getName().startsWith("veth")) continue;
                Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!(addr instanceof Inet4Address)) continue;
                    if (addr.isLoopbackAddress()) continue;
                    log.info("[协议栈] 自动添加 SIP 监听网卡: {} {}", nif.getName(), addr.getHostAddress());
                    monitorIps.add(addr.getHostAddress());
                }
            }
        } catch (Exception e) {
            log.error("[协议栈] 读取本机网卡失败", e);
        }
    }

    private void addListeningPoint(String ip, int port, SipProcessorObserver sipProcessorObserver) {
        SipStackImpl sipStack;
        try {
            sipStack = (SipStackImpl) SipFactory.getInstance()
                    .createSipStack(SipProperties.build("solo-gb28181-" + ip));
            // 强制使用国标 UTF-8 解析器
            sipStack.setMessageParserFactory(new GbStringMsgParserFactory());
        } catch (PeerUnavailableException e) {
            log.error("[协议栈] SIP 协议栈创建失败 ip={}", ip, e);
            return;
        }

        // TCP
        try {
            ListeningPoint tcp = sipStack.createListeningPoint(ip, port, "TCP");
            SipProviderImpl provider = (SipProviderImpl) sipStack.createSipProvider(tcp);
            provider.setDialogErrorsAutomaticallyHandled();
            provider.addSipListener(sipProcessorObserver);
            tcpProviderMap.put(ip, provider);
            log.info("[协议栈] tcp://{}:{} 启动成功", ip, port);
        } catch (TransportNotSupportedException | TooManyListenersException
                 | ObjectInUseException | InvalidArgumentException e) {
            log.error("[协议栈] tcp://{}:{} 启动失败: {}", ip, port, e.getMessage());
        }

        // UDP
        try {
            ListeningPoint udp = sipStack.createListeningPoint(ip, port, "UDP");
            SipProviderImpl provider = (SipProviderImpl) sipStack.createSipProvider(udp);
            provider.setDialogErrorsAutomaticallyHandled();
            provider.addSipListener(sipProcessorObserver);
            udpProviderMap.put(ip, provider);
            log.info("[协议栈] udp://{}:{} 启动成功", ip, port);
        } catch (TransportNotSupportedException | TooManyListenersException
                 | ObjectInUseException | InvalidArgumentException e) {
            log.error("[协议栈] udp://{}:{} 启动失败: {}", ip, port, e.getMessage());
        }
    }

    public List<String> getMonitorIps() { return List.copyOf(monitorIps); }

    public SipProviderImpl getUdpSipProvider(String ip) {
        if (udpProviderMap.size() == 1) return udpProviderMap.values().iterator().next();
        if (ip == null || ip.isBlank()) return null;
        return udpProviderMap.get(ip);
    }

    public SipProviderImpl getTcpSipProvider(String ip) {
        if (tcpProviderMap.size() == 1) return tcpProviderMap.values().iterator().next();
        if (ip == null || ip.isBlank()) return null;
        return tcpProviderMap.get(ip);
    }

    /** 仅在单 IP 模式下使用 */
    public SipProviderImpl getUdpSipProvider() {
        return udpProviderMap.size() == 1 ? udpProviderMap.values().iterator().next() : null;
    }

    public SipProviderImpl getTcpSipProvider() {
        return tcpProviderMap.size() == 1 ? tcpProviderMap.values().iterator().next() : null;
    }

    /**
     * 选择回包时使用的本机 IP
     * 单 IP 直接返回；多 IP 按设备的 localIp（设备记录的接入网卡）匹配
     */
    public String getLocalIp(String deviceLocalIp) {
        if (monitorIps.size() == 1) return monitorIps.get(0);
        if (deviceLocalIp != null && !deviceLocalIp.isBlank()) return deviceLocalIp;
        SipProviderImpl any = getUdpSipProvider();
        return any == null ? null : any.getListeningPoint().getIPAddress();
    }
}
