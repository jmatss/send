package com.github.jmatss.send.packet;

public class PublishPacket {
    public final int subMessageType;
    public final byte topicLength;
    public final String topic;
    public final int port;
    public final byte[] id;

    public PublishPacket(int subMessageType, byte topicLength, String topic, int port, byte[] id) {
        this.subMessageType = subMessageType;
        this.topicLength = topicLength;
        this.topic = topic;
        this.port = port;
        this.id = id;
    }
}