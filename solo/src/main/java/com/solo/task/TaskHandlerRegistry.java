package com.solo.task;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    public TaskHandlerRegistry(List<TaskHandler> taskHandlers) {
        for (TaskHandler taskHandler : taskHandlers) {
            handlers.put(taskHandler.getName(), taskHandler);
        }
    }

    public TaskHandler getHandler(String name) {
        return handlers.get(name);
    }

    public boolean contains(String name) {
        return handlers.containsKey(name);
    }
}
