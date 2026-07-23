package com.solo.media.request;

import lombok.Data;

import java.util.List;

@Data
public class StreamPushBatchDeleteRequest {
    private List<Long> ids;
}
