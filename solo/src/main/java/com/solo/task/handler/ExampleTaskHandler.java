package com.solo.task.handler;

import com.solo.task.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExampleTaskHandler implements TaskHandler {

    @Override
    public String getName() {
        return "exampleTask";
    }

    @Override
    public void execute(String params) {
        log.info("执行示例定时任务: params={}", params);
    }
}
