package com.solo.system.service;

import com.solo.system.response.OnlineUserVO;

import java.util.List;

public interface SysOnlineService {

    List<OnlineUserVO> list(String username, String nickname);

    void kick(String username);
}
