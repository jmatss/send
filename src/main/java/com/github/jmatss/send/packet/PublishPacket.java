package com.github.jmatss.send.packet;

import com.github.jmatss.send.Controller;
import com.github.jmatss.send.type.MessageType;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class PublishPacket implements Packet {
    private final MessageType messageType;
    private final String topic;
    private final MessageType subMessageType;
    private final int port;
    private final byte[] id;
    private byte[] packet;

    public PublishPacket(String topic, MessageType subMessageType, int port, byte[] id) {
        this.messageType = MessageType.PUBLISH;
        this.topic = topic;
        this.subMessageType = subMessageType;
        this.port = port;
        this.id = id;
        this.packet = null;
    }

    public String getTopic() {
        return this.topic;
    }

    public byte[] getId() {
        return this.id;
    }

    public int getPort() {
        return this.port;
    }

    public MessageType getSubMessageType() {
        return this.subMessageType;
    }

    @Override
    public byte[] getBytes() throws UnsupportedEncodingException {
        if (this.packet != null)
            return this.packet;

        byte[] topicBytes = this.topic.getBytes(Controller.ENCODING);
        ByteBuffer buf = ByteBuffer.allocate(1 + 1 + topicBytes.length + 1 + 4 + 4)
                .put((byte) this.messageType.getValue())
                .put((byte) topicBytes.length)
                .put(topicBytes)
                .put((byte) this.subMessageType.getValue())
                .putInt(this.port)
                .put(this.id);

        this.packet = buf.array();
        return this.packet;
    }

    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }
}