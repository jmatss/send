package com.github.jmatss.send;

import com.github.jmatss.send.protocol.Protocol;

import java.io.IOException;
import java.net.MulticastSocket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws IOException {
        String path = "";
        String ip = Protocol.DEFAULT_MULTICAST_IPV4;
        int port = Protocol.DEFAULT_PORT;
        MulticastSocket socket = new MulticastSocket(port);
        Controller controller = new Controller(path, socket, ip, port);

        try {
            Scanner in = new Scanner(System.in);
            while (true) {
                System.out.print(">> ");
                String input = in.nextLine();
                if (input.isEmpty()) {
                    System.out.println("Empty command.");
                    continue;
                }

                switch (input.getBytes(Protocol.ENCODING)[0]) {
                    case 'q':
                        System.out.println("QUIT");
                        return;
                    case 'l':   // list
                        System.out.println("LIST");
                        break;
                    case 'p':   // publish
                        System.out.println(input);
                        break;
                    case 's':   // subscribe
                        System.out.println("SUBSCRIBE");
                        break;
                    default:
                        usage(args);
                }
            }
        } finally {
            controller.shutdown();
        }
    }

    static void usage(String[] args) {
        System.out.println(
                String.format(
                        "%-7s %s %s",
                        "usage", args[0], "test"
                )
        );
    }

}
