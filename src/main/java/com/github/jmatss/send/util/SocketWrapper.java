package com.github.jmatss.send.util;

import com.github.jmatss.send.Controller;
import com.github.jmatss.send.protocol.Protocol;
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
        if (this.in != null) this.in.close();
        if (this.out != null) this.out.close();
        if (this.socket != null) this.socket.close();
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

    private boolean isByte(byte b) throws IOException {
        return readByte() == b;
    }

    private boolean isByte(int b) throws IOException {
        return readByte() == b;
    }

    private boolean isByteUnreadIfIncorrect(byte b) throws IOException {
        // Read and remove the first byte from the input stream.
        // If the read byte isn't equal "b", put it back into the stream.
        byte rb = readByte();
        if (rb == b) {
            return true;
        } else {
            this.in.unread(rb);
            return false;
        }
    }

    public boolean isDone() throws IOException {
        return isByteUnreadIfIncorrect((byte) MessageType.DONE.value());
    }

    // TODO: Some sort of check that it is either a yes or no packet, throw exception otherwise
    public boolean isYes() throws IOException {
        return isByte(MessageType.YES.value());
    }

    public boolean isNo() throws IOException {
        return isByte(MessageType.NO.value());
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
                .put(pp.topic.getBytes(Controller.ENCODING))
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
        if (!isByte(messageType.value()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int remoteIndex = readInt();
        if (localIndex != remoteIndex)
            throw new IOException("Index received from remote packet is different from the local index." +
                    " Local index: " + localIndex + ", remote index: " + remoteIndex);

        int textLength = readInt();
        if (textLength > Protocol.MAX_PIECE_SIZE)
            throw new IOException("textLength > Protocol.MAX_PIECE_SIZE (" +
                    textLength + " > " + Protocol.MAX_PIECE_SIZE);

        return new String(readN(textLength), Controller.ENCODING);
    }

    public RequestPacket receiveRequest()
            throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.REQUEST;
        if (!isByte(messageType.value()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int topicLength = readByte();
        String topic = new String(readN(topicLength), Controller.ENCODING);
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
        if (!isByte(messageType.value()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        // TODO: Make sure length isn't a weird size (ex. extremely large).
        int nameLength = readInt();
        String name = new String(readN(nameLength), Controller.ENCODING);
        long fileLength = readLong();
        int hashType = readByte();
        byte[] digest = readN(HashType.getSize(hashType));

        return new FileInfoPacket(nameLength, name, fileLength, hashType, digest);
    }

    public PublishPacket receivePublish()
            throws IOException, IncorrectMessageTypeException {
        MessageType messageType = MessageType.PUBLISH;
        if (!isByte(messageType.value()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int topicLength = readByte();
        String topic = new String(readN(topicLength), Controller.ENCODING);
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
        if (!isByte(messageType.value()))
            throw new IncorrectMessageTypeException("Received incorrect message type");

        int remoteIndex = readInt();
        if (localIndex != remoteIndex)
            throw new IOException("Index received from remote packet is different from the local index." +
                    " local index: " + localIndex + ", remote done index: " + remoteIndex);

        int pieceLength = readInt();
        byte[] pieceData = readN(pieceLength);
        int hashType = readByte();

        // A HashType.NONE returns a null md.
        MessageDigest md = HashType.getMessageDigest(hashType);
        if (md != null) {
            byte[] packetDigest = readN(HashType.getSize(hashType));
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
