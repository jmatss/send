package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.protocol.*;
import com.github.jmatss.send.type.MessageType;
import com.github.jmatss.send.util.ClosableWrapper;
import com.github.jmatss.send.util.LockableHashMap;
import com.github.jmatss.send.util.LockableHashSet;
import com.github.jmatss.send.util.ScheduledExecutorServiceSingleton;

import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Controller {
    public static final String ENCODING = "UTF-8";
    public static final long DEFAULT_PUBLISH_TIMEOUT = 0; // (0 = infinite)
    public static final long DEFAULT_PUBLISH_INTERVAL = 5; // Seconds

    private static final Logger LOGGER = Logger.getLogger(Controller.class.getName());
    private ScheduledExecutorService executor;
    private MulticastSocket socket;
    private InetAddress ip;
    private int port;
    private LockableHashMap<String, ClosableWrapper> publishedTopics;
    private LockableHashSet<String> subscribedTopics;

    Controller(String downloadPath, MulticastSocket socket, String ip, int port) throws IOException {
        if (port > (1 << 16) - 1 || port < 0)
            throw new IllegalArgumentException("Incorrect port number: " + port);
        else if (!InetAddress.getByName(ip).isMulticastAddress())
            throw new IllegalArgumentException("Specified ip isn't a multicast address: " + ip);

        this.executor = ScheduledExecutorServiceSingleton.getInstance();
        this.ip = InetAddress.getByName(ip);
        this.port = port;
        this.socket = socket;
        this.socket.joinGroup(this.ip);

        this.publishedTopics = new LockableHashMap<>();
        Sender.initInstance(this.publishedTopics);

        this.subscribedTopics = new LockableHashSet<>();
        Receiver receiver = Receiver.initInstance(Paths.get(downloadPath), this.socket, this.subscribedTopics);
        this.executor.submit(receiver::start);
    }

    Controller(String downloadPath, MulticastSocket socket, int port, boolean ipv6) throws IOException {
        this(downloadPath, socket, ipv6 ? Protocol.DEFAULT_MULTICAST_IPV6 : Protocol.DEFAULT_MULTICAST_IPV4, port);
    }

    // Defaults to ipv4
    Controller(String downloadPath, MulticastSocket socket, int port) throws IOException {
        this(downloadPath, socket, port, false);
    }

    // Defaults to ipv4
    Controller(String downloadPath, MulticastSocket socket) throws IOException {
        this(downloadPath, socket, Protocol.DEFAULT_PORT, false);
    }

    public List<Runnable> shutdown() throws IOException {
        this.socket.leaveGroup(this.ip);
        this.socket.close();
        return this.executor.shutdownNow();
    }

    public List<String> list() {
        List<String> result = new ArrayList<>();
        try (
                LockableHashMap lhm = this.publishedTopics.lock();
                LockableHashSet lts = this.subscribedTopics.lock()
        ) {
            for (String s : this.publishedTopics.keySet())
                result.add("pub : " + s);
            for (String s : this.subscribedTopics)
                result.add("sub : " + s);
        }
        return result;
    }

    public void setPath(String downloadPath) {
        Receiver.getInstance().setPath(Paths.get(downloadPath));
    }

    /**
     * Adds and sends publishing messages via the executor.
     *
     * @param protocol is the protocol message to be sent.
     * @param topic    that the sender publishes on and the subscribers can listen on.
     * @param timeout  in seconds for how long the message should be published. A value of zero indicates infinite
     *                 publishing.
     * @param interval in seconds between every publishing packet sent.
     * @return the topic that can be used to access the created ScheduledFuture if one want's to cancel the
     * publishing before the timeout.
     * @throws IncorrectMessageTypeException thrown if a protocol containing a disallowed MessageType is given.
     * @throws IOException thrown if it is unable to create a ServerSocket.
     */
    public String publish(Protocol protocol, String topic, long timeout, long interval)
            throws IncorrectMessageTypeException, IOException {
        verifyProtocol(protocol);
        if (timeout < 0)
            throw new IllegalArgumentException("Timeout set to less than zero.");
        else if (interval <= 0)
            throw new IllegalArgumentException("Interval set to zero or less.");

        try (LockableHashMap l = this.publishedTopics.lock()) {
            if (this.publishedTopics.containsKey(topic))
                throw new IllegalArgumentException("Already publishing on this topic.");

            ServerSocket serverSocket = new ServerSocket(0);
            Future<?> listener = this.executor.submit(
                    () -> {
                        try {
                            Sender.getInstance().listen(serverSocket, protocol);
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

            ClosableWrapper closeWrapper = new ClosableWrapper(serverSocket, listener, publisher);
            this.publishedTopics.put(topic, closeWrapper);
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
    public void cancelPublish(String topic) {
        try (LockableHashMap l = this.publishedTopics.lock()) {
            if (this.publishedTopics.containsKey(topic)) {
                this.publishedTopics.get(topic).close();
                this.publishedTopics.remove(topic);
            } else {
                throw new IllegalArgumentException("Not publishing on this topic.");
            }
        }
    }

    public void publishText(String topic, String text, long timeout, long interval)
            throws IOException, IncorrectMessageTypeException {
        Protocol protocol = new TextProtocol(text);
        publish(protocol, topic, timeout, interval);
    }

    public void publishFile(String topic, String path, long timeout, long interval)
            throws IOException, IncorrectMessageTypeException {
        File f = new File(path);
        if (!f.exists())
            throw new IOException("The path \"" + path + "\" doesn't exist.");
        else if (!f.isFile() && !f.isDirectory())
            throw new IOException("The path \"" + path + "\" is neither a file nor a directory.");

        List<String> names = new ArrayList<>();
        List<String> paths = new ArrayList<>();
        Path basePath = f.getParentFile().toPath();
        getFilesRecursively(f, basePath, names, paths);

        publish(new FileProtocol(names, paths), topic, timeout, interval);
    }

    private void getFilesRecursively(File f, Path basePath, List<String> names, List<String> paths) {
        if (f.isFile()) {
            names.add(basePath.relativize(f.toPath()).toString());
            paths.add(f.getPath());
        } else if (f.isDirectory()) {
            for (File newF : Objects.requireNonNull(f.listFiles())) {
                if (newF != null)
                    getFilesRecursively(newF, basePath, names, paths);
            }
        }
    }

    /**
     * Subscribes to the specified topic. The subscription can be canceled by calling cancelSubscribe with the topic.
     *
     * @param topic to subscribe to.
     * @return the topic.
     */
    public String subscribe(String topic) {
        try (LockableHashSet l = this.subscribedTopics.lock()) {
            if (this.subscribedTopics.contains(topic))
                throw new IllegalArgumentException("Already subscribed to this topic.");
            this.subscribedTopics.add(topic);
        }

        return topic;
    }

    public void cancelSubscribe(String topic) {
        try (LockableHashSet l = this.subscribedTopics.lock()) {
            if (!this.subscribedTopics.contains(topic))
                throw new IllegalArgumentException("Not subscribed to this topic.");
            this.subscribedTopics.remove(topic);
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
