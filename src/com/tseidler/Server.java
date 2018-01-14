package com.tseidler;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static ConcurrentHashMap<String, Socket> socketLoginMap = new ConcurrentHashMap<>();
    enum UserState {
        LOGGED_IN, NOT_LOGGED_IN
    }

    public static void main(String[] args) {
        new Server().startServer();
    }

    public void startServer() {
        final ExecutorService clientProcessingPool = Executors.newFixedThreadPool(40);

        Runnable serverTask = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket serverSocket = new ServerSocket(5000);
                    System.out.println("Waiting for clients to connect...");
                    while (true) {
                        Socket clientSocket = serverSocket.accept();
                        clientProcessingPool.submit(new ClientTask(clientSocket));
                    }
                } catch (IOException e) {
                    System.err.println("Unable to process client request");
                    e.printStackTrace();
                }
            }
        };
        Thread serverThread = new Thread(serverTask);
        serverThread.start();

    }

    private class ClientTask implements Runnable {
        private final Socket clientSocket;

        private ClientTask(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            System.out.println("Got a client !");

            try {
                UserState userState = UserState.NOT_LOGGED_IN;
                DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
                PrintStream outputStream = new PrintStream(clientSocket.getOutputStream());

                outputStream.println("Welcome, provide login: (LOGIN: login)");

                String line = inputStream.readLine();
                String login = "";

                while (userState == UserState.NOT_LOGGED_IN && line.compareToIgnoreCase("logout") != 0) {
                    if (line.startsWith("LOGIN: ")) {
                        login = line.substring("LOGIN: ".length());
                        if (!socketLoginMap.containsKey(login)) {
                            userState = UserState.LOGGED_IN;
                            outputStream.println("LOGIN OK");
                            socketLoginMap.put(login, clientSocket);
                            broadcast_all("CONNECTED " + login);
                            System.out.println(login + " logged in...");
                        } else {
                            outputStream.println("login already taken, try another one!");
                        }
                    } else {
                        outputStream.println("LOGIN: login or type LOGOUT");
                    }
                    line = inputStream.readLine();
                }

                while (line.compareToIgnoreCase("logout") != 0) {
                    if (line.startsWith("BROADCAST:")) {
                        broadcast(line.substring("BROADCAST:".length() + 1), login);
                    } else if (line.startsWith("PRIVATE:")) {
                        String toUser = line.split("\\s")[1];
                        if (socketLoginMap.containsKey(toUser)) {
                            PrintStream output = new PrintStream(socketLoginMap.get(toUser).getOutputStream());
                            output.println(login + "(whispers): " + line.substring(("PRIVATE: " + toUser).length() + 1));
                        } else {
                            outputStream.println("user: " + toUser + " not found!");
                        }
                    } else {
                        outputStream.println("try with: BROADCAST: (broadcast), PRIVATE: user message (private message)");
                    }
                    line = inputStream.readLine();
                }

                outputStream.println("LOGOUT");
                socketLoginMap.remove(login);
                broadcast_all("DISCONNECTED " + login);
                System.out.println(login + " logged out...");
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void broadcast(String message, String user) throws IOException {
            for (String login : socketLoginMap.keySet()) {
                if (login.compareTo(user) != 0) {
                    PrintStream outputStream = new PrintStream(socketLoginMap.get(login).getOutputStream());
                    outputStream.println(user + ": " + message);
                }
            }
        }

        private void broadcast_all(String message) throws IOException {
            for (String login : socketLoginMap.keySet()) {
                PrintStream outputStream = new PrintStream(socketLoginMap.get(login).getOutputStream());
                outputStream.println(message);
            }
        }
    }
}