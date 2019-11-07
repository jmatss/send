package com.github.jmatss.send.protocol;

import com.github.jmatss.send.HashType;
import com.github.jmatss.send.MessageType;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

public class FileProtocol extends Protocol {
    private final MessageType messageType = MessageType.FILE;
    private PFile[] files;

    FileProtocol(PFile[] files) {
        if (files.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero files");
        this.files = files;
    }

    FileProtocol(String[] names, String[] paths, HashType fileHashType) throws IOException,
            NoSuchAlgorithmException {
        if (paths.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero paths");
        else if (names.length == 0)
            throw new IllegalArgumentException("Not allowed to create a FileProtocol with zero names");
        else if (paths.length != names.length)
            throw new IllegalArgumentException("Length of paths and names differ");

        PFile[] files = new PFile[paths.length];
        for (int i = 0; i < paths.length; i++)
            files[i] = new PFile(names[i], paths[i], fileHashType);

        this.files = files;
    }

    FileProtocol(String[] names, String[] paths) throws IOException, NoSuchAlgorithmException {
        this(names, paths, Protocol.DEFAULT_HASH_TYPE);
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
