package com.github.jmatss.send;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

public class FileProtocol extends Protocol {
    private final MessageType messageType = MessageType.FILE;
    private PFile[] files;
    private int pieceSize;
    private HashType hashType;

    FileProtocol(PFile[] files, int pieceSize, HashType hashType) {
        if (files.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero files");

        initFileProtocol(files, pieceSize, hashType);
    }

    FileProtocol(PFile[] files, int pieceSize) {
        this(files, pieceSize, Protocol.DEFAULT_HASH_TYPE);
    }

    FileProtocol(PFile[] files) {
        this(files, Protocol.DEFAULT_PIECE_SIZE, Protocol.DEFAULT_HASH_TYPE);
    }

    FileProtocol(String[] names, String[] paths, int pieceSize, HashType hashType) throws IOException, NoSuchAlgorithmException {
        if (paths.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero paths");
        else if (names.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero names");
        else if (paths.length != names.length)
            throw new IllegalArgumentException("Length of paths and names differ");

        PFile[] files = new PFile[paths.length];
        for (int i = 0; i < paths.length; i++)
            files[i] = new PFile(names[i], paths[i], hashType);

        initFileProtocol(files, pieceSize, hashType);
    }

    FileProtocol(String[] names, String[] paths, int pieceSize) throws IOException, NoSuchAlgorithmException {
        this(names, paths, pieceSize, HashType.NONE);
    }

    FileProtocol(String[] names, String[] paths) throws IOException, NoSuchAlgorithmException {
        this(names, paths, Protocol.DEFAULT_PIECE_SIZE, HashType.NONE);
    }

    private void initFileProtocol(PFile[] files, int pieceSize, HashType hashType) {
        if (pieceSize > Protocol.MAX_PIECE_SIZE)
            throw new IllegalArgumentException(String.format("pieceSize > Protocol.MAX_PIECE_SIZE: (%d > %d)",
                    pieceSize, Protocol.MAX_PIECE_SIZE));

        this.files = files;
        this.pieceSize = pieceSize;
        this.hashType = hashType;
    }

    @Override
    public MessageType getMessageType() {
        return this.messageType;
    }

    @Override
    public Iterable<PFile> iter() {
        return () -> new Iterator<PFile>() {
            int index = 0;
            FileProtocol sup = FileProtocol.this;

            @Override
            public boolean hasNext() {
                return this.index < this.sup.files.length;
            }

            @Override
            public PFile next() {
                return this.sup.files[index++];
            }
        };
    }

    public PFile[] getFiles() {
        return this.files;
    }
}
