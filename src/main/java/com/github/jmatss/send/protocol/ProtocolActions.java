package com.github.jmatss.send.protocol;

import com.github.jmatss.send.HashType;
import com.github.jmatss.send.MessageType;
import com.github.jmatss.send.exception.IncorrectHashTypeException;
import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.FileInfoPacket;
import com.github.jmatss.send.packet.PublishPacket;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class ProtocolActions {
    /**
     * Sees if the latest received packet is a "done" packet.
     * Also makes sure that the index received in the done packet is the same as the local index to make sure that
     * the clients haven't gone out of sync.
     *
     * @param in         the input stream of the socket.
     * @param localIndex the current local index (i.e. which piece it is currently about to received).
     * @return a boolean indicating if the packet is a done packet or not.
     * @throws IOException if it is unable to read from the input stream.
     */
    public static boolean isDonePacket(PushbackInputStream in, int localIndex) throws IOException {
        int messageType;
        if ((messageType = in.read()) == MessageType.DONE.value()) {
            byte[] doneIndexBuf = new byte[4];
            int n = in.read(doneIndexBuf);
            if (n != doneIndexBuf.length)
                throw new IOException("Unable to read all index bytes from the done packet.");
            int remoteIndex = ByteBuffer.allocate(4).put(doneIndexBuf).getInt();

            if (localIndex != remoteIndex) {
                throw new IOException("Index received from done packet is different from the local index." +
                        " local index: " + localIndex + ", remote done index: " + remoteIndex);
            }

            return true;
        } else {
            in.unread(messageType);
            return false;
        }
    }

    public static void sendDonePacket(OutputStream out, int index) throws IOException {
        out.write(ByteBuffer.allocate(4).putInt(index).array());
    }

    public static void sendRequestPacket(OutputStream out, PublishPacket pp) throws IOException {
        out.write(ByteBuffer
                .allocate(1 + 1 + pp.topicLength + 4)
                .put((byte) MessageType.REQUEST.value())
                .put(pp.topicLength)
                .put(pp.topic.getBytes(Protocol.ENCODING))
                .put(pp.id)
                .array());
    }

    /**
     * Received a file info packet.
     *
     * @param in the input stream of the socket.
     * @return a FileInfoPacket containing the data from the received packet.
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulates EOFExceptions thrown if the input stream reaches EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     */
    public static FileInfoPacket receiveFileInfo(InputStream in)
            throws IOException, IncorrectMessageTypeException, IncorrectHashTypeException {
        int n;
        byte[] buf;

        int messageType = in.read();
        if (messageType == -1)
            throw new EOFException("End of file reached while reading FileInfo message type.");
        else if (messageType != MessageType.FILE_INFO.value())
            throw new IncorrectMessageTypeException("Received incorrect message type while reading FileInfo packet.");

        // TODO: Make sure length isn't a weird size (ex. extremely large).
        buf = new byte[4];
        n = in.read(buf);
        if (n == -1)
            throw new EOFException("End of file reached while reading FileInfo name length.");
        int nameLength = ByteBuffer.allocate(4).put(buf).getInt();

        buf = new byte[nameLength];
        n = in.read(buf);
        if (n == -1)
            throw new EOFException("End of file reached while reading FileInfo name.");
        String name = new String(buf, Protocol.ENCODING);

        buf = new byte[8];
        n = in.read(buf);
        if (n == -1)
            throw new EOFException("End of file reached while reading FileInfo file length.");
        long fileLength = ByteBuffer.allocate(8).put(buf).getLong();

        int hashType = in.read();
        if (hashType == -1)
            throw new EOFException("End of file reached while reading FileInfo hash type.");

        int hashSize;
        if (hashType == HashType.SHA1.value())
            hashSize = HashType.SHA1.size();
        else if (hashType == HashType.MD5.value())
            hashSize = HashType.MD5.size();
        else
            throw new IncorrectHashTypeException("Received incorrect hash type while reading FileInfo packet.");

        buf = new byte[hashSize];
        n = in.read(buf);
        if (n == -1)
            throw new EOFException("End of file reached while reading FileInfo hash.");
        byte[] digest = buf;

        return new FileInfoPacket(nameLength, name, fileLength, hashType, digest);
    }

    /**
     * Received a file info packet.
     *
     * @param in the input stream of the socket.
     * @return a FileInfoPacket containing the data from the received packet.
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulates EOFExceptions thrown if the input stream reaches EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     */

    /**
     * Receives a file piece.
     *
     * @param in         the input stream of the socket.
     * @param pp         containing data from the previously received publish packet.
     * @param localIndex the current piece index.
     * @return
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulatesEOFExceptions thrown if the input stream reaches EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     * @throws NoSuchAlgorithmException      if a incorrect hash function is used.
     */
    public static byte[] receiveFilePiece(InputStream in, PublishPacket pp, int localIndex)
            throws IOException, IncorrectMessageTypeException, IncorrectHashTypeException, NoSuchAlgorithmException {
        int n;
        byte[] buf;

        int messageType = in.read();
        if (messageType == -1)
            throw new EOFException("End of file reached while reading FilePiece message type.");
        else if (messageType != MessageType.FILE_PIECE.value())
            throw new IncorrectMessageTypeException("Received incorrect message type while reading FilePiece packet.");

        buf = new byte[4];
        n = in.read(buf);
        if (n == -1)
            throw new EOFException("End of file reached while reading FilePiece index.");
        int remoteIndex = ByteBuffer.allocate(4).put(buf).getInt();
        if (localIndex != remoteIndex) {
            throw new IOException("Index received from file piece packet is different from the local index." +
                    " local index: " + localIndex + ", remote done index: " + remoteIndex);
        }

        // TODO: Make sure length isn't a weird size (ex. extremely large).
        buf = new byte[4];
        n = in.read(buf);
        if (n == -1)
            throw new EOFException("End of file reached while reading FilePiece piece length.");
        int pieceLength = ByteBuffer.allocate(4).put(buf).getInt();

        buf = new byte[pieceLength];
        n = in.read(buf);
        if (n == -1)
            throw new EOFException("End of file reached while reading FilePiece piece data.");
        byte[] pieceData = buf.clone();

        int hashType = in.read();
        if (hashType == -1)
            throw new EOFException("End of file reached while reading FilePiece hash type.");

        int hashLength;
        byte[] actualDigest, packetDigest;
        MessageDigest md;
        // If the hash type isn't none: calculate and compare digest (if the given hash type is valid).
        // Else: do nothing.
        if (hashType != HashType.NONE.value()) {
            if (hashType == HashType.SHA1.value()) {
                hashLength = HashType.SHA1.size();
                md = MessageDigest.getInstance(HashType.SHA1.toString());
            } else if (hashType == HashType.MD5.value()) {
                hashLength = HashType.MD5.size();
                md = MessageDigest.getInstance(HashType.MD5.toString());
            } else {
                throw new IncorrectHashTypeException("Received incorrect hash type while parsing file piece packet: " + hashType);
            }

            packetDigest = new byte[hashLength];
            n = in.read(buf);
            if (n == -1)
                throw new EOFException("End of file reached while reading FilePiece digest.");
            else if (n != hashLength)
                throw new IOException("Read to few bytes while reading digest." +
                        " Expected: " + hashLength + ", got:" + n);

            actualDigest = md.digest(pieceData);
            if (!Arrays.equals(actualDigest, packetDigest)) {
                throw new IOException("Received digest incorrect. " +
                        "Calculated digest of received piece data: " + Arrays.toString(actualDigest) +
                        ", digest received from remote: " + Arrays.toString(packetDigest));
            }
        }

        return pieceData;
    }
}
