package com.github.jmatss.send.protocol;

import com.github.jmatss.send.type.HashType;
import com.github.jmatss.send.type.MessageType;

import java.io.IOException;
import java.util.Iterator;

public class FileProtocol extends Protocol {
    // TODO: dont use File_piece for this, change to something else.
    private final MessageType messageType = MessageType.FILE_PIECE;
    private PFile[] files;

    public FileProtocol(PFile[] files) {
        if (files.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero files");
        this.files = files;
    }

    public FileProtocol(String[] names, String[] paths, HashType fileHashType, HashType pieceHashType, int pieceSize) throws IOException {
        if (paths.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero paths");
        else if (names.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero names");
        else if (paths.length != names.length)
            throw new IllegalArgumentException("Length of paths and names differ");

        PFile[] files = new PFile[paths.length];
        for (int i = 0; i < paths.length; i++)
            files[i] = new PFile(names[i], paths[i], fileHashType, pieceHashType, pieceSize);

        this.files = files;
    }

    public FileProtocol(String[] names, String[] paths) throws IOException {
        this(names, paths, Protocol.DEFAULT_HASH_TYPE, Protocol.DEFAULT_HASH_TYPE, Protocol.DEFAULT_PIECE_SIZE);
    }

    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }

    /**
     * @return an iterator over all packets to send the text.
     */
    @Override
    public Iterable<PFile> iter() {
        return () -> new Iterator<PFile>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return this.index < FileProtocol.this.files.length;
            }

            @Override
            public PFile next() {
                return FileProtocol.this.files[index++];
            }
        };
    }

    public PFile[] getFiles() {
        return this.files;
    }
}
