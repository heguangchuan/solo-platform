package com.solo.gb28181.support;

import com.solo.gb28181.constant.GbRedisKeys;
import com.solo.gb28181.entity.GbInviteSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordStreamRegistryTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private RecordStreamRegistry registry;

    @Test
    void activeOwnershipHasNoTtlAndRetiredOwnershipExpires() {
        GbInviteSession session = new GbInviteSession();
        session.setStreamId("REC_device_channel");
        session.setDeviceId("device");
        session.setChannelId("channel");
        session.setServerId("server-1");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.writeValueAsString(any())).thenReturn("json");
        String key = GbRedisKeys.RECORD_STREAM + session.getStreamId();

        registry.register(session);
        registry.retire(session);

        verify(valueOperations).set(key, "json");
        verify(valueOperations).set(key, "json", Duration.ofMinutes(10));
    }
}
