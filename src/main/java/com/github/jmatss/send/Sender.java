package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.RequestPacket;
import com.github.jmatss.send.protocol.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Sender {
    private static final Logger LOGGER = Logger.getLogger(Sender.class.getName());
    private static Sender instance;

    private final ScheduledExecutorService executor;
    private final Map<String, List<Future<?>>> publishedTopics;
    private final Lock mutexPublishedTopics;

    private Sender(Map<String, List<Future<?>>> publishedTopics, Lock mutexPublishedTopics) {
        this.executor = ScheduledExecutorServiceSingleton.getInstance();
        this.publishedTopics = publishedTopics;
        this.mutexPublishedTopics = mutexPublishedTopics;
    }

    public static Sender getInstance(Map<String, List<Future<?>>> publishedTopics, Lock mutexPublishedTopics) {
        if (Sender.instance == null)
            Sender.instance = new Sender(publishedTopics, mutexPublishedTopics);
        return Sender.instance;
    }

    // TODO: fix so that the "send" function sends all packets before exiting if the task gets a cancel.
    public void listen(ServerSocket serverSocket, Protocol protocol) throws IOException {
        while (true) {
            Socket clientSocket = serverSocket.accept();
            ProtocolSocket pSocket = new ProtocolSocket(clientSocket);
            this.executor.submit(() -> send(pSocket, protocol));
        }
    }

    public void send(ProtocolSocket pSocket, Protocol protocol) {
        try {
            RequestPacket rp = pSocket.receiveRequest();
            this.mutexPublishedTopics.lock();
            try {
                if (!this.publishedTopics.containsKey(rp.topic))
                    throw new IllegalArgumentException("Received a request with a non published topic specified : " + rp.topic);
            } finally {
                this.mutexPublishedTopics.unlock();
            }

            if (protocol instanceof FileProtocol)
                sendFile(pSocket, (FileProtocol) protocol);
            else if (protocol instanceof TextProtocol)
                sendText(pSocket, (TextProtocol) protocol);
            else
                throw new RuntimeException("Incorrect protocol class");

        } catch (IOException | IncorrectMessageTypeException | NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
        } finally {
            try {
                pSocket.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to close socket: " + e.getMessage());
            }
        }
    }

    private void sendFile(ProtocolSocket pSocket, FileProtocol fileProtocol)
            throws IOException, NoSuchAlgorithmException {
        for (PFile pfile : fileProtocol.iter()) {
            pSocket.sendFileInfo(pfile.getFileInfoPacket());

            if (pSocket.isYes()) {
                for (byte[] filePiece : pfile.packetIterator())
                    pSocket.sendFilePiece(filePiece);
                pSocket.sendDone();
            }
        }
        pSocket.sendDone();
    }

    private void sendText(ProtocolSocket pSocket, TextProtocol textProtocol) throws IOException {
        for (byte[] textPacket : textProtocol.iter())
            pSocket.sendText(textPacket);
        pSocket.sendDone();
    }
}
