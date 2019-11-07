package com.github.jmatss.send.protocol;

import com.github.jmatss.send.HashType;
import com.github.jmatss.send.MessageType;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a "protocol" file.
 */
public class PFile {
    public static final int BUFFER_SIZE = 1 << 16;
    private static final Logger LOGGER = Logger.getLogger(PFile.class.getName());

    private final String name;
    private final String path;
    private HashType fileHashType;
    private byte[] digest;

    PFile(String name, String path, HashType fileHashType) throws IOException, NoSuchAlgorithmException {
        if (!new File(path).exists())
            throw new FileNotFoundException("Unable to find file " + path);
        else if (fileHashType == HashType.NONE)
            throw new IllegalArgumentException("Not allowed to used no hash on the \"whole file\" hash");

        this.name = name;
        this.path = path;
        this.fileHashType = fileHashType;
        this.digest = calculateFileHash();
    }

    public HashType getFileHashType() {
        return this.fileHashType;
    }

    public void setFileHashType(HashType fileHashType) {
        this.fileHashType = fileHashType;
    }

    public String getPath() {
        return this.path;
    }

    public byte[] getDigest() throws IOException, NoSuchAlgorithmException {
        if (this.digest == null)
            this.digest = calculateFileHash();
        return this.digest.clone();
    }

    private byte[] calculateFileHash() throws NoSuchAlgorithmException, IOException {
        MessageDigest hash = MessageDigest.getInstance(this.fileHashType.toString());
        try (InputStream input = new FileInputStream(this.path)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int n;
            while ((n = input.read(buf)) != -1) {
                hash.update(Arrays.copyOfRange(buf, 0, n));
            }
        }
        return hash.digest();
    }

    /**
     * Gets the "file info" packet that is to be sent before sending the data packets from the "packetIterator".
     * Contains "meta-data" of the file to be sent.
     * Format:
     * {@code
     * MessageType (1 byte)
     * | NameLength (4 bytes)
     * | Name ("NameLength" bytes)
     * | TotalFileLength (8 bytes)
     * | HashType (1 byte)
     * | Hash-digest (of whole file) (x bytes)
     * }
     *
     * @return the "file info" packet.
     * @throws IOException              when it's unable to decode the name into bytes or if it's unable to open the
     *                                  file.
     * @throws NoSuchAlgorithmException if an incorrect hash type is used.
     */
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
                .put((byte) this.fileHashType.value())
                .put(digest);

        return packet.array();
    }


    /**
     * Iterator over the packets to be sent. Can be sent "raw" without any modification to the receiver.
     *
     * @param pieceSize the maximum amount of data from the file to be sent per packet.
     * @param pieceHashType  the hash algorithm used on the piece data. Can be HashType.NONE.
     * @return an iterator over the packets.
     * @throws FileNotFoundException if it can't open the file from the PFile.path.
     */
    public Iterable<byte[]> packetIterator(int pieceSize, HashType pieceHashType) throws FileNotFoundException {
        class PacketIterator implements Iterable<byte[]> {
            private int index;
            private final int pieceSize;
            private final InputStream input;
            private final long fileLength;
            private final HashType pieceHashType;

            PacketIterator(int pieceSize, HashType pieceHashType) throws FileNotFoundException {
                this.index = 0;
                this.pieceSize = pieceSize;

                File file = new File(PFile.this.path);
                this.input = new FileInputStream(file);
                this.fileLength = file.length();

                // This pieceHashType is for hashing pieces of the file
                // while the PFile HashType is for hashing of the whole file.
                // This pieceHashType can be HashType.NONE.
                this.pieceHashType = pieceHashType;
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
                                .put((byte) this.sup.pieceHashType.value());
                        if (digest != null)
                            packet.put(digest);

                        this.sup.index++;
                        return packet.array();
                    }

                    private byte[] getPieceDigest(byte[] piece) throws IOException, NoSuchAlgorithmException {
                        if (this.sup.pieceHashType == HashType.NONE)
                            return null;
                        return MessageDigest
                                .getInstance(this.sup.pieceHashType.toString())
                                .digest(piece);
                    }
                };
            }
        }

        return new PacketIterator(pieceSize, pieceHashType);
    }
}
