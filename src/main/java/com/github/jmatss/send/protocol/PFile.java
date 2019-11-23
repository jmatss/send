package com.github.jmatss.send.protocol;

import com.github.jmatss.send.packet.FileInfoPacket;
import com.github.jmatss.send.packet.FilePiecePacket;
import com.github.jmatss.send.type.HashType;

import java.io.*;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a "protocol" file.
 */
public class PFile {
    private static final Logger LOGGER = Logger.getLogger(PFile.class.getName());
    public static final int BUFFER_SIZE = 1 << 16;

    // fileHashType can NOT be HashType.NONE while pieceHashType can.
    private HashType fileHashType;
    private HashType pieceHashType;

    private final String name;
    private final String path;
    private int pieceSize;

    PFile(String name, String path, HashType fileHashType, HashType pieceHashType, int pieceSize)
    throws IOException {
        if (!new File(path).exists())
            throw new FileNotFoundException("Unable to find file " + path);
        else if (fileHashType == HashType.NONE)
            throw new IllegalArgumentException("Not allowed to used no hash on the \"whole file\" hash");

        this.name = name;
        this.path = path;
        this.fileHashType = fileHashType;
        this.pieceHashType = pieceHashType;
        this.pieceSize = pieceSize;
    }

    public byte[] getFileDigest() throws IOException {
        return calculateFileHash();
    }

    private byte[] calculateFileHash() throws IOException {
        MessageDigest md = this.fileHashType.getMessageDigest();
        try (InputStream input = new FileInputStream(this.path)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = input.read(buf)) != -1) {
                md.update(Arrays.copyOfRange(buf, 0, n));
            }
        }
        return md.digest();
    }

    public FileInfoPacket getFileInfoPacket() throws IOException {
        return new FileInfoPacket(this.name, new File(this.path).length(), this.fileHashType, getFileDigest());
    }

    public Iterable<FilePiecePacket> packetIterator() throws FileNotFoundException {
        class PacketIterator implements Iterable<FilePiecePacket> {
            private int index;
            private final InputStream input;
            private final long fileLength;

            PacketIterator() throws FileNotFoundException {
                this.index = 0;

                File file = new File(PFile.this.path);
                this.input = new FileInputStream(file);
                this.fileLength = file.length();
            }

            @Override
            public Iterator<FilePiecePacket> iterator() {
                return new Iterator<FilePiecePacket>() {

                    @Override
                    public boolean hasNext() {
                        boolean result =
                                PacketIterator.this.fileLength > PacketIterator.this.index * PFile.this.pieceSize;
                        if (!result) {
                            try {
                                PacketIterator.this.input.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        return result;
                    }

                    @Override
                    public FilePiecePacket next() {
                        long remainingSize =
                                PacketIterator.this.fileLength - PacketIterator.this.index * PFile.this.pieceSize;
                        int minPieceSize = (int) Math.min(remainingSize, PFile.this.pieceSize);

                        byte[] content = new byte[minPieceSize];
                        try {
                            if (PacketIterator.this.input.read(content) != minPieceSize)
                                throw new IOException("Incorrect amount of bytes read from file");
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, e.getMessage());
                            return null;
                        }

                        return new FilePiecePacket(
                                PacketIterator.this.index++,
                                content,
                                PFile.this.pieceHashType
                        );
                    }
                };
            }
        }

        return new PacketIterator();
    }
}
