package control;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * ClientHandler handles one connected client (server-side).
 * Each connected client gets their own ClientHandler running in a separate thread.
 *
 * Responsibilities:
 * - Read messages from this specific client
 * - Send messages to this specific client
 * - Notify the server about important events
 */
public class ClientHandler implements Runnable {
    // Network connection
    private Socket socket;
    private BufferedReader in;   // Read FROM client
    private PrintWriter out;     // Write TO client

    // References
    private Server server;       // The main server
    private String clientName;   // This client's name (once they send it)

    // State
    private boolean running;     // Is this handler still running?

    /**
     * Create a new ClientHandler.
     *
     * @param socket The socket connection to this client
     * @param server The main server
     */
    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.running = true;
    }

    /**
     * Main loop - runs in a separate thread.
     * Reads messages from the client and handles them.
     */
    @Override
    public void run() {
        try {
            // Set up input and output streams
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Read and handle messages until client disconnects
            String message;
            while (running && (message = in.readLine()) != null) {
                handleClientMessage(message.trim());
            }

        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Handle a message from the client.
     * For now, just echo it back. Later we will parse protocol messages.
     *
     * @param message The message from the client
     */
    private void handleClientMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        // For now, just print and echo back
        // Later we will parse protocol format like: COMMAND~param1~param2
        System.out.println("Client: " + message);

        // Echo the message back to the client
        sendMessage("Echo: " + message);

        // You could also broadcast to all clients:
        // server.broadcast(clientName + ": " + message, this);
    }

    /**
     * Send a message to this client.
     *
     * @param message The message to send
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Get this client's name (if they set it).
     *
     * @return The client name, or null if not set yet
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * Set this client's name.
     *
     * @param name The client's name
     */
    public void setClientName(String name) {
        this.clientName = name;
    }

    /**
     * Clean up when client disconnects.
     */
    private void cleanup() {
        running = false;

        // Tell the server this client disconnected
        server.removeClient(this);

        // Close all connections
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error cleaning up: " + e.getMessage());
        }
    }

    /**
     * Stop this handler.
     */
    public void stop() {
        running = false;
    }
}
