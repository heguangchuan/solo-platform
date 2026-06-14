package com.solo.common;

import lombok.Data;

/**
 * 统一API响应结果封装
 * 所有API接口返回此结构，包含状态码、提示信息和本体数据
 *
 * @param <T> 响应数据的具体类型
 */
@Data
public class Result<T> {

    /**
     * 响应状态码，200表示成功，其他表示失败
     */
    private int code;

    /**
     * 响应提示信息
     */
    private String message;

    /**
     * 响应本体数据
     */
    private T data;

    /**
     * 私有构造方法，防止外部实例化
     */
    private Result() {}

    /**
     * 创建成功的响应结果
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 封装好的成功响应
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    /**
     * 创建无数据的成功响应
     *
     * @param <T> 数据类型
     * @return 封装好的成功响应
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 创建失败的响应结果
     *
     * @param code    错误状态码
     * @param message 错误提示信息
     * @param <T>     数据类型
     * @return 封装好的失败响应
     */
    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    /**
     * 创建默认错误响应（状态码500）
     *
     * @param message 错误提示信息
     * @param <T>     数据类型
     * @return 封装好的失败响应
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
}
