package com.github.jmatss.send;

public enum HashType {
    NONE(0, null, 0),
    SHA1(1, "SHA-1", 20),
    MD5(2, "MD5", 16);
    private final int i;
    private final String hash;
    private final int size;

    private HashType(int i, String hash, int size) {
        this.i = i;
        this.hash = hash;
        this.size = size;
    }

    public int value() {
        return this.i;
    }

    public int size() {
        return this.size;
    }

    public String toString() {
        return this.hash;
    }
}
