package control;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * Client for Skip-Bo game.
 * This is the main class that players run to connect to the server.
 *
 * Responsibilities:
 * - Connect to the server
 * - Send messages to the server
 * - Start a ServerHandler to receive messages
 */
public class Client {
    // Connection details
    private String host;
    private int port;

    // Network streams
    private Socket socket;
    private BufferedReader in;   // Read FROM server
    private PrintWriter out;     // Write TO server

    // Components
    private ServerHandler serverHandler;  // Handles incoming messages from server
    private Scanner scanner;              // Reads user input from console

    /**
     * Create a new client.
     *
     * @param host The server hostname (e.g., "localhost")
     * @param port The server port (e.g., 8888)
     */
    public Client(String host, int port) {
        this.host = host;
        this.port = port;
        this.scanner = new Scanner(System.in);
    }

    /**
     * Connect to the server.
     *
     * @return true if connection succeeded, false otherwise
     */
    public boolean connect() {
        try {
            // Create socket connection to the server
            socket = new Socket(host, port);

            // Set up input and output streams
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Start the ServerHandler in a separate thread
            // This allows us to receive messages while also sending them
            serverHandler = new ServerHandler(in, this);
            Thread serverThread = new Thread(serverHandler);
            serverThread.start();

            System.out.println("Connected to server at " + host + ":" + port);
            return true;

        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
            return false;
        }
    }

    /**
     * Disconnect from the server.
     */
    public void disconnect() {
        try {
            // Stop the server handler
            if (serverHandler != null) {
                serverHandler.stop();
            }

            // Close all connections
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();

            System.out.println("Disconnected from server");

        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    /**
     * Send a message to the server.
     *
     * @param message The message to send
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Main client loop.
     * Reads user input and sends it to the server.
     */
    public void run() {
        System.out.println("Type messages to send to server. Type 'quit' to exit.");

        // Keep reading user input until they quit
        while (true) {
            String input = scanner.nextLine().trim();

            // Check if user wants to quit
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                disconnect();
                break;
            }

            // Send the message to the server
            if (!input.isEmpty()) {
                sendMessage(input);
            }
        }
    }

    /**
     * Main method - start the client.
     * Usage: java control.Client [host] [port]
     */
    public static void main(String[] args) {
        // Default values
        String host = "localhost";
        int port = 8888;

        // Read host and port from command line arguments if provided
        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        // Create client, connect, and run
        Client client = new Client(host, port);
        if (client.connect()) {
            client.run();
        }
    }
}
