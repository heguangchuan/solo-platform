package com.solo.gb28181.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.solo.gb28181.entity.GbParentPlatform;
import com.solo.gb28181.mapper.GbParentPlatformMapper;
import com.solo.gb28181.service.PlatformRegisterService;
import com.solo.gb28181.transmit.cmd.PlatformCommander;
import com.solo.gb28181.transmit.cmd.PlatformRegisterState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 级联注册服务实现
 *
 * <p>调度由 {@link com.solo.gb28181.task.GbScheduledTasks} 触发。本类只保留业务方法：
 * 注册 / 刷新 / 心跳 / 上下线回填。注册响应异步由
 * {@link com.solo.gb28181.processor.response.RegisterResponseProcessor} 处理后回调 {@link #online}/{@link #offline}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformRegisterServiceImpl implements PlatformRegisterService {

    private final GbParentPlatformMapper parentPlatformMapper;
    private final PlatformCommander commander;
    private final PlatformRegisterState registerState;

    private int expiresOf(GbParentPlatform p) {
        return p.getExpires() != null && p.getExpires() > 0 ? p.getExpires() : 3600;
    }

    @Override
    public void registerAll() {
        List<GbParentPlatform> platforms = enabledPlatforms();
        for (GbParentPlatform p : platforms) {
            commander.register(p, expiresOf(p));
        }
    }

    @Override
    public void registerOffline() {
        // 启用但离线的上级：重试注册（上级恢复后自动上线）
        List<GbParentPlatform> platforms = parentPlatformMapper.selectList(
                new LambdaQueryWrapper<GbParentPlatform>()
                        .eq(GbParentPlatform::getEnabled, 1)
                        .ne(GbParentPlatform::getStatus, 1));
        for (GbParentPlatform p : platforms) {
            commander.register(p, expiresOf(p));
        }
    }

    @Override
    public void refreshAll() {
        // 所有启用的上级：重发注册，在 expires 过期前刷新
        List<GbParentPlatform> platforms = enabledPlatforms();
        for (GbParentPlatform p : platforms) {
            commander.register(p, expiresOf(p));
        }
    }

    @Override
    public void keepaliveAll() {
        List<GbParentPlatform> online = parentPlatformMapper.selectList(
                new LambdaQueryWrapper<GbParentPlatform>()
                        .eq(GbParentPlatform::getEnabled, 1)
                        .eq(GbParentPlatform::getStatus, 1));
        for (GbParentPlatform p : online) {
            commander.keepalive(p,
                    () -> registerState.resetKeepaliveFail(p.getServerGbId()),
                    () -> {
                        int fails = registerState.incKeepaliveFail(p.getServerGbId());
                        log.warn("[级联心跳] 超时 {}/{} -> {}",
                                fails, PlatformRegisterState.KEEPALIVE_FAIL_THRESHOLD, p.getServerGbId());
                        if (fails >= PlatformRegisterState.KEEPALIVE_FAIL_THRESHOLD) {
                            log.info("[级联心跳] 连续 {} 次超时，判离线 -> {}", fails, p.getServerGbId());
                            offline(p);
                            registerState.resetKeepaliveFail(p.getServerGbId());
                        }
                    });
        }
    }

    @Override
    public void online(GbParentPlatform platform) {
        parentPlatformMapper.update(null, new LambdaUpdateWrapper<GbParentPlatform>()
                .eq(GbParentPlatform::getId, platform.getId())
                .set(GbParentPlatform::getStatus, 1));
        registerState.resetKeepaliveFail(platform.getServerGbId());
    }

    @Override
    public void offline(GbParentPlatform platform) {
        parentPlatformMapper.update(null, new LambdaUpdateWrapper<GbParentPlatform>()
                .eq(GbParentPlatform::getId, platform.getId())
                .set(GbParentPlatform::getStatus, 0));
    }

    @Override
    public void unregisterAll() {
        List<GbParentPlatform> online = parentPlatformMapper.selectList(
                new LambdaQueryWrapper<GbParentPlatform>().eq(GbParentPlatform::getStatus, 1));
        for (GbParentPlatform p : online) {
            commander.register(p, 0);
        }
    }

    private List<GbParentPlatform> enabledPlatforms() {
        return parentPlatformMapper.selectList(
                new LambdaQueryWrapper<GbParentPlatform>().eq(GbParentPlatform::getEnabled, 1));
    }
}
