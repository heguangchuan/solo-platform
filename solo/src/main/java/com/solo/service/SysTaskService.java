package com.solo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.solo.entity.SysTask;
import com.solo.request.TaskPageRequest;
import com.solo.request.TaskRequest;

import java.util.List;

public interface SysTaskService extends IService<SysTask> {
    Page<SysTask> pageQuery(TaskPageRequest req);

    void createTask(TaskRequest request);

    void updateTask(Long id, TaskRequest request);

    void deleteTask(Long id);

    void deleteBatch(List<Long> ids);

    void switchTaskStatus(Long id, Integer status);

    void runTaskImmediately(Long id);
}
