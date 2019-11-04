package com.github.jmatss.send;

public enum MessageType {
    PUBLISH(0),
    SUBSCRIBE(1),
    File(2),
    Text(3);
    private final int i;

    MessageType(int i) {
        this.i = i;
    }

    public int value() {
        return this.i;
    }
}
