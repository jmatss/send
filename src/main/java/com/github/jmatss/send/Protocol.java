package com.github.jmatss.send;

import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

/*
*** Format ***
TODO:
    if (MessageType::PUBLISH && SubMessageType::TEXT):
        Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)       //
        | Random ID Number (8 bytes)    // unique
    if (MessageType::PUBLISH && SubMessageType::FILE):
        Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)
        | Random ID Number (8 bytes) (unique)

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
    public static final int MAX_PIECE_SIZE = 1 << 16;
    public static final int DEFAULT_PIECE_SIZE = 1 << 16;
    public static final HashType DEFAULT_HASH_TYPE = HashType.SHA1;
    public static final String ENCODING = "UTF-8";

    abstract public MessageType getMessageType();
    abstract public Iterable<T> iter();
}
