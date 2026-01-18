package controller;

import protocol.client.Hello;
import protocol.common.Feature;
import view.GameView;

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
    private GameView view;

    public Client(String host, int port){
        this.host = host;
        this.port = port;
        this.scanner = new Scanner(System.in);
        this.view = new GameView();
    }

    public static void main(String[] args){
        String host = "localhost";
        int port = 5555;

        Client client = new Client(host, port);
        client.view.showWelcome();

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
            serverHandler = new ServerHandler(in, this, view);
            Thread serverThread = new Thread(serverHandler);
            serverThread.start();
            System.out.println("Connected to server at " + host + ":" + port);

            // Send HELLO command
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
            if (serverHandler != null) {
                serverHandler.stop();
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
            System.out.println("Disconnected from server");
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    public void run() {
        view.showCommands();

        while (true) {
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                disconnectFromServer();
                break;
            }

            if (!input.isEmpty()) {
                sendMessage(input);

                // Protocol: If user played a discard move (PLAY to D.*), automatically send END
                if (isDiscardMove(input)) {
                    // Wait a tiny bit for server to process the discard
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        System.err.println("Sleep interrupted during discard wait");
                    }
                    sendMessage("END");
                    System.out.println(">>> Automatically sent END after discard");
                }
            }
        }
    }

    /**
     * Checks if a command is a discard move (PLAY to D.*)
     */
    private boolean isDiscardMove(String input) {
        if (!input.startsWith("PLAY~")) {
            return false;
        }
        String[] parts = input.split("~");
        if (parts.length >= 3) {
            String to = parts[2];
            // Check if destination is a discard pile (D.0, D.1, D.2, D.3)
            return to.startsWith("D.");
        }
        return false;
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}
