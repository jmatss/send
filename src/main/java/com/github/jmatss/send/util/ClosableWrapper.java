package com.github.jmatss.send.util;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClosableWrapper {
    private static final Logger LOGGER = Logger.getLogger(ClosableWrapper.class.getName());
    private final ServerSocket serverSocket;
    private final List<Future<?>> futures;

    public ClosableWrapper(ServerSocket serverSocket, Future<?>... futures) {
        this.serverSocket = serverSocket;
        this.futures = Arrays.asList(futures);
    }

    public void close() {
        this.futures.forEach((f) -> f.cancel(true));
        try {
            this.serverSocket.close();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Unable to close serverSocket.");
        }
    }
}
