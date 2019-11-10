package com.github.jmatss.send.protocol;

import com.github.jmatss.send.HashType;
import com.github.jmatss.send.MessageType;
import com.github.jmatss.send.exception.IncorrectHashTypeException;
import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.FileInfoPacket;
import com.github.jmatss.send.packet.PublishPacket;
import com.github.jmatss.send.packet.RequestPacket;

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
    public static boolean isDone(PushbackInputStream in, int localIndex) throws IOException {
        int messageType;
        if ((messageType = in.read()) == MessageType.DONE.value()) {
            int remoteIndex = readInt(in);
            if (localIndex != remoteIndex)
                throw new IOException("Index received from done packet is different from the local index." +
                        " Local index: " + localIndex + ", remote index: " + remoteIndex);
            return true;
        } else {
            in.unread(messageType);
            return false;
        }
    }

    // Removes the first byte
    // TODO: Maybe return more information so that the caller can see which message type was received.
    private static boolean isMessageType(InputStream in, MessageType localMessageType) throws IOException {
        return readByte(in) == localMessageType.value();
    }

    // TODO: Some sort of check that it is either a yes or no packet, throw exception otherwise
    public static boolean isYes(InputStream in) throws IOException {
        return isMessageType(in, MessageType.YES);
    }

    public static boolean isNo(InputStream in, int localIndex) throws IOException {
        return isMessageType(in, MessageType.NO);
    }

    // TODO: Make these "send...Packet" functions uniform. Ex. by always giving them some sort
    //  of "packet-class" instead of the current different second arguments.
    public static void sendDone(OutputStream out, int index) throws IOException {
        out.write(ByteBuffer.allocate(5)
                .put((byte) MessageType.DONE.value())
                .putInt(index)
                .array());
    }

    public static void sendRequest(OutputStream out, PublishPacket pp) throws IOException {
        out.write(ByteBuffer
                .allocate(1 + 1 + pp.topicLength + 4)
                .put((byte) MessageType.REQUEST.value())
                .put((byte) pp.topicLength)
                .put(pp.topic.getBytes(Protocol.ENCODING))
                .put(pp.id)
                .array());
    }

    public static void sendPacket(OutputStream out, byte... packet) throws IOException {
        out.write(packet);
    }

    public static void sendText(OutputStream out, byte[] textPacket) throws IOException {
        sendPacket(out, textPacket);
    }

    public static void sendFileInfo(OutputStream out, byte[] fileInfo) throws IOException {
        sendPacket(out, fileInfo);
    }

    public static void sendFilePiece(OutputStream out, byte[] filePiece) throws IOException {
        sendPacket(out, filePiece);
    }

    public static void sendYes(OutputStream out) throws IOException {
        sendPacket(out, (byte) MessageType.YES.value());
    }

    public static void sendNo(OutputStream out) throws IOException {
        sendPacket(out, (byte) MessageType.NO.value());
    }

    public static String receiveText(InputStream in, int localIndex)
            throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.TEXT;
        if (!isMessageType(in, messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int remoteIndex = readInt(in);
        if (localIndex != remoteIndex)
            throw new IOException("Index received from remote packet is different from the local index." +
                    " Local index: " + localIndex + ", remote index: " + remoteIndex);

        // TODO: Make sure length isn't a weird size (ex. extremely large).
        int textLength = readInt(in);
        return new String(readN(in, textLength), Protocol.ENCODING);
    }

    public static RequestPacket receiveRequest(InputStream in)
            throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.REQUEST;
        if (!isMessageType(in, messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int topicLength = readByte(in);
        String topic = new String(readN(in, topicLength), Protocol.ENCODING);
        byte[] id = readN(in, 4);

        return new RequestPacket(topicLength, topic, id);
    }

    /**
     * Received a file info packet.
     *
     * @param in the input stream of the socket.
     * @return a FileInfoPacket containing the data from the received packet.
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulates EOFExceptions thrown if the input stream
     *                                       reaches EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     */
    public static FileInfoPacket receiveFileInfo(InputStream in)
            throws IOException, IncorrectMessageTypeException, IncorrectHashTypeException {
        MessageType messageType = MessageType.FILE_INFO;
        if (!isMessageType(in, messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        // TODO: Make sure length isn't a weird size (ex. extremely large).
        int nameLength = readInt(in);
        String name = new String(readN(in, nameLength), Protocol.ENCODING);
        long fileLength = readLong(in);
        int hashType = readByte(in);
        byte[] digest;

        int hashSize;
        if (hashType == HashType.SHA1.value())
            hashSize = HashType.SHA1.size();
        else if (hashType == HashType.MD5.value())
            hashSize = HashType.MD5.size();
        else
            throw new IncorrectHashTypeException("Received incorrect hash type while reading FileInfo packet.");

        digest = readN(in, hashSize);

        return new FileInfoPacket(nameLength, name, fileLength, hashType, digest);
    }

    public static PublishPacket receivePublish(InputStream in)
            throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.PUBLISH;
        if (!isMessageType(in, messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int topicLength = readByte(in);
        String topic = new String(readN(in, topicLength), Protocol.ENCODING);
        int subMessageType = readByte(in);
        int port = readInt(in);
        byte[] id = readN(in, 4);

        return new PublishPacket(subMessageType, topicLength, topic, port, id);
    }

    /**
     * Receives a file piece.
     *
     * @param in         the input stream of the socket.
     * @param localIndex the current piece index.
     * @return the piece data.
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulatesEOFExceptions thrown if the input stream reaches
     *                                       EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     * @throws NoSuchAlgorithmException      if a incorrect hash function is used.
     */
    public static byte[] receiveFilePiece(InputStream in, int localIndex)
            throws IOException, IncorrectMessageTypeException, IncorrectHashTypeException, NoSuchAlgorithmException {
        MessageType messageType = MessageType.FILE_PIECE;
        if (!isMessageType(in, messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int remoteIndex = readInt(in);
        if (localIndex != remoteIndex)
            throw new IOException("Index received from remote packet is different from the local index." +
                    " local index: " + localIndex + ", remote done index: " + remoteIndex);

        int pieceLength = readInt(in);
        byte[] pieceData = readN(in, pieceLength);
        int hashType = readByte(in);

        int hashLength;
        MessageDigest md;
        // If the hash type isn't none: calculate and compare digest (if the given hash type is valid).
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

            byte[] packetDigest = readN(in, hashLength);
            byte[] actualDigest = md.digest(pieceData);
            if (!Arrays.equals(actualDigest, packetDigest))
                throw new IOException("Received packet digest is incorrect. " +
                        "Calculated digest of received piece data: " + Arrays.toString(actualDigest) +
                        ", digest received from remote: " + Arrays.toString(packetDigest));
        }

        return pieceData;
    }

    private static byte[] readN(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int readBytes = in.read(buf);
        if (readBytes == -1)
            throw new EOFException("End of file reached while reading bytes from the input stream.");
        else if (n != readBytes)
            throw new IOException("Unable to read all bytes from input stream. " +
                    "Expected: " + n + " bytes, got: " + readBytes + " bytes.");
        return buf;
    }

    private static byte readByte(InputStream in) throws IOException {
        byte res = (byte) in.read();
        if (res == -1)
            throw new EOFException("End of file reached while reading one byte.");
        return res;
    }

    private static int readInt(InputStream in) throws IOException {
        byte[] buf = readN(in, 4);
        return ByteBuffer.allocate(4).put(buf).getInt();
    }

    private static long readLong(InputStream in) throws IOException {
        byte[] buf = readN(in, 8);
        return ByteBuffer.allocate(8).put(buf).getLong();
    }
}
