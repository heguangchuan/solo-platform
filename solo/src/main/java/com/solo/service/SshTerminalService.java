package com.solo.service;

import com.solo.entity.SysServer;

/**
 * SSH Web 终端服务接口
 */
public interface SshTerminalService {

    /**
     * 打开服务器 Shell 会话
     */
    TerminalSession openShell(SysServer server, OutputHandler outputHandler, int cols, int rows);

    /**
     * 终端输出回调
     */
    @FunctionalInterface
    interface OutputHandler {
        void onData(String data);
    }

    /**
     * SSH 终端会话
     */
    interface TerminalSession extends AutoCloseable {

        /**
         * 写入终端输入
         */
        void write(String data);

        /**
         * 调整终端尺寸
         */
        void resize(int cols, int rows);

        /**
         * 关闭会话
         */
        @Override
        void close();
    }
}
