package com.github.jmatss.send.packet;

import com.github.jmatss.send.Controller;
import com.github.jmatss.send.type.MessageType;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class RequestPacket implements Packet {
    private final MessageType messageType;
    private final String topic;
    private final byte[] id;
    private byte[] packet;

    public RequestPacket(String topic, byte[] id) {
        this.messageType = MessageType.REQUEST;
        this.topic = topic;
        this.id = id;
        this.packet = null;
    }

    public String getTopic() {
        return this.topic;
    }

    @Override
    public byte[] getBytes() throws UnsupportedEncodingException {
        if (this.packet != null)
            return this.packet;

        byte[] topicBytes = this.topic.getBytes(Controller.ENCODING);
        ByteBuffer buf = ByteBuffer.allocate(1 + 1 + topicBytes.length + 4)
                .put((byte) this.messageType.getValue())
                .put((byte) topicBytes.length)
                .put(topicBytes)
                .put(this.id);

        this.packet = buf.array();
        return this.packet;
    }

    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }
}
