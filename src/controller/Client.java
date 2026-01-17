package controller;

import protocol.client.Hello;
import protocol.common.Feature;

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
    private String playerName;

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

        // Ask for player name
        System.out.print("Enter your player name: ");
        String name = client.scanner.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Invalid name. Exiting.");
            return;
        }

        client.playerName = name;

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
            System.out.println("Connected to server at " + host + ":" + port);

            // Send HELLO command to announce ourselves
            Hello hello = new Hello(playerName, new Feature[0]);
            sendMessage(hello.transformToProtocolString());
            System.out.println("Announced as: " + playerName);

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
        System.out.println("\nCommands:");
        System.out.println("  GAME~<num>     - Request game with <num> players (2-6)");
        System.out.println("  PLAY~<from>~<to> - Play a card (e.g., PLAY~H.5~B.0)");
        System.out.println("  TABLE          - Request table state");
        System.out.println("  HAND           - Request your hand");
        System.out.println("  END            - End your turn");
        System.out.println("  quit/exit      - Disconnect from server");
        System.out.println();

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

