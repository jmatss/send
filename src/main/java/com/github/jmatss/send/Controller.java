package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.packet.RequestPacket;
import com.github.jmatss.send.protocol.*;

import java.io.*;
import java.net.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.github.jmatss.send.protocol.ProtocolActions.*;

// TODO: Fix limitation of only one ip/port(?)
public class Controller {
    private static final Logger LOGGER = Logger.getLogger(Controller.class.getName());
    private ScheduledExecutorService executor;
    private MulticastSocket socket;
    private InetAddress ip;
    private int port;

    // PUBLISHING
    private Map<String, List<Future<?>>> publishedTopics;
    private Lock mutexPublishedTopics;

    // SUBSCRIBING
    private Set<String> subscribedTopics;
    private Lock mutexSubscribedTopics;

    Controller(String ip, int port) throws IOException {
        init(ip, port);
    }

    Controller(int port, boolean ipv6) throws IOException {
        try {
            init(ipv6 ? Protocol.DEFAULT_MULTICAST_IPV6 : Protocol.DEFAULT_MULTICAST_IPV4, port);
        } catch (UnknownHostException e) {
            // Should never happen since the default group is a hardcoded correct address.
            throw new RuntimeException(e);
        }
    }

    // Defaults to ipv4
    Controller(int port) throws IOException {
        this(port, false);
    }

    // Defaults to ipv4
    Controller() throws IOException {
        this(Protocol.DEFAULT_PORT, false);
    }

    private void init(String ip, int port) throws IOException {
        if (port > (1 << 16) - 1 || port < 0)
            throw new IllegalArgumentException("Incorrect port number: " + port);
        else if (!InetAddress.getByName(ip).isMulticastAddress())
            throw new IllegalArgumentException("Specified ip isn't a multicast address: " + ip);

        this.executor = ScheduledExecutorServiceSingleton.getInstance();

        this.publishedTopics = new HashMap<>();
        this.mutexPublishedTopics = new ReentrantLock(true);
        this.subscribedTopics = new TreeSet<>();
        this.mutexSubscribedTopics = new ReentrantLock(true);

        this.ip = InetAddress.getByName(ip);
        this.port = port;
        this.socket = new MulticastSocket(this.port);
        this.socket.joinGroup(this.ip);
    }

    /**
     * Adds and sends publishing messages via the executor.
     *
     * @param protocol is the protocol message to be sent.
     * @param topic    that the sender publishes on and the subscribers can listen on.
     * @param timeout  in seconds for how long the message should be published.
     * @param interval in seconds between every publishing packet sent.
     * @return the topic that can be used to access the created ScheduledFuture if one want's to cancel the
     * publishing before the timeout.
     * @throws IncorrectMessageTypeException thrown if a protocol containing a disallowed MessageType is given.
     */
    // TODO: Create listening socket in the beginning of this function and take port from it
    //  and send to getPublishPacket().
    public String publish(Protocol protocol, String topic, long timeout, long interval) throws IncorrectMessageTypeException, IOException {
        verifyProtocol(protocol);
        if (timeout < 0)
            throw new IllegalArgumentException("Timeout set to less than zero.");
        else if (interval <= 0)
            throw new IllegalArgumentException("Interval set to zero or less.");

        this.mutexPublishedTopics.lock();
        try {
            if (this.publishedTopics.containsKey(topic))
                throw new IllegalArgumentException("Already publishing on this topic.");

            ServerSocket serverSocket = new ServerSocket(0);
            Future<?> listener = this.executor.submit(
                    () -> {
                        try {
                            listener(serverSocket, protocol);
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Exception while listening on server socket: " + e.getMessage());
                        }
                    }
            );

            byte[] packet = protocol.getPublishPacket(topic, serverSocket.getLocalPort());
            DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, this.ip, this.port);
            Future<?> publisher = this.executor.scheduleAtFixedRate(
                    () -> {
                        try {
                            this.socket.send(datagramPacket);
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Unable to send publish packet: " + e.getMessage());
                        }
                    },
                    0,
                    interval,
                    TimeUnit.SECONDS
            );

            List<Future<?>> futureList = new ArrayList<>(2);
            futureList.add(listener);
            futureList.add(publisher);

            this.publishedTopics.put(topic, futureList);
        } finally {
            this.mutexPublishedTopics.unlock();
        }

        // Automatically cancel and remove task from this.publishedTasks map after timeout.
        // (but only if timeout is set and no one else has already done it).
        if (timeout != 0) {
            this.executor.schedule(
                    () -> {
                        cancelPublish(topic);
                    },
                    timeout,
                    TimeUnit.SECONDS
            );
        }

