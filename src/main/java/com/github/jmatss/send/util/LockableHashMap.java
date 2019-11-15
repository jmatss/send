package com.github.jmatss.send.util;

import java.util.HashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockableHashMap<K, V> extends HashMap<K, V> implements AutoCloseable {
    private final Lock lock;

    public LockableHashMap() {
        super();
        this.lock = new ReentrantLock();
    }

    public LockableHashMap(int size) {
        super(size);
        this.lock = new ReentrantLock();
    }

    public LockableHashMap lock() {
        this.lock.lock();
        return this;
    }

    public LockableHashMap unlock() {
        this.lock.unlock();
        return this;
    }

    @Override
    public void close() {
        this.lock.unlock();
    }
}
