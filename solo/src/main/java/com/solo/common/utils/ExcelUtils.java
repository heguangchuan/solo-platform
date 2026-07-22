package com.solo.common.utils;

import cn.idev.excel.FastExcel;
import com.solo.common.excel.ExcelListener;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExcelUtils {

    /**
     * @param inputStream 文件流
     * @param clazz       对象类型
     * @param predicate   预言
     * @param consumer    处理逻辑
     * @param <T>         泛型类型
     */
    public static <T> void readExcel(InputStream inputStream, Class<T> clazz, Predicate<T> predicate, Consumer<List<T>> consumer) {
        ExcelListener<T> excelListener = new ExcelListener<>();
        excelListener.setConsumer(consumer);
        excelListener.setPredicate(predicate);
        FastExcel.read(inputStream, clazz, excelListener).sheet().doRead();
    }

    /**
     * @param inputStream  文件流
     * @param clazz        对象类型
     * @param predicate    预言
     * @param consumer     处理逻辑
     * @param <T>          泛型类型
     * @param headerNumber 表头数
     */
    public static <T> void readExcel(InputStream inputStream, Class<T> clazz, Predicate<T> predicate, Consumer<List<T>> consumer, Integer headerNumber) {
        ExcelListener<T> excelListener = new ExcelListener<>();
        excelListener.setConsumer(consumer);
        excelListener.setPredicate(predicate);
        FastExcel.read(inputStream, clazz, excelListener).sheet().headRowNumber(headerNumber).doRead();
    }


    /**
     * @param inputStream   文件流
     * @param clazz         对象类型
     * @param excelListener excel监听器
     * @param <T>           泛型类型
     */
    public static <T> void readExcel(InputStream inputStream, Class<T> clazz, ExcelListener<T> excelListener) {
        FastExcel.read(inputStream, clazz, excelListener).sheet().doRead();
    }


    /**
     * @param inputStream   文件流
     * @param clazz         对象类型
     * @param excelListener excel监听器
     * @param <T>           泛型类型
     * @param headerNumber  表头数
     */
    public static <T> void readExcel(InputStream inputStream, Class<T> clazz, ExcelListener<T> excelListener, Integer headerNumber) {
        FastExcel.read(inputStream, clazz, excelListener).sheet().headRowNumber(headerNumber).doRead();
    }

    /**
     * @param outputStream 输出流
     * @param clazz        对象类型
     * @param sheetName    sheet名称
     * @param data         数据
     * @param <T>          泛型类
     */
    public static <T> void writeExcel(OutputStream outputStream, Class<T> clazz, String sheetName, List<T> data) {
        FastExcel.write(outputStream, clazz).sheet(sheetName).doWrite(data);
    }

    /**
     * 导出excel
     *
     * @param response  响应
     * @param fileName  文件名称
     * @param sheetName sheet名称
     * @param data      数据列表
     * @param clazz     类型
     * @param <T>       泛型
     * @throws IOException 异常
     */
    public static <T> void export(HttpServletResponse response, String fileName, String sheetName, List<T> data, Class<T> clazz) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.ms-excel");
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");
        FastExcel.write(response.getOutputStream(), clazz).sheet(sheetName).doWrite(data);
    }

}
