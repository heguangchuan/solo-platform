package com.solo.aop;

import com.solo.system.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户权限缓存
 *
 * <p>用户第一次通过 {@link PermissionAspect} 时把权限列表加载进内存，之后同一 userId 直接命中。
 * 权限变更后（角色 / 分配 / 删除）必须显式失效——本组件对外只暴露 {@link #evict(Long)} 和
 * {@link #evictAll()}，业务侧调用不需要感知底层数据结构。</p>
 *
 * <p><b>为什么单进程内存缓存足够</b>：solo 部署一般单实例；分布式部署再换 Redis
 * 的话本组件是唯一改点，不影响调用方。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCache {

    /** key=userId, value=权限编码列表 */
    private final Map<Long, List<String>> cache = new ConcurrentHashMap<>();

    private final SysUserService sysUserService;

    /**
     * 取用户权限；缓存未命中时回源到 sys_user_role ⨝ sys_role_permission ⨝ sys_permission。
     */
    public List<String> get(Long userId) {
        return cache.computeIfAbsent(userId, id -> {
            try {
                return sysUserService.getPermissionCodes(id);
            } catch (Exception e) {
                log.error("[权限缓存] 加载失败 userId={}", id, e);
                return List.of();
            }
        });
    }

    /** 失效单个用户：分配角色、修改用户角色、停用用户等场景 */
    public void evict(Long userId) {
        if (userId == null) return;
        if (cache.remove(userId) != null) {
            log.info("[权限缓存] 失效 userId={}", userId);
        }
    }

    /** 失效全部：角色权限被批量重写、系统权限表变动等场景 */
    public void evictAll() {
        int size = cache.size();
        cache.clear();
        log.info("[权限缓存] 全部失效 count={}", size);
    }
}
