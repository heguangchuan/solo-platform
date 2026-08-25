package com.solo.gb28181.processor.request.message.response;

import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.processor.request.message.MessageHandler;
import com.solo.gb28181.service.GbDeviceService;
import com.solo.gb28181.utils.XmlUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;

/**
 * DeviceInfo 响应处理（<Response><CmdType>DeviceInfo</CmdType>...）
 * <p>设备主动 Notify 也用同样字段名，先按 Response 解析；设备会回填 name/manufacturer/model/firmware/channel</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceInfoResponseMessageHandler implements MessageHandler {

    private final GbDeviceService deviceService;

    @Override
    public String getRootType() { return "Response"; }

    @Override
    public String getCmdType() { return "DeviceInfo"; }

    @Override
    public void handleForDevice(RequestEvent event, GbDevice device, Element root) {
        String name = XmlUtils.childText(root, "DeviceName");
        String manufacturer = XmlUtils.childText(root, "Manufacturer");
        String model = XmlUtils.childText(root, "Model");
        String firmware = XmlUtils.childText(root, "Firmware");
        String owner = XmlUtils.childText(root, "Owner");
        String civilCode = XmlUtils.childText(root, "CivilCode");
        String address = XmlUtils.childText(root, "Address");
        String channelStr = XmlUtils.childText(root, "Channel");

        boolean dirty = false;
        // reportName 保存设备上报原始名称
        if (notBlank(name) && !name.equals(device.getReportName())) {
            device.setReportName(name);
            dirty = true;
        }
        // 如果用户还没设置自定义名称，就把上报名称（reportName）填到 name 让用户看见
        if (notBlank(name) && (device.getName() == null || device.getName().isBlank())) {
            device.setName(name);
            dirty = true;
        }
        if (notBlank(manufacturer) && !manufacturer.equals(device.getManufacturer())) { device.setManufacturer(manufacturer); dirty = true; }
        if (notBlank(model) && !model.equals(device.getModel())) { device.setModel(model); dirty = true; }
        if (notBlank(firmware) && !firmware.equals(device.getFirmware())) { device.setFirmware(firmware); dirty = true; }
        // Owner / CivilCode / Address：GB/T 28181 DeviceInfo 标准字段；存库给级联 NOTIFY 用
        // （与 GbDeviceChannel 字段同名同义，但属于设备级；ADR-030 设备级 NOTIFY 推送需要）
        if (notBlank(owner) && !owner.equals(device.getOwner())) { device.setOwner(owner); dirty = true; }
        if (notBlank(civilCode) && !civilCode.equals(device.getCivilCode())) { device.setCivilCode(civilCode); dirty = true; }
        if (notBlank(address) && !address.equals(device.getAddress())) { device.setAddress(address); dirty = true; }
        if (dirty) {
            deviceService.updateById(device);
            log.info("[设备信息] 回填 deviceId={} reportName={} manufacturer={} model={} firmware={} owner={} civilCode={} address={}",
                    device.getDeviceId(), name, manufacturer, model, firmware, owner, civilCode, address);
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }
}
