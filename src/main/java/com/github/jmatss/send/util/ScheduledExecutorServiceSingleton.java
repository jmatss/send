package com.github.jmatss.send.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ScheduledExecutorServiceSingleton {
    private static ScheduledExecutorServiceSingleton instance;
    private final ScheduledExecutorService scheduler;

    private ScheduledExecutorServiceSingleton() {
        int processors = Runtime.getRuntime().availableProcessors();
        this.scheduler = Executors.newScheduledThreadPool(processors);
    }

    public static synchronized ScheduledExecutorService getInstance() {
        if (ScheduledExecutorServiceSingleton.instance == null)
            ScheduledExecutorServiceSingleton.instance = new ScheduledExecutorServiceSingleton();
        return ScheduledExecutorServiceSingleton.instance.scheduler;
    }
}
