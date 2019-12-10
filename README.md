A simple program for sending/receiving files/text on a local network.

Commands:

    l/ls/list
    p/pub/publish [<TOPIC>]
    up/unpublish <TOPIC>
    s/sub/subscribe <TOPIC>
    us/unsubscribe <TOPIC>
    o/path <DOWNLOAD_PATH>
    q/quit

Packet formats:

    if (MessageType::PUBLISH && (SubMessageType::TEXT || SubMessageType::FILE_PIECE)):
        MessageType (1 byte)
        | Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)
        | Port (4 bytes) (that this publisher listens on for TCP connection)
        | Random ID Number (4 bytes) (unique)

    if (MessageType::REQUEST):
        MessageType (1 byte)
        | Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | ID Number (4 bytes) (corresponding to the id in the PUBLISH message)

    if (MessageType::TEXT):
        MessageType (1 byte)
        | Index (4 bytes) (needed when splitting the TEXT into multiple packets)
        | Length (4 bytes)
        | Text ("Length" bytes)

    if (MessageType::FILE_INFO):
        MessageType (1 byte)
        | Name Length (4 bytes)
        | Name ("Name Length" bytes)
        | TotalFileLength (8 bytes)
        | HashType (1 byte) (can NOT be HashType::NONE)
        | Hash-digest (of whole file) (x bytes)
        
    if (MessageType::FILE_PIECE):
        MessageType (1 byte)
        | Index (4 bytes)
        | Length (4 bytes)
        | PieceContent ("Length" bytes)
        | HashType (1 byte)
        | Hash-digest (of this piece) (x bytes) (can be zero bytes if HashType::NONE)
        
    if (MessageType::YES || MessageType::NO || MessageType::DONE):
        MessageType (1 byte)

Communication:

    *** FILE COM ***
    A                   B
    Publish ->          (Subscribe)
    (Publish ->)        Subscribe
                        <- Request
    File_info ->
                        <- Yes
        File_piece ->
        ...
        Done ->
    File_info ->        
                        <- No
    ...
    Done ->

    *** TEXT COM ***
    A                   B
    Publish ->          (Subscribe)
    (Publish ->)        Subscribe
                        <- Request
    Text ->
    ...
    Done ->

TODO:
* Compression of files
* Encryption (tls or quic)
