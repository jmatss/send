package com.github.jmatss.send;

import com.github.jmatss.send.exception.IncorrectMessageTypeException;
import com.github.jmatss.send.mock.DummyMulticastSocket;
import com.github.jmatss.send.protocol.Protocol;
import com.github.jmatss.send.protocol.TextProtocol;
import com.github.jmatss.send.type.MessageType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class SenderTest {
    @Test
    public void testSenderSendingPublishPacketCorrectly()
            throws IOException, InterruptedException, IncorrectMessageTypeException {
        String text = "Test_text";
        String path = "";
        String ip = Protocol.DEFAULT_MULTICAST_IPV4;
        int port = Protocol.DEFAULT_PORT;
        String host = "127.0.0.1";
        DummyMulticastSocket mSocket = new DummyMulticastSocket(host);

        String topic = "test_topic";
        byte[] topicBytes = topic.getBytes(Protocol.ENCODING);
        long timeout = 0;
        long interval = 1;
        Protocol protocol = new TextProtocol(text);

        Controller controller = new Controller(path, mSocket, ip, port);
        try {
            controller.publish(protocol, topic, timeout, interval);

            DatagramPacket receivedPacket = mSocket.receiveTest();
            byte[] receivedPacketData = Arrays.copyOfRange(
                    receivedPacket.getData(),
                    receivedPacket.getOffset(),
                    receivedPacket.getOffset() + receivedPacket.getLength()
            );

            /*
                Expected values

                OBS! The last 8 bytes will be unknown (port and id).
                Just test if port is inside the proper range 0 < port < 2^16.
             */
            int expectedPacketLength = topicBytes.length + 11;
            byte expectedMessageType = (byte) MessageType.PUBLISH.value();
            byte expectedTopicLength = (byte) topicBytes.length;
            String expectedTopic = topic;
            byte expectedSubMessageType = (byte) MessageType.TEXT.value();

            ByteBuffer receivedPacketBuffer = ByteBuffer.allocate(receivedPacketData.length + 11)
                    .put(receivedPacketData);
            receivedPacketBuffer.rewind();

            /*
                Actual values
             */
            int actualPacketLength = receivedPacketData.length;
            byte actualMessageType = receivedPacketBuffer.get();
            byte actualTopicLength = receivedPacketBuffer.get();
            byte[] actualTopicBytes = new byte[actualTopicLength];
            receivedPacketBuffer.get(actualTopicBytes);
            String actualTopic = new String(actualTopicBytes, Protocol.ENCODING);
            byte actualSubMessageType = receivedPacketBuffer.get();
            int actualPort = receivedPacketBuffer.getInt();
            // No tests for actualID

            /*
                Verify that the received Publish packet is correct
             */
            assertEquals(expectedPacketLength, actualPacketLength);
            assertEquals(expectedMessageType, actualMessageType);
            assertEquals(expectedTopicLength, actualTopicLength);
            assertEquals(expectedTopic, actualTopic);
            assertEquals(expectedSubMessageType, actualSubMessageType);
            assertTrue(actualPort > 0, "Received port was less than or equal to zero");
            assertTrue(actualPort < (1 << 16), "Received port was greater than or equal to 2^16");
        } finally {
            controller.shutdown();
        }
    }
}
