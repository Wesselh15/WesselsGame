package control;

import protocol.common.Feature;
import protocol.common.position.*;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String playerName;
    private boolean connected;
    private boolean inGame;

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
        this.connected = false;
        this.inGame = false;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            // Start listener thread
            Thread listenerThread = new Thread(this::listenForMessages);
            listenerThread.setDaemon(true);
            listenerThread.start();

            System.out.println("Connected to server at " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        connected = false;

        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                handleServerMessage(message.trim());
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("Connection lost: " + e.getMessage());
                connected = false;
            }
        }
    }

    private void handleServerMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        String[] parts = message.split(protocol.Command.SEPERATOR, -1);
        String command = parts[0];

        try {
            switch (command) {
                case "WELCOME":
                    handleWelcome(parts);
                    break;
                case "QUEUE":
                    System.out.println("You have been added to the game queue. Waiting for other players...");
                    break;
                case "START":
                    handleStart(parts);
                    break;
                case "TURN":
                    handleTurn(parts);
                    break;
                case "HAND":
                    handleHand(parts);
                    break;
                case "STOCK":
                    handleStock(parts);
                    break;
                case "TABLE":
                    handleTable(parts);
                    break;
                case "PLAY":
                    handlePlay(parts);
                    break;
                case "WINNER":
                    handleWinner(parts);
                    break;
                case "ERROR":
                    handleError(parts);
                    break;
                default:
                    System.out.println("Unknown server message: " + message);
            }
        } catch (Exception e) {
            System.err.println("Error handling server message: " + e.getMessage());
        }
    }

    private void handleWelcome(String[] parts) {
        if (parts.length >= 2) {
            String name = parts[1];
            System.out.println("Player '" + name + "' has joined the server");
        }
    }

    private void handleStart(String[] parts) {
        if (parts.length >= 2) {
            String[] players = parts[1].split(protocol.Command.LIST_SEPERATOR);
            System.out.println("\n=== GAME STARTED ===");
            System.out.println("Players: " + String.join(", ", players));
            System.out.println("====================\n");
            inGame = true;
        }
    }

    private void handleTurn(String[] parts) {
        if (parts.length >= 2) {
            String currentPlayer = parts[1];
            System.out.println("\n--- It's " + currentPlayer + "'s turn ---");

            if (currentPlayer.equals(playerName)) {
                System.out.println("It's YOUR turn!");
                printHelp();
            }
        }
    }

    private void handleHand(String[] parts) {
        if (parts.length >= 2) {
            String[] cards = parts[1].split(protocol.Command.LIST_SEPERATOR);
            System.out.println("Your hand: " + String.join(", ", cards));
        }
    }

    private void handleStock(String[] parts) {
        if (parts.length >= 3) {
            String player = parts[1];
            String topCard = parts[2];
            System.out.println(player + "'s stock pile top card: " + topCard);
        }
    }

    private void handleTable(String[] parts) {
        if (parts.length >= 3) {
            System.out.println("\n=== TABLE STATE ===");

            // Building piles
            String[] buildingPiles = parts[1].split("\\" + protocol.Command.VALUE_SEPERATOR);
            System.out.println("Building Piles: " +
                "1:" + buildingPiles[0] + " " +
                "2:" + buildingPiles[1] + " " +
                "3:" + buildingPiles[2] + " " +
                "4:" + buildingPiles[3]);

            // Player tables
            String[] playerTables = parts[2].split(protocol.Command.LIST_SEPERATOR);
            for (String playerTable : playerTables) {
                String[] playerParts = playerTable.split("\\" + protocol.Command.VALUE_SEPERATOR);
                if (playerParts.length >= 5) {
                    System.out.println(playerParts[0] + "'s discard piles: " +
                        "1:" + playerParts[1] + " " +
                        "2:" + playerParts[2] + " " +
                        "3:" + playerParts[3] + " " +
                        "4:" + playerParts[4]);
                }
            }
            System.out.println("==================\n");
        }
    }

    private void handlePlay(String[] parts) {
        if (parts.length >= 4) {
            String player = parts[1];
            String from = parts[2];
            String to = parts[3];
            System.out.println(player + " played: " + from + " -> " + to);
        }
    }

    private void handleWinner(String[] parts) {
        if (parts.length >= 2) {
            System.out.println("\n=== GAME OVER ===");
            String[] scores = parts[1].split(protocol.Command.LIST_SEPERATOR);
            for (String score : scores) {
                String[] scoreParts = score.split("\\" + protocol.Command.VALUE_SEPERATOR);
                if (scoreParts.length >= 2) {
                    System.out.println(scoreParts[0] + ": " + scoreParts[1] + " points");
                }
            }
            System.out.println("=================\n");
            inGame = false;
        }
    }

    private void handleError(String[] parts) {
        if (parts.length >= 2) {
            String errorCode = parts[1];
            System.out.println("ERROR " + errorCode + ": " + getErrorMessage(errorCode));
        }
    }

    private String getErrorMessage(String code) {
        switch (code) {
            case "001":
                return "Invalid player name";
            case "002":
                return "Name already in use";
            case "103":
                return "Player disconnected";
            case "204":
                return "Invalid command";
            case "205":
                return "Command not allowed";
            case "206":
                return "Invalid move";
            default:
                return "Unknown error";
        }
    }

    public void sendHello(String name) {
        this.playerName = name;
        protocol.client.Hello hello = new protocol.client.Hello(
            name,
            new Feature[0]
        );
        sendMessage(hello.transformToProtocolString());
    }

    public void sendGame(int numberOfPlayers) {
        protocol.client.Game game = new protocol.client.Game(numberOfPlayers);
        sendMessage(game.transformToProtocolString());
    }

    public void sendPlay(String from, String to) {
        try {
            Position fromPos = parsePositionString(from);
            Position toPos = parsePositionString(to);

            if (fromPos != null && toPos != null) {
                protocol.client.Play play = new protocol.client.Play(fromPos, toPos);
                sendMessage(play.transformToProtocolString());
            } else {
                System.out.println("Invalid position format");
            }
        } catch (Exception e) {
            System.out.println("Error sending play: " + e.getMessage());
        }
    }

    public void sendEnd() {
        protocol.client.End end = new protocol.client.End();
        sendMessage(end.transformToProtocolString());
    }

    public void sendHandRequest() {
        protocol.client.Hand hand = new protocol.client.Hand();
        sendMessage(hand.transformToProtocolString());
    }

    public void sendTableRequest() {
        protocol.client.Table table = new protocol.client.Table();
        sendMessage(table.transformToProtocolString());
    }

    private Position parsePositionString(String posStr) {
        try {
            String[] parts = posStr.split("\\.");
            String type = parts[0].toUpperCase();

            switch (type) {
                case "S":
                    return new StockPilePosition();
                case "H":
                    if (parts.length > 1) {
                        protocol.common.Card card = parseCard(parts[1]);
                        return new HandPosition(card);
                    }
                    return new HandPosition(null);
                case "B":
                    int bpNum = Integer.parseInt(parts[1]);
                    return new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, bpNum);
                case "D":
                    int dpNum = Integer.parseInt(parts[1]);
                    return new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, dpNum);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private protocol.common.Card parseCard(String cardStr) {
        try {
            if (cardStr.equalsIgnoreCase("SB")) {
                return new protocol.common.Card();
            } else {
                int number = Integer.parseInt(cardStr);
                return new protocol.common.Card(number);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void printHelp() {
        System.out.println("\nCommands:");
        System.out.println("  play <from> <to>  - Make a move (e.g., 'play H.5 B.1' or 'play S D.1')");
        System.out.println("  hand              - Request your hand");
        System.out.println("  table             - Request table state");
        System.out.println("  help              - Show this help");
        System.out.println("\nPosition format:");
        System.out.println("  S           - Stock pile");
        System.out.println("  H.<card>    - Hand (e.g., H.5, H.SB)");
        System.out.println("  B.<number>  - Building pile 1-4 (e.g., B.1)");
        System.out.println("  D.<number>  - Discard pile 1-4 (e.g., D.1)");
        System.out.println();
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter your name: ");
        String name = scanner.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Invalid name");
            return;
        }

        sendHello(name);

        // Wait a bit for server response
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ignore
        }

        System.out.print("Enter number of players (2-6): ");
        try {
            int numPlayers = Integer.parseInt(scanner.nextLine().trim());
            sendGame(numPlayers);
        } catch (NumberFormatException e) {
            System.out.println("Invalid number");
            return;
        }

        // Main command loop
        while (connected) {
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split("\\s+");
            String command = parts[0].toLowerCase();

            switch (command) {
                case "play":
                    if (parts.length >= 3) {
                        sendPlay(parts[1], parts[2]);
                    } else {
                        System.out.println("Usage: play <from> <to>");
                    }
                    break;
                case "hand":
                    sendHandRequest();
                    break;
                case "table":
                    sendTableRequest();
                    break;
                case "help":
                    printHelp();
                    break;
                case "quit":
                case "exit":
                    disconnect();
                    return;
                default:
                    System.out.println("Unknown command. Type 'help' for help.");
            }
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 8888;

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

        Client client = new Client(host, port);
        if (client.connect()) {
            client.run();
        }
    }
}
