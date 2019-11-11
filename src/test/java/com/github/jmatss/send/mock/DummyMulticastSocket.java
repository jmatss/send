package com.github.jmatss.send.mock;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class DummyMulticastSocket extends MulticastSocket {
    private final byte[] publish_packet;
    private final String host;
    private int amountOfReads;
    private boolean isClosed;

    public DummyMulticastSocket(byte[] publish_packet, String host, int amountOfReads) throws IOException {
        this.publish_packet = publish_packet;
        this.host = host;
        this.amountOfReads = amountOfReads;
        this.isClosed = false;
    }

    @Override
    public void receive(DatagramPacket p) throws IOException {
        // Block on receive if all all "amountOfReads" have been read.
        if (this.amountOfReads-- == 0) {
            while(true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        p.setData(this.publish_packet.clone());
        p.setLength(this.publish_packet.length);
        p.setAddress(InetAddress.getByName(this.host));
    }

    @Override
    public synchronized void close() {
        this.isClosed = true;
    }

    @Override
    public synchronized boolean isClosed() {
        return this.isClosed;
    }
}
