package com.github.jmatss.send.util;

import java.util.HashSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockableHashSet<T> extends HashSet<T> implements AutoCloseable {
    private final Lock lock;

    public LockableHashSet() {
        super();
        this.lock = new ReentrantLock();
    }

    public LockableHashSet lock() {
        this.lock.lock();
        return this;
    }

    public LockableHashSet unlock() {
        this.lock.unlock();
        return this;
    }

    @Override
    public void close() {
        this.lock.unlock();
    }
}
