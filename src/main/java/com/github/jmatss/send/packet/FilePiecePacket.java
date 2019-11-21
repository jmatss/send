package com.github.jmatss.send.packet;

import com.github.jmatss.send.type.HashType;
import com.github.jmatss.send.type.MessageType;

import java.nio.ByteBuffer;

public class FilePiecePacket implements Packet {
    private final MessageType messageType;
    private final int index;
    private final byte[] data;
    private final HashType hashType;
    private byte[] packet;

    public FilePiecePacket(int index, byte[] data, HashType hashType) {
        this.messageType = MessageType.FILE_PIECE;
        this.index = index;
        this.data = data;
        this.hashType = hashType;
        this.packet = null;
    }

    @Override
    public byte[] getBytes() {
        if (this.packet != null)
            return this.packet;

        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + 4 + this.data.length + 1 + this.hashType.getSize())
                .put((byte) this.messageType.getValue())
                .putInt(this.index)
                .putInt(this.data.length)
                .put(this.data)
                .put((byte) this.hashType.getValue());
        if (this.hashType != HashType.NONE)
            buf.put(this.hashType.getMessageDigest().digest(this.data));

        this.packet = buf.array();
        return this.packet;
    }

    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }
}
