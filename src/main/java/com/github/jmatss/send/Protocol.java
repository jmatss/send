package com.github.jmatss.send;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

/*
*** Format ***
TODO:
    if (MessageType::Publish && SubMessageType::Text):
        Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)       //
        | Random ID Number (8 bytes)    // unique
    if (MessageType::Publish && SubMessageType::File):
        Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)
        | Random ID Number (8 bytes) (unique)

    if (MessageType::Text):
        MessageType (1 byte)
        | Length (4 bytes)
        | Text ("Length" bytes)
    if (MessageType::File):
        MessageType (1 byte)
        | HashType (1 byte)
        | Hash-digest (x bytes) (can be zero bytes if HashType::None)
        | Index (8 bytes)
        | Length (4 bytes)
        | File ("Length" bytes)
 TODO:
  compression of files

*/

abstract public class Protocol implements Iterable<byte[]> {
    public static final int MAX_PACKET_SIZE = 1 << 16;
    public static final int MAX_PIECE_SIZE = 1 << 16;
    public static final HashType DEFAULT_HASH_TYPE = HashType.SHA1;

    abstract public HashType getHashType();
}
