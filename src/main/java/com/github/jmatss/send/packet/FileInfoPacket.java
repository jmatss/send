package com.github.jmatss.send.packet;

import com.github.jmatss.send.Controller;
import com.github.jmatss.send.type.HashType;
import com.github.jmatss.send.type.MessageType;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public class FileInfoPacket implements Packet {
    private final MessageType messageType;
    private final String name;
    private final long fileLength;
    private final HashType hashType;
    private final byte[] digest;
    private byte[] packet;

    public FileInfoPacket(String name, long fileLength, HashType hashType, byte[] digest) {
        this.messageType = MessageType.FILE_INFO;
        this.name = name;
        this.fileLength = fileLength;
        this.hashType = hashType;
        this.digest = digest;
        this.packet = null;
    }

    public String getName() {
        return this.name;
    }

    public long getFileLength() {
        return this.fileLength;
    }

    @Override
    public byte[] getBytes() throws UnsupportedEncodingException {
        if (this.packet != null)
            return this.packet;

        byte[] nameBytes = this.name.getBytes(Controller.ENCODING);
        ByteBuffer buf = ByteBuffer.allocate(1 + 4 + nameBytes.length + 8 + 1 + this.hashType.getSize())
                .put((byte) this.messageType.getValue())
                .putInt(nameBytes.length)
                .put(nameBytes)
                .putLong(this.fileLength)
                .put((byte) this.hashType.getValue())
                .put(this.digest);

        this.packet = buf.array();
        return this.packet;
    }

    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }
}
