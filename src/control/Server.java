package control;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Server for Skip-Bo game.
 * This is the main server class that accepts client connections.
 *
 * Responsibilities:
 * - Listen for incoming client connections
 * - Create a ClientHandler for each connected client
 * - Keep track of all connected clients
 * - Provide methods to send messages to clients
 */
public class Server {
    private int port;                           // Port number to listen on
    private ServerSocket serverSocket;          // Server socket for accepting connections
    private boolean running;                    // Is the server running?
    private List<ClientHandler> clients;        // List of all connected clients

    /**
     * Create a new server.
     *
     * @param port The port to listen on (e.g., 8888)
     */
    public Server(int port) {
        this.port = port;
        this.running = false;
        this.clients = new ArrayList<>();
    }

    /**
     * Start the server.
     * This method blocks and runs until the server is stopped.
     */
    public void start() {
        try {
            // Create server socket
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started on port " + port);
            System.out.println("Waiting for clients to connect...");

            // Keep accepting client connections
            while (running) {
                try {
                    // Wait for a client to connect (this blocks until a client connects)
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    // Create a handler for this client
                    ClientHandler handler = new ClientHandler(clientSocket, this);

                    // Add to our list of clients
                    clients.add(handler);

                    // Start the handler in a new thread
                    // This allows the server to handle multiple clients at the same time
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

    /**
     * Stop the server.
     */
    public void stop() {
        running = false;

        // Stop all client handlers
        for (ClientHandler client : clients) {
            client.stop();
        }

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server: " + e.getMessage());
        }
    }

    /**
     * Remove a client from the list (called when they disconnect).
     *
     * @param handler The client handler to remove
     */
    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("Client disconnected. Total clients: " + clients.size());
    }

    /**
     * Send a message to all connected clients.
     *
     * @param message The message to broadcast
     */
    public void broadcast(String message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    /**
     * Send a message to all clients except one.
     *
     * @param message The message to send
     * @param exclude The client to exclude from the broadcast
     */
    public void broadcast(String message, ClientHandler exclude) {
        for (ClientHandler client : clients) {
            if (client != exclude) {
                client.sendMessage(message);
            }
        }
    }

    /**
     * Get the number of connected clients.
     *
     * @return The number of connected clients
     */
    public int getClientCount() {
        return clients.size();
    }

    /**
     * Main method - start the server.
     * Usage: java control.Server [port]
     */
    public static void main(String[] args) {
        // Default port
        int port = 8888;

        // Read port from command line arguments if provided
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        // Create and start server
        Server server = new Server(port);
        server.start();
    }
}
