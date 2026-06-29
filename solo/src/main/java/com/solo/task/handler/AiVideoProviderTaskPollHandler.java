package com.solo.task.handler;

import com.solo.service.AiVideoGenerationService;
import com.solo.task.TaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AI视频供应商任务轮询处理器。
 */
@Component
@RequiredArgsConstructor
public class AiVideoProviderTaskPollHandler implements TaskHandler {

    private final AiVideoGenerationService aiVideoGenerationService;

    @Override
    public String getName() {
        return "aiVideoProviderTaskPoll";
    }

    @Override
    public void execute(String params) {
        aiVideoGenerationService.pollVideoProviderTasks();
    }
}
