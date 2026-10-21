package com.solo.system.service;

import com.solo.system.response.OnlineUserVO;

import java.util.List;

public interface SysOnlineService {

    /** 列出当前在线用户，可按账号、昵称模糊筛选 */
    List<OnlineUserVO> list(String username, String nickname);

    void kick(String username);
}
