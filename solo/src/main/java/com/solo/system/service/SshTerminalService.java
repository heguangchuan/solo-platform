package com.solo.system.service;

import com.solo.system.entity.SysServer;

public interface SshTerminalService {

    TerminalSession openShell(SysServer server, OutputHandler outputHandler, int cols, int rows);

    @FunctionalInterface
    interface OutputHandler {
        void onData(String data);
    }

    interface TerminalSession extends AutoCloseable {

        void write(String data);

        void resize(int cols, int rows);

        @Override
        void close();
    }
}
