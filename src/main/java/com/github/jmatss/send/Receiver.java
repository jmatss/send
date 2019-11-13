package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectHashTypeException;
import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.FileInfoPacket;
import com.github.jmatss.send.packet.PublishPacket;
import com.github.jmatss.send.protocol.Protocol;
import com.github.jmatss.send.protocol.ProtocolSocket;
import com.github.jmatss.send.type.MessageType;
import com.github.jmatss.send.util.ScheduledExecutorServiceSingleton;

import java.io.*;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Receiver {
    public static final int SOCKET_TIMEOUT = 5000; // ms
    private static final Logger LOGGER = Logger.getLogger(Receiver.class.getName());
    private static Receiver instance;

    private final ScheduledExecutorService executor;
    private final MulticastSocket multicastSocket;
    private final Set<String> subscribedTopics;
    private final Lock mutexSubscribedTopics;
    private String downloadPath;

    private Receiver(String downloadPath, MulticastSocket multicastSocket, Set<String> subscribedTopics,
                     Lock mutexSubscribedTopics) {
        this.downloadPath = downloadPath;
        this.executor = ScheduledExecutorServiceSingleton.getInstance();
        this.multicastSocket = multicastSocket;
        this.subscribedTopics = subscribedTopics;
        this.mutexSubscribedTopics = mutexSubscribedTopics;
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
                this.multicastSocket.receive(packet);

                if (packet.getData().length == 0)
                    throw new IOException("Received empty packet");
                else if (packet.getData()[packet.getOffset()] != MessageType.PUBLISH.value())
                    throw new IncorrectMessageTypeException("Received incorrect MessageType. " +
                            "Expected: " + MessageType.PUBLISH.value() +
                            ", got: " + packet.getData()[packet.getOffset()]);
                else if (packet.getLength() < Protocol.MIN_PUBLISH_PACKET_SIZE)
                    throw new IOException("Received to few byte: " + packet.getLength());

                this.executor.submit(() -> receive(packet));
            } catch (IOException | IncorrectMessageTypeException e) {
                LOGGER.log(Level.SEVERE, "Exception when receiving packet in Receiver: " + e.getMessage());
            } finally {
                // TODO: Fix a better way to exit the receiver
                if (this.multicastSocket.isClosed())
                    return;
            }
        }
    }

    private void receive(DatagramPacket packet) {
        byte[] content = Arrays.copyOfRange(packet.getData(), packet.getOffset(),
                packet.getOffset() + packet.getLength());

        ProtocolSocket pSocket = null;
        try {
            PublishPacket pp = new ProtocolSocket(new ByteArrayInputStream(content)).receivePublish();

            this.mutexSubscribedTopics.lock();
            try {
                if (!this.subscribedTopics.contains(pp.topic))
                    return;
            } finally {
                this.mutexSubscribedTopics.unlock();
            }


            pSocket = new ProtocolSocket(new Socket(packet.getAddress(), pp.port));
            pSocket.getSocket().setSoTimeout(SOCKET_TIMEOUT);

            pSocket.sendRequest(pp);
            if (pp.subMessageType == MessageType.FILE_PIECE.value())
                receiveFile(pSocket);
            else if (pp.subMessageType == MessageType.TEXT.value())
                receiveText(pSocket);
            else
                throw new RuntimeException("Incorrect subMessageType received: " + pp.subMessageType);

        } catch (IOException | RuntimeException | IncorrectHashTypeException
                | IncorrectMessageTypeException | NoSuchAlgorithmException e) {
            // TODO: Implement better exception handling.
            e.printStackTrace();
            LOGGER.log(Level.SEVERE, e.getMessage());

            throw new RuntimeException(e);
        } finally {
            try {
                if (pSocket != null)
                    pSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage());
            }
        }
    }

    private void receiveFile(ProtocolSocket pSocket)
            throws IOException, IncorrectHashTypeException, IncorrectMessageTypeException, NoSuchAlgorithmException {
        while (true) {
            if (pSocket.isDone())
                break;

            FileInfoPacket fileInfoPacket = pSocket.receiveFileInfo();

            // If the file already exists on this local host, don't download it again.
            // TODO: More checking, ex see if hash is the same; if not, download with another name.
            File file = new File(this.downloadPath + fileInfoPacket.name);
            if (!file.exists()) {
                pSocket.sendYes();

                try (OutputStream fileWriter = new FileOutputStream(file)) {
                    int index = 0;
                    while (!pSocket.isDone()) {
                        fileWriter.write(pSocket.receiveFilePiece(index));
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
                pSocket.sendNo();
            }
        }
    }

    // TODO: Make a local "out" where the received text is to be written.
    private void receiveText(ProtocolSocket pSocket) throws IOException, IncorrectMessageTypeException {
        int index = 0;
        while (!pSocket.isDone()) {
            String text = pSocket.receiveText(index);
            // FIXME: temp out
            System.out.print(text);
            index++;
        }
        System.out.println();
    }
}
