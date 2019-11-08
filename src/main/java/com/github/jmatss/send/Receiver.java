package com.github.jmatss.send;

import com.github.jmatss.send.protocol.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.MulticastSocket;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Receiver {
    private static final Logger LOGGER = Logger.getLogger(Receiver.class.getName());
    private final MulticastSocket socket;
    private final Set<String> subscribedTopics;
    private final Lock mutex;
    private Receiver receiver;

    private Receiver(MulticastSocket socket, Set<String> subscribedTopics, Lock mutex) {
        this.socket = socket;
        this.subscribedTopics = subscribedTopics;
        this.mutex = mutex;
    }

    /*
    if (MessageType::PUBLISH && SubMessageType::TEXT):
        MessageType (1 byte)
        | Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)       //
        | Random ID Number (4 bytes)    // unique
    if (MessageType::PUBLISH && SubMessageType::FILE):
        MessageType (1 byte)
        | Topic Length (1 byte)
        | Topic ("Topic Length" bytes)
        | SubMessageType (1 byte)
        | Random ID Number (4 bytes) (unique)
     */

    public void start(MulticastSocket socket, Set<String> subscribedTopics, Lock mutex) {
        if (receiver != null)
            return;
        else
            this.receiver = new Receiver(socket, subscribedTopics, mutex);

        byte[] buffer = new byte[Protocol.MAX_PUBLISH_PACKET_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        while (true) {
            try {
                socket.receive(packet);
                byte[] content = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getLength());

                if (content.length < Protocol.MIN_PUBLISH_PACKET_SIZE)
                    throw new IOException("Received to few byte: " + content.length);
                else if (content[0] != MessageType.PUBLISH.value())
                    throw new IOException("Received incorrect MessageType. Expected: "
                            + MessageType.PUBLISH.value() + ", got: " + (int) content[0]);

                int topicLength = (int) content[1];
                String topic = new String(content, 2, topicLength, Protocol.ENCODING);
                int subMessageType = (int) content[2 + topicLength];
                byte[] id = Arrays.copyOfRange(content, 2 + topicLength + 1, 2 + topicLength + 1 + 4);

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Exception when receiving packet in Receiver: " + e.getMessage());
            }
        }
    }

}
