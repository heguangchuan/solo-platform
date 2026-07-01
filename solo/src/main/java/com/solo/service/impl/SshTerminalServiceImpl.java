package com.solo.service.impl;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.solo.common.ServiceException;
import com.solo.config.TerminalProperties;
import com.solo.entity.SysServer;
import com.solo.service.SshTerminalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * SSH Web 终端服务实现类
 */
@Service
@RequiredArgsConstructor
public class SshTerminalServiceImpl implements SshTerminalService {

    private static final String AUTH_TYPE_PASSWORD = "PASSWORD";
    private static final String AUTH_TYPE_PRIVATE_KEY = "PRIVATE_KEY";

    private final TerminalProperties terminalProperties;

    @Override
    public TerminalSession openShell(SysServer server, OutputHandler outputHandler, int cols, int rows) {
        try {
            JSch jsch = new JSch();
            if (AUTH_TYPE_PRIVATE_KEY.equals(server.getAuthType())) {
                jsch.addIdentity("server-" + server.getId(), server.getPrivateKeySecret().getBytes(StandardCharsets.UTF_8), null, null);
            }
            Session session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
            if (AUTH_TYPE_PASSWORD.equals(server.getAuthType())) {
                session.setPassword(server.getPasswordSecret());
            }
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect(terminalProperties.getConnectTimeoutMs());

            ChannelShell shell = (ChannelShell) session.openChannel("shell");
            shell.setPty(true);
            shell.setPtySize(cols, rows, cols * 8, rows * 16);
            InputStream inputStream = shell.getInputStream();
            OutputStream outputStream = shell.getOutputStream();
            shell.connect(terminalProperties.getConnectTimeoutMs());
            return new JschTerminalSession(session, shell, inputStream, outputStream, outputHandler,
                    terminalProperties.getOutputBufferSize());
        } catch (Exception e) {
            throw new ServiceException("SSH连接失败：" + e.getMessage());
        }
    }

    private static class JschTerminalSession implements TerminalSession {
        private final Session session;
        private final ChannelShell shell;
        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final OutputHandler outputHandler;
        private final Thread readerThread;
        private volatile boolean closed = false;

        private JschTerminalSession(Session session, ChannelShell shell, InputStream inputStream,
                                    OutputStream outputStream, OutputHandler outputHandler, int bufferSize) {
            this.session = session;
            this.shell = shell;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.outputHandler = outputHandler;
            this.readerThread = new Thread(() -> readLoop(bufferSize), "ssh-terminal-reader-" + session.getHost());
            this.readerThread.setDaemon(true);
            this.readerThread.start();
        }

        @Override
        public void write(String data) {
            if (closed || data == null) return;
            try {
                outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
            } catch (Exception e) {
                throw new ServiceException("终端输入失败：" + e.getMessage());
            }
        }

        @Override
        public void resize(int cols, int rows) {
            if (closed || cols <= 0 || rows <= 0) return;
            shell.setPtySize(cols, rows, cols * 8, rows * 16);
        }

        @Override
        public void close() {
            closed = true;
            readerThread.interrupt();
            if (shell != null && shell.isConnected()) {
                shell.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }

        private void readLoop(int bufferSize) {
            byte[] buffer = new byte[Math.max(bufferSize, 1024)];
            try {
                while (!closed && shell.isConnected()) {
                    int len = inputStream.read(buffer);
                    if (len < 0) break;
                    if (len > 0) {
                        outputHandler.onData(new String(buffer, 0, len, StandardCharsets.UTF_8));
                    }
                }
            } catch (Exception ignored) {
                // 关闭连接时 read 会被中断，这里不再额外抛出
            }
        }
    }
}
