package com.solo.excel;

import cn.idev.excel.context.AnalysisContext;
import cn.idev.excel.event.AnalysisEventListener;
import cn.idev.excel.util.ListUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * FastExcel监听
 * <a href="https://FastExcel.opensource.alibaba.com/docs/current/quickstart/read">...</a>
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class ExcelListener<T> extends AnalysisEventListener<T> {

    /**
     * 每隔5条存储数据库，实际使用中可以100条，然后清理list ，方便内存回收
     */
    private static final int BATCH_COUNT = 500;

    /**
     * 缓存的数据
     */
    private List<T> cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);

    private Predicate<T> predicate;

    private Consumer<List<T>> consumer;

    @Override
    public void invoke(T data, AnalysisContext analysisContext) {
        //log.info("解析到数据:{}", JsonUtils.obj2Json(data));
        if (predicate != null && !predicate.test(data)) {
            return;
        }
        cachedDataList.add(data);
        // 达到BATCH_COUNT了，需要去存储一次数据库，防止数据几万条数据在内存，容易OOM
        if (cachedDataList.size() >= BATCH_COUNT) {
            if (consumer != null) {
                consumer.accept(cachedDataList);
            }
            // 存储完成清理 list
            cachedDataList = ListUtils.newArrayListWithExpectedSize(BATCH_COUNT);
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        if (consumer != null) {
            consumer.accept(cachedDataList);
        }
        log.info("所有数据解析完成！");
    }
}