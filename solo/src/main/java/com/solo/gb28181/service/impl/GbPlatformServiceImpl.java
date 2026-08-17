package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.entity.GbDevice;
import com.solo.gb28181.entity.GbPlatform;
import com.solo.gb28181.mapper.GbDeviceMapper;
import com.solo.gb28181.mapper.GbPlatformMapper;
import com.solo.gb28181.request.GbPlatformPageRequest;
import com.solo.gb28181.response.GbPlatformVO;
import com.solo.gb28181.service.GbPlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 国标接入禁用名单服务实现（黑名单）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GbPlatformServiceImpl extends ServiceImpl<GbPlatformMapper, GbPlatform> implements GbPlatformService {

    /** 禁用名单缓存 TTL：5 分钟。增删改会主动 delete，TTL 只是兜底。 */
    private static final Duration BLOCKED_CACHE_TTL = Duration.ofMinutes(5);
    /** 空名单占位成员：Redis 不存空 Set，用哨兵区分"缓存过但是空"和"没缓存过" */
    private static final String EMPTY_MARKER = "__none__";

    private final GbDeviceMapper gbDeviceMapper;
    private final StringRedisTemplate redis;

    @Override
    public Page<GbPlatformVO> pageQuery(GbPlatformPageRequest request) {
        LambdaQueryWrapper<GbPlatform> wrapper = new LambdaQueryWrapper<>();
        if (request.getPlatformId() != null && !request.getPlatformId().isBlank()) {
            wrapper.like(GbPlatform::getPlatformId, request.getPlatformId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            wrapper.like(GbPlatform::getName, request.getName());
        }
        if (request.getEnabled() != null) {
            wrapper.eq(GbPlatform::getEnabled, request.getEnabled());
        }
        wrapper.orderByDesc(GbPlatform::getCreateTime);

        Page<GbPlatform> page = page(new Page<>(request.getPage(), request.getPageSize()), wrapper);
        Page<GbPlatformVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    private GbPlatformVO toVO(GbPlatform platform) {
        GbPlatformVO vo = new GbPlatformVO();
        vo.setId(platform.getId());
        vo.setPlatformId(platform.getPlatformId());
        vo.setName(platform.getName());
        vo.setEnabled(platform.getEnabled());

        vo.setCreateTime(platform.getCreateTime());
        vo.setUpdateTime(platform.getUpdateTime());

        // 关联 gb_device 回填运行态：被拉黑的 ID 可能之前注册过，
        // 展示在线状态便于运维确认"拉黑后是否真的断了"
        GbDevice device = gbDeviceMapper.selectOne(
                new LambdaQueryWrapper<GbDevice>().eq(GbDevice::getDeviceId, platform.getPlatformId()));
        if (device != null) {
            vo.setStatus(device.getStatus());
            vo.setKeepaliveTime(device.getKeepaliveTime());
        }
        return vo;
    }

    @Override
    public void createByManual(GbPlatform platform) {
        if (platform.getPlatformId() == null || platform.getPlatformId().isBlank()) {
            throw new ServiceException("国标ID不能为空");
        }
        if (platform.getPlatformId().length() != 20) {
            throw new ServiceException("国标ID必须为20位");
        }
        boolean dup = lambdaQuery().eq(GbPlatform::getPlatformId, platform.getPlatformId()).exists();
        if (dup) {
            throw new ServiceException("该国标ID已在禁用名单中：" + platform.getPlatformId());
        }
        platform.setId(null);
        // 黑名单默认就是"禁用"：enabled=0 拦截，=1 表示保留记录但临时放行
        if (platform.getEnabled() == null) {
            platform.setEnabled(0);
        }
        save(platform);
        evictBlockedCache();
        log.info("[接入禁用] 新增禁用名单: deviceId={} enabled={}",
                platform.getPlatformId(), platform.getEnabled());
    }

    @Override
    public void updateByManual(GbPlatform platform) {
        GbPlatform exist = getById(platform.getId());
        if (exist == null) {
            throw new ServiceException("禁用名单记录不存在");
        }
        // platformId 不可改（要换 ID 就删了重加，避免"改一下就绕过拦截"的误操作）
        exist.setName(platform.getName());
        exist.setEnabled(platform.getEnabled());

        updateById(exist);
        evictBlockedCache();
        log.info("[接入禁用] 编辑禁用名单: deviceId={} enabled={}",
                exist.getPlatformId(), exist.getEnabled());
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        // 只删名单记录，不动已注册的 gb_device 行（解禁后设备重新 REGISTER 即可恢复）
        for (Long id : ids) {
            GbPlatform platform = getById(id);
            if (platform != null) {
                removeById(id);
                log.info("[接入禁用] 移出禁用名单: deviceId={}", platform.getPlatformId());
            }
        }
        evictBlockedCache();
    }

    @Override
    public boolean isBlocked(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return false;
        try {
            Boolean hit = redis.opsForSet().isMember(GbRedisKeys.DEVICE_BLOCKED_SET, deviceId);
            if (Boolean.TRUE.equals(hit)) {
                return true;
            }
            // Set 存在（含空名单哨兵）→ 缓存有效，未命中即未被拉黑
            if (Boolean.TRUE.equals(redis.hasKey(GbRedisKeys.DEVICE_BLOCKED_SET))) {
                return false;
            }
            // 缓存缺失 → 重建后再判
            return loadBlockedCache().contains(deviceId);
        } catch (Exception e) {
            // Redis 不可用：回退查库（宁可多打 SQL，不能漏拦截）
            log.warn("[接入禁用] 名单缓存读取失败，回退查库 deviceId={} err={}", deviceId, e.getMessage());
            return lambdaQuery()
                    .eq(GbPlatform::getPlatformId, deviceId)
                    .and(w -> w.eq(GbPlatform::getEnabled, 0).or().isNull(GbPlatform::getEnabled))
                    .exists();
        }
    }

    @Override
    public void evictBlockedCache() {
        try {
            redis.delete(GbRedisKeys.DEVICE_BLOCKED_SET);
        } catch (Exception e) {
            log.warn("[接入禁用] 名单缓存失效失败 err={}", e.getMessage());
        }
    }

    /**
     * 整表加载 enabled=0（或 NULL）的 deviceId 到 Redis Set。
     * <p>黑名单预期只有几条到几十条，整表扫描成本可忽略；一次加载后 SISMEMBER 判定 O(1)。</p>
     */
    private Set<String> loadBlockedCache() {
        Set<String> blocked = lambdaQuery()
                .select(GbPlatform::getPlatformId)
                .and(w -> w.eq(GbPlatform::getEnabled, 0).or().isNull(GbPlatform::getEnabled))
                .list()
                .stream()
                .map(GbPlatform::getPlatformId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        try {
            if (blocked.isEmpty()) {
                // Redis 不存空 Set：写哨兵成员，让"缓存过但名单为空"和"没缓存过"可区分，
                // 避免空名单时每条 SIP 报文都重新扫库
                redis.opsForSet().add(GbRedisKeys.DEVICE_BLOCKED_SET, EMPTY_MARKER);
            } else {
                redis.opsForSet().add(GbRedisKeys.DEVICE_BLOCKED_SET, blocked.toArray(new String[0]));
            }
            redis.expire(GbRedisKeys.DEVICE_BLOCKED_SET, BLOCKED_CACHE_TTL);
        } catch (Exception e) {
            log.warn("[接入禁用] 名单缓存写入失败 err={}", e.getMessage());
        }
        return blocked;
    }
}
