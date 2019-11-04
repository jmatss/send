package com.github.jmatss.send;

public enum HashType {
    NONE(0, ""), SHA1(1, "SHA-1");
    private final int i;
    private final String hash;

    private HashType(int i, String hash) {
        this.i = i;
        this.hash = hash;
    }

    public int value() {
        return this.i;
    }

    public String toString() {
        return this.hash;
    }
}
