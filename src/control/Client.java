package control;

import protocol.common.Feature;
import protocol.common.position.*;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Simple client for Skip-Bo game.
 * Connects to a server and allows a player to join and play games.
 */
public class Client {
    // Connection settings
    private String host;
    private int port;

    // Network communication
    private Socket socket;
    private BufferedReader in;  // Reads messages FROM server
    private PrintWriter out;    // Sends messages TO server

    // Client state
    private String playerName;
    private boolean connected;
    private Scanner scanner;

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
        this.connected = false;
        this.scanner = new Scanner(System.in);
    }

    /**
     * Connect to the server.
     * Returns true if successful, false otherwise.
     */
    public boolean connect() {
        try {
            // Create socket connection to server
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            // Start a background thread to listen for server messages
            // We need a separate thread because listenForMessages() blocks waiting for messages
            // This allows us to send messages while also receiving them
            Thread listenerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    listenForMessages();
                }
            });
            listenerThread.start();

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
        connected = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    /**
     * Background thread that listens for messages from the server.
     */
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

    /**
     * Handle a message received from the server.
     * Messages are in protocol format: COMMAND~param1~param2~...
     */
    private void handleServerMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        // Split message by protocol separator
        String[] parts = message.split(protocol.Command.SEPERATOR, -1);
        String command = parts[0];

        try {
            // Handle different server commands
            switch (command) {
                case "WELCOME":
                    handleWelcome(parts);
                    break;
                case "QUEUE":
                    System.out.println("Added to game queue. Waiting for other players...");
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
            System.err.println("Error handling message: " + e.getMessage());
        }
    }

    /**
     * Handle WELCOME message from server.
     * Format: WELCOME~playerName
     */
    private void handleWelcome(String[] parts) {
        if (parts.length >= 2) {
            String name = parts[1];
            System.out.println("Player '" + name + "' joined the server");
        }
    }

    /**
     * Handle START message - game is starting.
     * Format: START~player1,player2,player3,...
     */
    private void handleStart(String[] parts) {
        if (parts.length >= 2) {
            String[] players = parts[1].split(protocol.Command.LIST_SEPERATOR);
            System.out.println("\n=== GAME STARTED ===");
            System.out.println("Players: " + String.join(", ", players));
            System.out.println("====================\n");
        }
    }

    /**
     * Handle TURN message - whose turn it is.
     * Format: TURN~playerName
     */
    private void handleTurn(String[] parts) {
        if (parts.length >= 2) {
            String currentPlayer = parts[1];
            boolean isYourTurn = currentPlayer.equals(playerName);
            System.out.println("\n--- It's " + currentPlayer + "'s turn ---");
            if (isYourTurn) {
                System.out.println("It's YOUR turn! Type 'help' for commands.");
            }
        }
    }

    /**
     * Handle HAND message - your cards.
     * Format: HAND~card1,card2,card3,...
     */
    private void handleHand(String[] parts) {
        if (parts.length >= 2) {
            String[] cards = parts[1].split(protocol.Command.LIST_SEPERATOR);
            System.out.println("Your hand: " + String.join(", ", cards));
        }
    }

    /**
     * Handle STOCK message - a player's stock pile top card.
     * Format: STOCK~playerName~topCard
     */
    private void handleStock(String[] parts) {
        if (parts.length >= 3) {
            String player = parts[1];
            String topCard = parts[2];
            System.out.println(player + "'s stock pile: " + topCard);
        }
    }

    /**
     * Handle TABLE message - building piles and discard piles.
     * Format: TABLE~bp1.bp2.bp3.bp4~player1Table,player2Table,...
     */
    private void handleTable(String[] parts) {
        if (parts.length >= 3) {
            // Building piles (shared by all players)
            String[] buildingPiles = parts[1].split("\\" + protocol.Command.VALUE_SEPERATOR);
            System.out.println("\n=== TABLE ===");
            System.out.print("Building piles: ");
            for (int i = 0; i < buildingPiles.length; i++) {
                System.out.print((i + 1) + ":" + buildingPiles[i] + " ");
            }
            System.out.println();

            // Player discard piles
            String[] playerTables = parts[2].split(protocol.Command.LIST_SEPERATOR);
            for (String playerTable : playerTables) {
                String[] tableParts = playerTable.split("\\" + protocol.Command.VALUE_SEPERATOR);
                if (tableParts.length >= 5) {
                    System.out.print(tableParts[0] + "'s discard: ");
                    for (int i = 1; i < 5; i++) {
                        System.out.print(i + ":" + tableParts[i] + " ");
                    }
                    System.out.println();
                }
            }
            System.out.println("=============\n");
        }
    }

    /**
     * Handle PLAY message - a player made a move.
     * Format: PLAY~from~to~playerName
     */
    private void handlePlay(String[] parts) {
        if (parts.length >= 4) {
            String player = parts[1];
            String from = parts[2];
            String to = parts[3];
            System.out.println(player + " played: " + from + " -> " + to);
        }
    }

    /**
     * Handle WINNER message - game is over.
     * Format: WINNER~player1.score1,player2.score2,...
     */
    private void handleWinner(String[] parts) {
        if (parts.length >= 2) {
            String[] scoreData = parts[1].split(protocol.Command.LIST_SEPERATOR);
            System.out.println("\n=== GAME OVER ===");
            for (String score : scoreData) {
                String[] scoreParts = score.split("\\" + protocol.Command.VALUE_SEPERATOR);
                if (scoreParts.length >= 2) {
                    System.out.println(scoreParts[0] + ": " + scoreParts[1] + " points");
                }
            }
            System.out.println("=================\n");
        }
    }

    /**
     * Handle ERROR message from server.
     * Format: ERROR~errorCode
     */
    private void handleError(String[] parts) {
        if (parts.length >= 2) {
            String errorCode = parts[1];
            String errorMessage = getErrorMessage(errorCode);
            System.out.println("ERROR " + errorCode + ": " + errorMessage);
        }
    }

    /**
     * Get a human-readable error message for an error code.
     */
    private String getErrorMessage(String code) {
        switch (code) {
            case "001": return "Invalid player name";
            case "002": return "Name already in use";
            case "103": return "Player disconnected";
            case "204": return "Invalid command";
            case "205": return "Command not allowed";
            case "206": return "Invalid move";
            default: return "Unknown error";
        }
    }

    /**
     * Send HELLO message to server (join with your name).
     */
    public void sendHello(String name) {
        this.playerName = name;
        protocol.client.Hello hello = new protocol.client.Hello(name, new Feature[0]);
        sendMessage(hello.transformToProtocolString());
    }

    /**
     * Send GAME message to server (request to join a game).
     */
    public void sendGame(int numberOfPlayers) {
        protocol.client.Game game = new protocol.client.Game(numberOfPlayers);
        sendMessage(game.transformToProtocolString());
    }

    /**
     * Send PLAY message to server (make a move).
     * Example: sendPlay("H.5", "B.1") means play card 5 from hand to building pile 1
     */
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

    /**
     * Send END message to server (end your turn).
     */
    public void sendEnd() {
        protocol.client.End end = new protocol.client.End();
        sendMessage(end.transformToProtocolString());
    }

    /**
     * Request your hand from the server.
     */
    public void sendHandRequest() {
        protocol.client.Hand hand = new protocol.client.Hand();
        sendMessage(hand.transformToProtocolString());
    }

    /**
     * Request table state from the server.
     */
    public void sendTableRequest() {
        protocol.client.Table table = new protocol.client.Table();
        sendMessage(table.transformToProtocolString());
    }

    /**
     * Parse a position string like "S", "H.5", "B.1", "D.2".
     * S = Stock pile
     * H = Hand (with card number)
     * B = Building pile (with pile number 1-4)
     * D = Discard pile (with pile number 1-4)
     */
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

    /**
     * Parse a card string like "5" or "SB" (Skip-Bo).
     */
    private protocol.common.Card parseCard(String cardStr) {
        try {
            if (cardStr.equalsIgnoreCase("SB")) {
                return new protocol.common.Card();  // Skip-Bo card
            } else {
                int number = Integer.parseInt(cardStr);
                return new protocol.common.Card(number);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Send a message to the server.
     */
    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Print help information.
     */
    private void showHelp() {
        System.out.println("\nCommands:");
        System.out.println("  play <from> <to>  - Make a move (e.g., 'play H.5 B.1')");
        System.out.println("  hand              - Request your hand");
        System.out.println("  table             - Request table state");
        System.out.println("  help              - Show this help");
        System.out.println("  quit              - Quit the game");
        System.out.println("\nPosition format:");
        System.out.println("  S           - Your stock pile");
        System.out.println("  H.<card>    - Card from hand (e.g., H.5, H.SB)");
        System.out.println("  B.<number>  - Building pile 1-4 (e.g., B.1)");
        System.out.println("  D.<number>  - Your discard pile 1-4 (e.g., D.1)");
        System.out.println();
    }

    /**
     * Main client loop - handles user input and sends commands to server.
     */
    public void run() {
        // Get player name
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

        // Get number of players
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
                    showHelp();
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

    /**
     * Main method - start the client.
     */
    public static void main(String[] args) {
        String host = "localhost";
        int port = 8888;

        // Read host and port from command line arguments
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

        // Create and connect client
        Client client = new Client(host, port);
        if (client.connect()) {
            client.run();
        }
    }
}
