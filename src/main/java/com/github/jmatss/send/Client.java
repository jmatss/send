package com.github.jmatss.send;

import com.github.jmatss.send.protocol.Protocol;

import java.io.IOException;
import java.net.MulticastSocket;
import java.util.List;
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
                try {
                    System.out.print(">> ");
                    String input = in.nextLine();
                    if (input.isEmpty()) {
                        usage();
                        continue;
                    }

                    String[] cmd = input.split(" ");

                    switch (cmd[0]) {
                        case "q":
                        case "quit":
                            return;
                        case "l":
                        case "ls":
                        case "list":
                            System.out.println("LIST:");
                            List<String> l = controller.list();
                            for (String s : l)
                                System.out.println(s);
                            break;
                        case "p":
                        case "pub":
                        case "publish":
                            boolean text = true;
                            long timeout = Controller.DEFAULT_PUBLISH_TIMEOUT;
                            long interval = Controller.DEFAULT_PUBLISH_INTERVAL;
                            String textOrPath;
                            String topic;

                            if (cmd.length >= 2) {
                                topic = cmd[1];
                            } else {
                                System.out.print("Topic: ");
                                topic = in.nextLine(); // TODO: make sure it isn't empty
                            }

                            System.out.print("Send t[ext]/f[iles] (default: text): ");
                            input = in.nextLine();
                            if (!input.isEmpty() && input.getBytes(Protocol.ENCODING)[0] == 'f')
                                text = false;

                            if (text)
                                System.out.print("Text: ");
                            else
                                System.out.print("Path: ");
                            textOrPath = in.nextLine(); // TODO: make sure it isn't empty

                            System.out.print("Timeout (default: " + timeout + " s, 0 = infinite): ");
                            input = in.nextLine();
                            if (!input.isEmpty()) {
                                try {
                                    timeout = Long.parseLong(input);
                                } catch (NumberFormatException e) {
                                    System.out.println("Unable to parse timeout string: " + e.getMessage());
                                    break;
                                }
                            }

                            System.out.print("Interval (default: " + interval + " s): ");
                            input = in.nextLine();
                            if (!input.isEmpty()) {
                                try {
                                    interval = Long.parseLong(input);
                                } catch (NumberFormatException e) {
                                    System.out.println("Unable to parse interval string: " + e.getMessage());
                                    break;
                                }
                            }

                            if (text)
                                controller.publishText(topic, textOrPath, timeout, interval);
                            else
                                controller.publishFile(topic, textOrPath, timeout, interval);

                            System.out.println("PUBLISHED to " + topic);
                            break;
                        case "up":
                        case "unpublish":
                            if (cmd.length != 2) {
                                System.out.println("Incorrect input");
                                usage();
                                break;
                            }

                            controller.cancelPublish(cmd[1]);
                            System.out.println("UNPUBLISHED " + cmd[1]);
                            break;
                        case "s":
                        case "sub":
                        case "subscribe":
                            if (cmd.length != 2) {
                                System.out.println("Incorrect input");
                                usage();
                                break;
                            }

                            controller.subscribe(cmd[1]);
                            System.out.println("SUBSCRIBED to " + cmd[1]);
                            break;
                        case "us":
                        case "unsubscribe":
                            if (cmd.length != 2) {
                                System.out.println("Incorrect input");
                                usage();
                                break;
                            }

                            controller.cancelSubscribe(cmd[1]);
                            System.out.println("UNSUBSCRIBED " + cmd[1]);
                            break;
                        default:
                            System.out.println("Invalid command.");
                            usage();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } finally {
            controller.shutdown();
        }
    }

    static void usage() {
        System.out.println("Commands:\n" +
                "\tl/ls/list\n" +
                "\tp/pub/publish [<TOPIC>]\n" +
                "\tup/unpublish <TOPIC>\n" +
                "\ts/sub/subscribe <TOPIC>\n" +
                "\tus/unsubscribe <TOPIC>\n" +
                "\tq/quit"
        );
    }
}
