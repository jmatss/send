package com.github.jmatss.send.packet;

public class RequestPacket {
    public final int topicLength;
    public final String topic;
    public final byte[] id;

    public RequestPacket(int topicLength, String topic, byte[] id) {
        this.topicLength = topicLength;
        this.topic = topic;
        this.id = id;
    }
}
