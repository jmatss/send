package com.github.jmatss.send.protocol;

import com.github.jmatss.send.type.HashType;
import com.github.jmatss.send.type.MessageType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FileProtocol extends Protocol {
    // TODO: dont use File_piece for this, change to something else.
    private final MessageType messageType = MessageType.FILE_PIECE;
    private List<PFile> files;

    public FileProtocol(List<PFile> files) {
        if (files.isEmpty())
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero files");
        this.files = files;
    }

    public FileProtocol(List<String> names, List<String> paths, HashType fileHashType, HashType pieceHashType,
                        int pieceSize) throws IOException {
        if (paths.isEmpty())
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero paths");
        else if (names.isEmpty())
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero names");
        else if (paths.size() != names.size())
            throw new IllegalArgumentException("Length of paths and names differ");

        List<PFile> files = new ArrayList<>(paths.size());
        for (int i = 0; i < paths.size(); i++)
            files.add(new PFile(names.get(i), paths.get(i), fileHashType, pieceHashType, pieceSize));

        this.files = files;
    }

    public FileProtocol(List<String> names, List<String> paths) throws IOException {
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
                return this.index < FileProtocol.this.files.size();
            }

            @Override
            public PFile next() {
                return FileProtocol.this.files.get(index++);
            }
        };
    }
}
