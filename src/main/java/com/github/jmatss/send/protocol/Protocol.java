package com.github.jmatss.send.protocol;

import com.github.jmatss.send.HashType;
import com.github.jmatss.send.MessageType;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Random;

/*
*** Format ***
    if (MessageType::PUBLISH && SubMessageType::TEXT):
        MessageType (1 byte)
        | Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)       //
        | Random ID Number (4 bytes)    // unique
    if (MessageType::PUBLISH && SubMessageType::FILE):
        MessageType (1 byte)
        | Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)
        | Random ID Number (4 bytes)
        TODO: Maybe add amount of files.

    if (MessageType::TEXT):
        MessageType (1 byte)
        | Length (4 bytes)
        | Text ("Length" bytes)

    *** TEXT COM ***
    A                   B
    Publish ->
                        <- Subscribe
    Text ->
    ...

    A                   B
                        <- Subscribe
    Publish ->
    Text ->
    ...
*/

/*
    if (MessageType::FILE_INFO):
        MessageType (1 byte)
        | NameLength (4 bytes)
        | Name ("NameLength" bytes)
        | TotalFileLength (8 bytes)
        | HashType (1 byte)
        | Hash-digest (of whole file) (x bytes) (can be zero bytes if HashType::None)
    if (MessageType::FILE):
        MessageType (1 byte)
        | Index (4 bytes)
        | Length (4 bytes)
        | PieceContent ("Length" bytes)
        | HashType (1 byte)
        | Hash-digest (of this piece) (x bytes) (can be zero bytes if HashType::None)
    if (MessageType::FILE_DONE):
        MessageType (1 byte)
        | Index (4 bytes)

    *** FILE COM ***
    A                   B
    Publish ->
                        <- Subscribe
    File_info ->
        File ->
        ...
    ...

    A                   B
                        <- Subscribe
    Publish ->
    File_info ->
        File ->
        ...
    ...

 TODO:
  compression of files

*/

abstract public class Protocol<T> {
    public static final int MAX_PUBLISH_PACKET_SIZE = 1 + 1 + 256 + 1 + 4;
    public static final int MIN_PUBLISH_PACKET_SIZE = 1 + 1 + 1 + 4;
    public static final int MAX_PIECE_SIZE = 1 << 16;
    public static final int DEFAULT_PIECE_SIZE = 1 << 16;
    public static final HashType DEFAULT_HASH_TYPE = HashType.SHA1;
    public static final String ENCODING = "UTF-8";
    private byte[] id;

    // Flags: R=0, P=0, T=1, Scope: Link-local (2)
    // https://tools.ietf.org/html/rfc4291#section-2.7
    public static final String DEFAULT_MULTICAST_IPV6 = "ff12::";
    public static final String DEFAULT_MULTICAST_IPV4 = "224.0.0.3";
    public static final int DEFAULT_PORT = 7301;

    abstract public MessageType getMessageType();

    abstract public Iterable<T> iter();

    public byte[] getPublishPacket(String topic) {
        if (this.id == null)
            this.id = ByteBuffer.allocate(4).putInt(new Random().nextInt()).array();

        byte[] topicBytes;
        try {
            topicBytes = topic.getBytes(ENCODING);
        } catch (UnsupportedEncodingException e) {
            // Should never happen since the ENCODING is a hardcoded correct encoding.
            throw new RuntimeException(e);
        }

        return ByteBuffer.allocate(1 + 4 + topicBytes.length + 1 + 4)
                .put((byte) MessageType.PUBLISH.value())
                .putInt(topicBytes.length)
                .put(topicBytes)
                .put((byte) getMessageType().value())
                .put(this.id)
                .array();
    }

    private byte[] getId() {
        if (this.id == null)
            this.id = ByteBuffer.allocate(4).putInt(new Random().nextInt()).array();
        return this.id.clone();
    }
}
