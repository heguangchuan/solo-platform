package com.solo.task;

public interface TaskHandler {
    String getName();

    void execute(String params) throws Exception;
}
