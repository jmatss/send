package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectHashTypeException;
import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.FileInfoPacket;
import com.github.jmatss.send.packet.PublishPacket;
import com.github.jmatss.send.protocol.Protocol;
import com.github.jmatss.send.protocol.ProtocolActions;

import java.io.*;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.jmatss.send.protocol.ProtocolActions.*;

public class Receiver {
    public static final int SOCKET_TIMEOUT = 5000; // ms
    private static final Logger LOGGER = Logger.getLogger(Receiver.class.getName());
    private static Receiver instance;

    private final ScheduledExecutorService executor;
    private final MulticastSocket socket;
    private final Set<String> subscribedTopics;
    private final Lock mutex;
    private String downloadPath;

    private Receiver(String downloadPath, MulticastSocket socket, Set<String> subscribedTopics, Lock mutex) {
        this.downloadPath = downloadPath;
        this.executor = ScheduledExecutorServiceSingleton.getInstance();
        this.socket = socket;
        this.subscribedTopics = subscribedTopics;
        this.mutex = mutex;
    }

    public static Receiver getInstance(String downloadPath, MulticastSocket socket, Set<String> subscribedTopics,
                                       Lock mutex) {
        if (Receiver.instance == null)
            Receiver.instance = new Receiver(downloadPath, socket, subscribedTopics, mutex);
        return Receiver.instance;
    }

    public void start() {
        byte[] buffer = new byte[Protocol.MAX_PUBLISH_PACKET_SIZE];
        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                this.socket.receive(packet);

                int packetMessageType = packet.getData()[packet.getOffset()];
                if (packet.getLength() < Protocol.MIN_PUBLISH_PACKET_SIZE)
                    throw new IOException("Received to few byte: " + packet.getLength());
                else if (packetMessageType != MessageType.PUBLISH.value())
                    throw new IOException("Received incorrect MessageType. Expected: "
                            + MessageType.PUBLISH.value() + ", got: " + packetMessageType);

                this.executor.submit(() -> receive(packet));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Exception when receiving packet in Receiver: " + e.getMessage());
            }
        }
    }

    private void receive(DatagramPacket packet) {
        byte[] content = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());
        byte topicLength = content[1];
        String topic;
        int subMessageType = content[2 + topicLength];
        int port = ByteBuffer
                .wrap(content, topicLength + 3, 4)
                .getInt();
        byte[] id = Arrays.copyOfRange(content, topicLength + 7, topicLength + 11);

        try {
            topic = new String(content, 2, topicLength, Protocol.ENCODING);
            Socket socket = new Socket(packet.getAddress(), port);

            PublishPacket publishPacket = new PublishPacket(subMessageType, topicLength, topic, port, id);
            socket.setSoTimeout(SOCKET_TIMEOUT);
            OutputStream out = new DataOutputStream(socket.getOutputStream());
            PushbackInputStream in = new PushbackInputStream(socket.getInputStream());

            sendRequest(out, publishPacket);

            if (subMessageType == MessageType.FILE_PIECE.value())
                receiveFile(in, out);
            else if (subMessageType == MessageType.TEXT.value())
                receiveText(in);
            else
                throw new RuntimeException("Incorrect subMessageType received: " + subMessageType);

        } catch (IOException | RuntimeException | IncorrectHashTypeException | IncorrectMessageTypeException | NoSuchAlgorithmException e) {
            // TODO: Implement better exception handling.
            LOGGER.log(Level.SEVERE, e.getMessage());
        } finally {
            socket.close();
        }
    }

    private void receiveFile(PushbackInputStream in, OutputStream out)
            throws IOException, IncorrectHashTypeException, IncorrectMessageTypeException, NoSuchAlgorithmException {
        // FIXME: Temporary max iteration.
        final int MAX_ITERATIONS = 1 << 16;
        int iterations = 0;
        while (true) {
            if (isDone(in, 0))
                break;
            else if (iterations >= MAX_ITERATIONS)
                break;

            FileInfoPacket fileInfoPacket = receiveFileInfo(in);

            // If the file already exists on this local host, don't download it again.
            // TODO: More checking, ex see if hash is the same; if not, download with another name.
            File file = new File(this.downloadPath + fileInfoPacket.name);
            if (!file.exists()) {
                sendYes(out);

                try (OutputStream fileWriter = new FileOutputStream(file)) {
                    int index = 0;
                    while (!isDone(in, index)) {
                        fileWriter.write(receiveFilePiece(in, index));
                        index++;
                    }

                    if (file.length() != fileInfoPacket.fileLength) {
                        // TODO: return custom error(?)
                        LOGGER.log(Level.SEVERE, "Unable to download while file " + fileInfoPacket.name +
                                ". Expected: " + fileInfoPacket.fileLength + " bytes, " +
                                "got: " + file.length() + " bytes");
                    }
                }
            } else {
                sendNo(out);
            }

            iterations++;
        }
    }

    // TODO: Make an local "out" where the received text is to be written.
    private void receiveText(PushbackInputStream in) throws IOException, IncorrectMessageTypeException {
        int index = 0;
        while (!isDone(in, 0)) {
            String text = ProtocolActions.receiveText(in, index);
            // FIXME: temp out
            System.out.print(text);
            index++;
        }
        System.out.println();
    }
}
