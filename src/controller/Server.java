package controller;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private int port;
    private ServerSocket serverSocket;
    private boolean running;
    private List<ClientHandler> clients;

    public Server(int port){
        this.port = port;
        this.running = false;
        this.clients = new ArrayList<>();
    }

    public static void main(String[] args) {
        // Default port
        int port = 5555;
        // Create and start server
        Server server = new Server(port);
        server.start();
    }

    public void start() {
        try{
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started on port " + port);
            System.out.println("Waiting for clients to connect.");

            while (running) {
                try {
                    // Wait for a client to connect
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    // Create a personal handler for this client
                    ClientHandler handler = new ClientHandler(clientSocket, this);

                    // Add to our list of clients
                    clients.add(handler);

                    // Start the handler in a new thread
                    Thread clientThread = new Thread(handler);
                    clientThread.start();

                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }


    public void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }


    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("Client disconnected. Total clients: " + clients.size());
    }

}