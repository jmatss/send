package com.github.jmatss.send.packet;

public class FileInfoPacket {
    public final int nameLength;
    public final String name;
    public final long fileLength;
    public final int hashType;
    public final byte[] digest;

    public FileInfoPacket(int nameLength, String name, long fileLength, int hashType, byte[] digest) {
        this.nameLength = nameLength;
        this.name = name;
        this.fileLength = fileLength;
        this.hashType = hashType;
        this.digest = digest;
    }
}
