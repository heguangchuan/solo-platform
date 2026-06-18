package com.solo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.solo.common.ServiceException;
import com.solo.common.SystemConstants;
import com.solo.entity.SysTask;
import com.solo.mapper.SysTaskMapper;
import com.solo.request.TaskPageRequest;
import com.solo.request.TaskRequest;
import com.solo.service.SysTaskService;
import com.solo.task.DynamicTaskScheduler;
import com.solo.task.TaskHandlerRegistry;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysTaskServiceImpl extends ServiceImpl<SysTaskMapper, SysTask> implements SysTaskService {

    private final DynamicTaskScheduler dynamicTaskScheduler;
    private final TaskHandlerRegistry taskHandlerRegistry;

    public SysTaskServiceImpl(@Lazy DynamicTaskScheduler dynamicTaskScheduler, TaskHandlerRegistry taskHandlerRegistry) {
        this.dynamicTaskScheduler = dynamicTaskScheduler;
        this.taskHandlerRegistry = taskHandlerRegistry;
    }

    @Override
    public Page<SysTask> pageQuery(TaskPageRequest req) {
        Long page = req.getPage() != null ? req.getPage() : 1L;
        Long pageSize = req.getPageSize() != null ? req.getPageSize() : 20L;
        Page<SysTask> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<SysTask> wrapper = new LambdaQueryWrapper<>();
        if (req.getTaskName() != null && !req.getTaskName().isBlank()) {
            wrapper.like(SysTask::getTaskName, req.getTaskName());
        }
        if (req.getTaskCode() != null && !req.getTaskCode().isBlank()) {
            wrapper.like(SysTask::getTaskCode, req.getTaskCode());
        }
        if (req.getStatus() != null) {
            wrapper.eq(SysTask::getStatus, req.getStatus());
        }
        wrapper.orderByDesc(SysTask::getCreateTime);
        return page(pageParam, wrapper);
    }

    @Override
    public void createTask(TaskRequest request) {
        validateRequest(request, null);
        SysTask task = new SysTask();
        fillTask(task, request);
        if (task.getStatus() == null) {
            task.setStatus(SystemConstants.STATUS_DISABLED);
        }
        save(task);
        if (task.getStatus() == SystemConstants.STATUS_ENABLED) {
            dynamicTaskScheduler.schedule(task);
        }
    }

    @Override
    public void updateTask(Long id, TaskRequest request) {
        SysTask task = getExistingTask(id);
        validateRequest(request, id);
        fillTask(task, request);
        updateById(task);
        if (task.getStatus() == SystemConstants.STATUS_ENABLED) {
            dynamicTaskScheduler.schedule(task);
        } else {
            dynamicTaskScheduler.stop(id);
        }
    }

    @Override
    public void deleteTask(Long id) {
        getExistingTask(id);
        dynamicTaskScheduler.stop(id);
        removeById(id);
    }

    @Override
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        ids.forEach(dynamicTaskScheduler::stop);
        removeByIds(ids);
    }

    @Override
    public void switchTaskStatus(Long id, Integer status) {
        SysTask task = getExistingTask(id);
        if (status != SystemConstants.STATUS_ENABLED && status != SystemConstants.STATUS_DISABLED) {
            throw new ServiceException("状态值不合法");
        }
        task.setStatus(status);
        updateById(task);
        if (status == SystemConstants.STATUS_ENABLED) {
            validateRunnableTask(task);
            dynamicTaskScheduler.schedule(task);
        } else {
            dynamicTaskScheduler.stop(id);
        }
    }

    @Override
    public void runTaskImmediately(Long id) {
        SysTask task = getExistingTask(id);
        validateRunnableTask(task);
        dynamicTaskScheduler.runOnce(id);
    }

    private void validateRequest(TaskRequest request, Long currentId) {
        if (request.getTaskName() == null || request.getTaskName().isBlank()) {
            throw new ServiceException("任务名称不能为空");
        }
        if (request.getTaskCode() == null || request.getTaskCode().isBlank()) {
            throw new ServiceException("任务编码不能为空");
        }
        if (request.getCron() == null || request.getCron().isBlank()) {
            throw new ServiceException("Cron表达式不能为空");
        }
        if (!CronExpression.isValidExpression(request.getCron())) {
            throw new ServiceException("Cron表达式不合法");
        }
        if (request.getHandlerName() == null || request.getHandlerName().isBlank()) {
            throw new ServiceException("任务处理器不能为空");
        }
        if (request.getStatus() != null
                && request.getStatus() != SystemConstants.STATUS_ENABLED
                && request.getStatus() != SystemConstants.STATUS_DISABLED) {
            throw new ServiceException("状态值不合法");
        }
        if (!taskHandlerRegistry.contains(request.getHandlerName())) {
            throw new ServiceException("任务处理器不存在");
        }
        LambdaQueryWrapper<SysTask> wrapper = new LambdaQueryWrapper<SysTask>()
                .eq(SysTask::getTaskCode, request.getTaskCode());
        if (currentId != null) {
            wrapper.ne(SysTask::getId, currentId);
        }
        if (count(wrapper) > 0) {
            throw new ServiceException("任务编码已存在");
        }
    }

    private void validateRunnableTask(SysTask task) {
        if (!CronExpression.isValidExpression(task.getCron())) {
            throw new ServiceException("Cron表达式不合法");
        }
        if (!taskHandlerRegistry.contains(task.getHandlerName())) {
            throw new ServiceException("任务处理器不存在");
        }
    }

    private SysTask getExistingTask(Long id) {
        SysTask task = getById(id);
        if (task == null) {
            throw new ServiceException(SystemConstants.HTTP_NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private void fillTask(SysTask task, TaskRequest request) {
        task.setTaskName(request.getTaskName());
        task.setTaskCode(request.getTaskCode());
        task.setCron(request.getCron());
        task.setHandlerName(request.getHandlerName());
        task.setTaskParams(request.getTaskParams());
        task.setStatus(request.getStatus());
        task.setRemark(request.getRemark());
    }
}
