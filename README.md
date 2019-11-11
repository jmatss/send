# work in progress

Packet formats:

    if (MessageType::PUBLISH && (SubMessageType::TEXT || SubMessageType::FILE_PIECE)):
        MessageType (1 byte)
        | Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)       //
        | Port (4 bytes) (that this publisher listens of for TCP connection)
        | Random ID Number (4 bytes)    // unique

    if (MessageType::REQUEST):
        MessageType (1 byte)
        | Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | ID Number (4 bytes) (corresponding to the id in the PUBLISH message)

    if (MessageType::TEXT):
        MessageType (1 byte)
        | Index (4 bytes)
        | Length (4 bytes)
        | Text ("Length" bytes)

    if (MessageType::FILE_INFO):
        MessageType (1 byte)
        | NameLength (4 bytes)
        | Name ("NameLength" bytes)
        | TotalFileLength (8 bytes)
        | HashType (1 byte)
        | Hash-digest (of whole file) (x bytes)
        
    if (MessageType::FILE_PIECE):
        MessageType (1 byte)
        | Index (4 bytes)
        | Length (4 bytes)
        | PieceContent ("Length" bytes)
        | HashType (1 byte)
        | Hash-digest (of this piece) (x bytes) (can be zero bytes if HashType::None)
        
    if (MessageType::YES || MessageType::NO):
        MessageType (1 byte)
        
    // FIXME: weird to always send index with done
    if (MessageType::DONE):
        MessageType (1 byte)
        | Index (4 bytes)

Com formats:

    *** FILE COM ***
    A                   B
    Publish ->          (<- Subscribe)
    (Publish ->)        <- Subscribe
                        <- REQUEST
    File_info ->
                        <- YES
        FILE_PIECE ->
        ...
        DONE ->
    File_info ->        
                        <- NO
    ...
    DONE ->

        *** TEXT COM ***
    A                   B
    Publish ->          (<- Subscribe)
    (Publish ->)        <- Subscribe
                        <- REQUEST
    Text ->
    ...
    DONE ->

 TODO:
  compression of files
