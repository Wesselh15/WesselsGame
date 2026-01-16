package control;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * ServerHandler listens for messages from the server (client-side).
 * This class runs in a separate thread so the client can send and receive at the same time.
 *
 * Responsibilities:
 * - Read messages from the server
 * - Handle/display those messages to the user
 */
public class ServerHandler implements Runnable {
    private BufferedReader in;  // Stream to read messages FROM the server
    private Client client;      // Reference to the client (to call its methods)
    private boolean running;    // Is this handler still running?

    /**
     * Create a new ServerHandler.
     *
     * @param in The input stream to read server messages from
     * @param client The client this handler belongs to
     */
    public ServerHandler(BufferedReader in, Client client) {
        this.in = in;
        this.client = client;
        this.running = true;
    }

    /**
     * Main loop - runs in a separate thread.
     * Continuously reads messages from the server and handles them.
     */
    @Override
    public void run() {
        try {
            String message;
            // Keep reading messages until connection is lost or we stop
            while (running && (message = in.readLine()) != null) {
                handleServerMessage(message.trim());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Connection to server lost: " + e.getMessage());
            }
        }
    }

    /**
     * Handle a message received from the server.
     * For now, just print it. Later we can parse protocol messages here.
     *
     * @param message The message from the server
     */
    private void handleServerMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        // For now, just print the message
        // Later we will parse protocol format like: COMMAND~param1~param2
        System.out.println("Server: " + message);
    }

    /**
     * Stop this handler.
     */
    public void stop() {
        running = false;
    }
}
