package com.github.jmatss.send.util;

import java.util.TreeSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LockableTreeSet<T> extends TreeSet<T> implements AutoCloseable {
    private final Lock lock;

    public LockableTreeSet() {
        super();
        this.lock = new ReentrantLock();
    }

    public LockableTreeSet lock() {
        this.lock.lock();
        return this;
    }

    public LockableTreeSet unlock() {
        this.lock.unlock();
        return this;
    }

    @Override
    public void close() {
        this.lock.unlock();
    }
}
