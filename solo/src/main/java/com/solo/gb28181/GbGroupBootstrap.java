package com.solo.gb28181;

import com.solo.gb28181.config.Gb28181Properties;
import com.solo.gb28181.entity.GbGroup;
import com.solo.gb28181.service.GbGroupService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动时根据 {@code gb28181.default-group.*} 自动 upsert 一个默认业务分组(215)。
 * <p>通道注册 / 导入 / 新增未指定分组时统一挂到该分组下（{@code gb_device_channel.group_id} 兜底值）。</p>
 * <p>名称策略：配置优先——每次启动以配置的 name 覆盖 DB（id 不变），与"application.yaml 是运行时配置唯一源"一致。
 * id 定位为建库期确定、不热切换：若配置 id 与库中默认分组不一致，只告警不迁移，避免误改冲掉人工分组。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GbGroupBootstrap {

    private final Gb28181Properties properties;
    private final GbGroupService groupService;

    @PostConstruct
    void init() {
        Gb28181Properties.DefaultGroup cfg = properties.getDefaultGroup();
        if (cfg == null || cfg.getId() == null || cfg.getId().isBlank()) {
            log.error("[资源目录] gb28181.default-group.id 未配置，通道无默认分组可挂。请在 application.yaml 补齐。");
            return;
        }
        String id = cfg.getId().trim();
        String groupId = cfg.getGroupId() == null ? null : cfg.getGroupId().trim();
        if (groupId == null || groupId.isBlank()) {
            log.error("[资源目录] gb28181.default-group.group-id 未配置，级联 Catalog 无法上报默认目录。请在 application.yaml 补齐。");
            return;
        }
        if (groupId.length() != 20) {
            log.warn("[资源目录] gb28181.default-group.group-id={} 非 20 位国标码，级联 Catalog 上报上级会被拒识，请修正", groupId);
        }
        String name = cfg.getName() == null || cfg.getName().isBlank() ? "默认目录" : cfg.getName();

        GbGroup exist = groupService.getById(id);
        if (exist != null) {
            // 配置优先：以配置 name / groupId 覆盖（id 不动）
            boolean changed = false;
            if (!name.equals(exist.getName())) {
                exist.setName(name);
                changed = true;
            }
            if (!groupId.equals(exist.getGroupId())) {
                exist.setGroupId(groupId);
                changed = true;
            }
            if (changed) {
                groupService.updateById(exist);
                log.info("[资源目录] 默认分组已按配置更新 id={} groupId={} name={}", id, groupId, name);
            } else {
                log.info("[资源目录] 默认分组已就绪 id={} groupId={} name={}", id, groupId, name);
            }
            return;
        }
        GbGroup group = new GbGroup();
        group.setId(id);
        group.setGroupId(groupId);
        group.setName(name);
        group.setParentId("0");
        group.setPlatformId("0000");
        group.setSort(1);
        groupService.save(group);
        log.info("[资源目录] 默认分组已写入 id={} groupId={} name={}", id, groupId, name);
    }
}
