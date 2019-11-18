package com.github.jmatss.send.protocol;

import com.github.jmatss.send.type.HashType;
import com.github.jmatss.send.type.MessageType;
import com.github.jmatss.send.exception.IncorrectHashTypeException;
import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.FileInfoPacket;
import com.github.jmatss.send.packet.PublishPacket;
import com.github.jmatss.send.packet.RequestPacket;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class SocketWrapper {
    private final Socket socket;
    private final PushbackInputStream in;
    private final OutputStream out;

    private SocketWrapper(Socket socket, InputStream in, OutputStream out) {
        this.socket = socket;
        this.in = new PushbackInputStream(in);
        this.out = out;
    }

    public SocketWrapper(Socket socket) throws IOException {
        this(socket, socket.getInputStream(), socket.getOutputStream());
    }

    public SocketWrapper(InputStream in) {
        this(null, in, null);
    }

    public SocketWrapper(OutputStream out) {
        this(null, null, out);
    }

    public void close() throws IOException {
        if (this.in != null)
            this.in.close();
        if (this.out != null)
            this.out.close();
        if (this.socket != null)
            this.socket.close();
    }

    public Socket getSocket() {
        return this.socket;
    }

    public InputStream getInputStream() {
        return this.in;
    }

    public OutputStream getOutputStream() {
        return this.out;
    }

    /**
     * Sees if the latest received packet is a "done" packet.
     * Also makes sure that the index received in the done packet is the same as the local index to make sure that
     * the clients haven't gone out of sync.
     *
     * @return a boolean indicating if the packet is a done packet or not.
     * @throws IOException if it is unable to read from the input stream.
     */
    public boolean isDone() throws IOException {
        nullGuard(this.in);
        // Read and remove the first byte from the input stream.
        // If it isn't a DONE message, put it back into the stream.
        int messageType = this.in.read();
        if (messageType == MessageType.DONE.value()) {
            return true;
        } else {
            this.in.unread(messageType);
            return false;
        }
    }

    // Removes the first byte
    // TODO: Maybe return more information so that the caller can see which message type was received.
    private boolean isMessageType(MessageType localMessageType) throws IOException {
        return readByte() == localMessageType.value();
    }

    // TODO: Some sort of check that it is either a yes or no packet, throw exception otherwise
    public boolean isYes() throws IOException {
        return isMessageType(MessageType.YES);
    }

    public boolean isNo() throws IOException {
        return isMessageType(MessageType.NO);
    }

    // TODO: Make these "send...Packet" functions uniform. Ex. by always giving them some sort
    //  of "packet-class" instead of the current different second arguments.
    public void sendDone() throws IOException {
        nullGuard(this.out);
        this.out.write((byte) MessageType.DONE.value());
    }

    public void sendRequest(PublishPacket pp) throws IOException {
        nullGuard(this.out);
        this.out.write(ByteBuffer
                .allocate(1 + 1 + pp.topicLength + 4)
                .put((byte) MessageType.REQUEST.value())
                .put((byte) pp.topicLength)
                .put(pp.topic.getBytes(Protocol.ENCODING))
                .put(pp.id)
                .array());
    }

    public void sendPacket(byte... packet) throws IOException {
        nullGuard(this.out);
        this.out.write(packet);
    }

    public void sendText(byte[] textPacket) throws IOException {
        sendPacket(textPacket);
    }

    public void sendFileInfo(byte[] fileInfo) throws IOException {
        sendPacket(fileInfo);
    }

    public void sendFilePiece(byte[] filePiece) throws IOException {
        sendPacket(filePiece);
    }

    public void sendYes() throws IOException {
        sendPacket((byte) MessageType.YES.value());
    }

    public void sendNo() throws IOException {
        sendPacket((byte) MessageType.NO.value());
    }

    public String receiveText(int localIndex)
            throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.TEXT;
        if (!isMessageType(messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int remoteIndex = readInt();
        if (localIndex != remoteIndex)
            throw new IOException("Index received from remote packet is different from the local index." +
                    " Local index: " + localIndex + ", remote index: " + remoteIndex);

        int textLength = readInt();
        if (textLength > Protocol.MAX_PIECE_SIZE)
            throw new IOException("textLength > Protocol.MAX_PIECE_SIZE (" +
                    textLength + " > " + Protocol.MAX_PIECE_SIZE);

        return new String(readN(textLength), Protocol.ENCODING);
    }

    public RequestPacket receiveRequest()
            throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.REQUEST;
        if (!isMessageType(messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int topicLength = readByte();
        String topic = new String(readN(topicLength), Protocol.ENCODING);
        byte[] id = readN(4);

        return new RequestPacket(topicLength, topic, id);
    }

    /**
     * Received a file info packet.
     *
     * @return a FileInfoPacket containing the data from the received packet.
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulates EOFExceptions thrown if the input stream
     *                                       reaches EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     */
    public FileInfoPacket receiveFileInfo()
            throws IOException, IncorrectMessageTypeException, IncorrectHashTypeException {
        MessageType messageType = MessageType.FILE_INFO;
        if (!isMessageType(messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        // TODO: Make sure length isn't a weird size (ex. extremely large).
        int nameLength = readInt();
        String name = new String(readN(nameLength), Protocol.ENCODING);
        long fileLength = readLong();
        int hashType = readByte();
        byte[] digest;

        int hashSize;
        if (hashType == HashType.SHA1.value())
            hashSize = HashType.SHA1.size();
        else if (hashType == HashType.MD5.value())
            hashSize = HashType.MD5.size();
        else
            throw new IncorrectHashTypeException("Received incorrect hash type while reading FileInfo packet.");

        digest = readN(hashSize);

        return new FileInfoPacket(nameLength, name, fileLength, hashType, digest);
    }

    public PublishPacket receivePublish()
            throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.PUBLISH;
        if (!isMessageType(messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int topicLength = readByte();
        String topic = new String(readN(topicLength), Protocol.ENCODING);
        int subMessageType = readByte();
        int port = readInt();
        byte[] id = readN(4);

        return new PublishPacket(subMessageType, topicLength, topic, port, id);
    }

    /**
     * Receives a file piece.
     *
     * @param localIndex the current piece index.
     * @return the piece data.
     * @throws IOException                   if it is unable to read from the input stream.
     *                                       It also encapsulatesEOFExceptions thrown if the input stream reaches
     *                                       EOF.
     * @throws IncorrectMessageTypeException if the file info packet contains an invalid MessageType.
     * @throws IncorrectHashTypeException    if the file info packet contains an invalid HashType.
     * @throws NoSuchAlgorithmException      if a incorrect hash function is used.
     */
    public byte[] receiveFilePiece(int localIndex)
            throws IOException, IncorrectMessageTypeException, IncorrectHashTypeException, NoSuchAlgorithmException {
        MessageType messageType = MessageType.FILE_PIECE;
        if (!isMessageType(messageType))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int remoteIndex = readInt();
        if (localIndex != remoteIndex)
            throw new IOException("Index received from remote packet is different from the local index." +
                    " local index: " + localIndex + ", remote done index: " + remoteIndex);

        int pieceLength = readInt();
        byte[] pieceData = readN(pieceLength);
        int hashType = readByte();

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

            byte[] packetDigest = readN(hashLength);
            byte[] actualDigest = md.digest(pieceData);
            if (!Arrays.equals(actualDigest, packetDigest))
                throw new IOException("Received packet digest is incorrect. " +
                        "Calculated digest of received piece data: " + Arrays.toString(actualDigest) +
                        ", digest received from remote: " + Arrays.toString(packetDigest));
        }

        return pieceData;
    }

    private byte[] readN(int n) throws IOException {
        nullGuard(this.in);
        byte[] buf = new byte[n];
        int readBytes = this.in.read(buf);
        if (readBytes == -1)
            throw new EOFException("End of file reached while reading bytes from the input stream.");
        else if (n != readBytes)
            throw new IOException("Unable to read all bytes from input stream. " +
                    "Expected: " + n + " bytes, got: " + readBytes + " bytes.");
        return buf;
    }

    private byte readByte() throws IOException {
        nullGuard(this.in);
        byte res = (byte) this.in.read();
        if (res == -1)
            throw new EOFException("End of file reached while reading one byte.");
        return res;
    }

    private int readInt() throws IOException {
        byte[] buf = readN(4);
        return ByteBuffer.allocate(4).put(buf).getInt(0);
    }

    private long readLong() throws IOException {
        byte[] buf = readN(8);
        return ByteBuffer.allocate(8).put(buf).getLong(0);
    }

    private void nullGuard(Object... os) throws IOException {
        for (Object o : os) {
            if (o == null)
                throw new IOException("The given stream is null.");
        }
    }
}
