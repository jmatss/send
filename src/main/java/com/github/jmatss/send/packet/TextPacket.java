package com.github.jmatss.send.packet;

import com.github.jmatss.send.Controller;
import com.github.jmatss.send.type.MessageType;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class TextPacket implements Packet {
    private final MessageType messageType;
    private final int index;
    private final String text;
    private byte[] packet;

    public TextPacket(int index, String text) {
        this.messageType = MessageType.TEXT;
        this.index = index;
        this.text = text;
        this.packet = null;
    }

    @Override
    public byte[] getBytes() throws UnsupportedEncodingException {
        if (this.packet != null)
            return this.packet;

        byte[] textBytes = text.getBytes(Controller.ENCODING);
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4 + textBytes.length)
                .put((byte) this.messageType.getValue())
                .putInt(this.index)
                .putInt(textBytes.length)
                .put(textBytes);

        this.packet = buf.array();
        return this.packet;
    }

    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }
}
