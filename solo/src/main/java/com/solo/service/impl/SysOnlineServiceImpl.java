package com.solo.service.impl;

import com.solo.common.ServiceException;
import com.solo.response.OnlineUserVO;
import com.solo.service.SysOnlineService;
import com.solo.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 在线用户服务实现：基于 Redis 中的 {@code auth:online} 集合与 hash 数据。
 */
@Service
@RequiredArgsConstructor
public class SysOnlineServiceImpl implements SysOnlineService {

    private final JwtUtils jwtUtils;

    @Override
    public List<OnlineUserVO> list(String usernameKw, String nicknameKw) {
        Set<String> usernames = jwtUtils.listOnlineUsernames();
        List<OnlineUserVO> result = new ArrayList<>();
        if (usernames == null || usernames.isEmpty()) return result;

        String userKw = usernameKw == null ? null : usernameKw.trim();
        String nickKw = nicknameKw == null ? null : nicknameKw.trim();

        for (String username : usernames) {
            Map<Object, Object> session = jwtUtils.getSession(username);
            if (session == null || session.isEmpty()) {
                // hash 已过期但索引集合还在，顺手清理
                jwtUtils.removeToken(username);
                continue;
            }
            String nickname = str(session.get(JwtUtils.FIELD_NICKNAME));
            if (userKw != null && !userKw.isEmpty() && !username.contains(userKw)) continue;
            if (nickKw != null && !nickKw.isEmpty() && (nickname == null || !nickname.contains(nickKw))) continue;

            OnlineUserVO vo = new OnlineUserVO();
            vo.setUsername(username);
            vo.setNickname(nickname);
            String userIdStr = str(session.get(JwtUtils.FIELD_USER_ID));
            if (userIdStr != null && !userIdStr.isEmpty()) {
                try { vo.setUserId(Long.parseLong(userIdStr)); } catch (NumberFormatException ignored) { }
            }
            vo.setLoginTime(parseTime(str(session.get(JwtUtils.FIELD_LOGIN_TIME))));
            vo.setLoginIp(str(session.get(JwtUtils.FIELD_LOGIN_IP)));
            vo.setClientSource(str(session.get(JwtUtils.FIELD_CLIENT_SOURCE)));
            vo.setUserAgent(str(session.get(JwtUtils.FIELD_USER_AGENT)));
            result.add(vo);
        }
        // 按登录时间倒序，最新登录的在前
        result.sort(Comparator.comparing(OnlineUserVO::getLoginTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    @Override
    public void kick(String username) {
        if (username == null || username.isBlank()) {
            throw new ServiceException("用户名不能为空");
        }
        if (jwtUtils.getToken(username) == null) {
            throw new ServiceException("该用户当前不在线");
        }
        jwtUtils.removeToken(username);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static LocalDateTime parseTime(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }
}
