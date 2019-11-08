package com.github.jmatss.send;

public enum MessageType {
    PUBLISH(0), SUBSCRIBE(1), FILE(2), TEXT(3), FILE_INFO(4), FILE_DONE(5);
    private final int i;

    MessageType(int i) {
        this.i = i;
    }

    public int value() {
        return this.i;
    }
}
