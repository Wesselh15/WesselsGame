package Controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ServerHandler serverHandler;
    private Scanner scanner;

    public Client(String host, int port){
        this.host = host;
        this.port = port;
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args){
        String host = "localhost";
        int port = 5555;

        // Creates client
        Client client = new Client(host, port);
        if (client.connectToServer()){
            client.run();
        }
    }


    public boolean connectToServer(){
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            serverHandler = new ServerHandler(in, this);
            Thread serverThread = new Thread(serverHandler);
            serverThread.start();
            System.out.println("Connected to server with " + host + ": " + port);
            return true;
        } catch (IOException e){
            System.out.println("Could not connect to the server");
            return false;
        }
    }

    public void disconnectFromServer() {
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

    public void run() {
        System.out.println("Type messages to send to server. Type 'quit' to exit.");
        while (true) {
            String input = scanner.nextLine().trim();
            // Checks if user wants to disconnect
            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                disconnectFromServer();
                break;
            }
            // Sends the message to the server
            if (!input.isEmpty()) {
                sendMessage(input);
            }
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

}

