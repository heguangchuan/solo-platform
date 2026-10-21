package com.solo.gb28181.service;

import com.solo.gb28181.entity.GbParentPlatform;

public interface PlatformRegisterService {

    void registerAll();

    void registerOffline();

    void refreshAll();

    void keepaliveAll();

    void online(GbParentPlatform platform);

    void offline(GbParentPlatform platform);

    void register(GbParentPlatform platform);

    void unregisterAll();
}