        return topic;
    }


    // FIXME: If someone does a manual cancel, and then re-published on the same topic,
    //  the "timeout-cancel" can cancel the newly published topic.
    private void cancelPublish(String topic) {
        this.mutexPublishedTopics.lock();
        try {
            if (this.publishedTopics.containsKey(topic)) {
                for (Future future : this.publishedTopics.get(topic))
                    future.cancel(true);
                this.publishedTopics.remove(topic);
            }
        } finally {
            this.mutexPublishedTopics.unlock();
        }
    }

    // TODO: fix so that the "send" function sends all packets before exiting if the task gets a cancel.
    private void listener(ServerSocket serverSocket, Protocol protocol) throws IOException {
        while (true) {
            Socket clientSocket = serverSocket.accept();
            this.executor.submit(() -> send(clientSocket, protocol));
        }
    }

    public void send(Socket socket, Protocol protocol) {
        OutputStream out;
        PushbackInputStream in;
        try {
            in = new PushbackInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            RequestPacket rp = receiveRequest(in);
            this.mutexPublishedTopics.lock();
            try {
                if (!this.publishedTopics.containsKey(rp.topic))
                    throw new IllegalArgumentException("Received a request with a non published topic specified : " + rp.topic);
            } finally {
                this.mutexPublishedTopics.unlock();
            }

            if (protocol instanceof FileProtocol)
                sendFile(in, out, (FileProtocol) protocol);
            else if (protocol instanceof TextProtocol)
                sendText(out, (TextProtocol) protocol);
            else
                throw new RuntimeException("Incorrect protocol class");

        } catch (IOException | IncorrectMessageTypeException | NoSuchAlgorithmException e) {
            LOGGER.log(Level.SEVERE, e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Unable to close socket: " + e.getMessage());
            }
        }
    }

    private void sendFile(InputStream in, OutputStream out, FileProtocol fileProtocol)
            throws IOException, NoSuchAlgorithmException {
        for (PFile pfile : fileProtocol.iter()) {
            sendFileInfo(out, pfile.getFileInfoPacket());

            if (isYes(in)) {
                // TODO: synchronize this index with the index inside filePiece.
                int index = 0;
                for (byte[] filePiece : pfile.packetIterator()) {
                    sendFilePiece(out, filePiece);
                    index++;
                }
                sendDone(out, index);
            }
        }
        sendDone(out, 0);
    }

    private void sendText(OutputStream out, TextProtocol textProtocol) throws IOException {
        for (byte[] textPacket : textProtocol.iter()) {
            ProtocolActions.sendText(out, textPacket);
        }
        sendDone(out, 0);
    }

    /**
     * Subscribes to the specified topic. The subscription can be canceled by calling cancelSubscribe with the topic.
     *
     * @param topic to subscribe to.
     * @return the topic.
     */
    public String subscribe(String topic) {
        this.mutexSubscribedTopics.lock();
        try {
            if (this.subscribedTopics.contains(topic))
                throw new IllegalArgumentException("Already subscribed to this topic.");
            this.subscribedTopics.add(topic);
        } finally {
            this.mutexSubscribedTopics.unlock();
        }

        return topic;
    }

    private void cancelSubscribe(String topic) {
        this.mutexSubscribedTopics.lock();
        try {
            if (this.subscribedTopics.contains(topic)) {
                this.subscribedTopics.remove(topic);
            }
        } finally {
            this.mutexSubscribedTopics.unlock();
        }
    }

    private Protocol verifyProtocol(Protocol protocol) throws IncorrectMessageTypeException {
        MessageType messageType = protocol.getMessageType();
        switch (messageType) {
            case FILE_PIECE:
                if (!(protocol instanceof FileProtocol))
                    throw new IncorrectMessageTypeException(errString(messageType, "FileProtocol"));
                break;
            case TEXT:
                if (!(protocol instanceof TextProtocol))
                    throw new IncorrectMessageTypeException(errString(messageType, "TextProtocol"));
                break;
            default:
                LOGGER.log(Level.SEVERE,
                        "Incorrect MessageType received in Sender: " + messageType.toString());
                throw new IncorrectMessageTypeException("Received a disallowed MessageType in Sender");
        }

        return protocol;
    }

    private String errString(MessageType messageType, String protocolClass) {
        return "Received MessageType \"" + messageType.toString() + "\" " +
                "from getMessageType() by a non " + protocolClass + " class.";
    }
}
