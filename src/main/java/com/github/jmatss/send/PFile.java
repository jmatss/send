package com.github.jmatss.send;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PFile {
    public static final int BUFFER_SIZE = 1 << 16;
    private static final Logger LOGGER = Logger.getLogger(PFile.class.getName());

    private final String name;
    private final String path;
    private HashType hashType;
    private byte[] digest;

    PFile(String name, String path, HashType hashType) throws IOException, NoSuchAlgorithmException {
        this.name = name;
        this.path = path;
        this.hashType = hashType;
        this.digest = calculateFileHash();
    }

    PFile(String path, String name) {
        this.name = name;
        this.path = path;
        this.hashType = HashType.NONE;
    }

    public HashType getHashType() {
        return this.hashType;
    }

    public void setHashType(HashType hashType) {
        this.hashType = hashType;
    }

    public String getPath() {
        return this.path;
    }

    public byte[] getDigest() throws IOException, NoSuchAlgorithmException {
        if (this.hashType == HashType.NONE)
            return null;
        else if (this.digest == null)
            this.digest = calculateFileHash();

        return this.digest.clone();
    }

    private byte[] calculateFileHash() throws NoSuchAlgorithmException, IOException {
        if (this.hashType == HashType.NONE)
            return null;

        MessageDigest hash = MessageDigest.getInstance(this.hashType.toString());
        try (InputStream input = new FileInputStream(this.path)) {
            byte[] buf = new byte[BUFFER_SIZE];
            while (input.read(buf) != -1) {
                hash.update(buf);
            }
        }

        return hash.digest();
    }

    public byte[] getFileInfo() throws IOException, NoSuchAlgorithmException {
        byte[] digest = this.getDigest();
        int hashLength = (digest != null) ? digest.length : 0;
        File file = new File(this.path);

        ByteBuffer packet = ByteBuffer
                .allocate(1 + 4 + this.name.length() + 1 + hashLength + 8)
                .put((byte) MessageType.FILE_INFO.value())
                .putInt(this.name.length())
                .put(this.name.getBytes(Protocol.ENCODING))
                .putLong(file.length())
                .put((byte) this.hashType.value());
        if (digest != null)
            packet.put(digest);

        return packet.array();
    }


    public Iterable<byte[]> packetIterator(int pieceSize) throws FileNotFoundException {
        class PacketIterator implements Iterable<byte[]> {
            private int index;
            private final int pieceSize;
            private final InputStream input;
            private final long fileLength;
            private final HashType hashType;

            PacketIterator(int pieceSize) throws FileNotFoundException {
                this.index = 0;
                this.pieceSize = pieceSize;

                File file = new File(PFile.this.path);
                this.input = new FileInputStream(file);
                this.fileLength = file.length();

                this.hashType = PFile.this.hashType;
            }

            @Override
            public Iterator<byte[]> iterator() {
                return new Iterator<byte[]>() {
                    PacketIterator sup = PacketIterator.this;

                    @Override
                    public boolean hasNext() {
                        boolean result = this.sup.fileLength > this.sup.index * this.sup.pieceSize;
                        if (!result) {
                            try {
                                this.sup.input.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        return result;
                    }

                    @Override
                    public byte[] next() {
                        long remainingSize = this.sup.fileLength - this.sup.index * this.sup.pieceSize;
                        int minPieceSize = (int) Math.min(remainingSize, this.sup.pieceSize);

                        byte[] content = new byte[minPieceSize];
                        byte[] digest;
                        try {
                            if (this.sup.input.read(content) != minPieceSize)
                                throw new IOException("Incorrect amount of bytes read from file");
                            digest = getPieceDigest(content);
                        } catch (IOException | NoSuchAlgorithmException e) {
                            LOGGER.log(Level.SEVERE, e.getMessage());
                            e.printStackTrace();
                            return null;
                        }

                        int hashLength = (digest != null) ? digest.length : 0;

                        ByteBuffer packet = ByteBuffer
                                .allocate(1 + 4 + 4 + minPieceSize + 1 + hashLength)
                                .put((byte) MessageType.FILE.value())
                                .putInt(this.sup.index)
                                .putInt(minPieceSize)
                                .put(content)
                                .put((byte) this.sup.hashType.value());
                        if (digest != null)
                            packet.put(digest);

                        this.sup.index++;
                        return packet.array();
                    }

                    private byte[] getPieceDigest(byte[] piece) throws IOException, NoSuchAlgorithmException {
                        if (this.sup.hashType == HashType.NONE || piece.length == 0)
                            return null;

                        return MessageDigest
                                .getInstance(this.sup.hashType.toString())
                                .digest(piece);
                    }
                };
            }
        }

        return new PacketIterator(pieceSize);
    }
}
