package com.solo.aop;

import com.solo.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionCache {

    /** key=userId, value=权限编码列表 */
    private final Map<Long, List<String>> cache = new ConcurrentHashMap<>();

    private final SysUserMapper sysUserMapper;


    public List<String> get(Long userId) {
        return cache.computeIfAbsent(userId, id -> {
            try {
                return sysUserMapper.selectPermissionCodes(id);
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
