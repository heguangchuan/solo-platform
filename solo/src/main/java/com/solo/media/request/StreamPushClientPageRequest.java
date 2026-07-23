package com.solo.media.request;

import lombok.Data;

@Data
public class StreamPushClientPageRequest {
    private Long page = 1L;
    private Long pageSize = 20L;

    private String clientId;
    private Integer enabled;
}
