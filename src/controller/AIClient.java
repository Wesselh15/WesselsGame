package controller;

import protocol.client.Hello;
import protocol.common.Feature;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Smart AI client that only makes valid moves
 * The AI knows:
 * - What the building piles expect (1-12 or X if full)
 * - What its stock top card is
 * - Which moves are valid
 */
public class AIClient {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String playerName;
    private boolean running;
    private Random random;

    // Game state tracking
    private List<String> hand;
    private boolean myTurn;

    // NEW: Smart AI tracking
    private String stockTopCard;           // What is my stock top card?
    private String[] buildingPileNext;     // What does each building pile expect? (1-12 or X)

    public AIClient(String host, int port, String playerName) {
        this.host = host;
        this.port = port;
        this.playerName = playerName;
        this.random = new Random();
        this.hand = new ArrayList<>();
        this.myTurn = false;
        this.buildingPileNext = new String[4];  // 4 building piles
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5555;
        String aiName = "AI_Bot";

        if (args.length >= 1) {
            aiName = args[0];
        }

        AIClient ai = new AIClient(host, port, aiName);
        ai.start();
    }

    public void start() {
        if (connectToServer()) {
            run();
        }
    }

    private boolean connectToServer() {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            running = true;

            System.out.println("[AI " + playerName + "] Connected to server");

            // Send HELLO
            Hello hello = new Hello(playerName, new Feature[0]);
            sendMessage(hello.transformToProtocolString());

            return true;
        } catch (IOException e) {
            System.out.println("[AI " + playerName + "] Could not connect to server");
            return false;
        }
    }

    private void run() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                handleMessage(message.trim());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[AI " + playerName + "] Connection lost");
            }
        }
    }

    /**
     * Processes messages from the server
     * NOTE: Now also STOCK and TABLE messages for smart moves!
     */
    private void handleMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        String[] parts = message.split("~");
        if (parts.length == 0) {
            return;
        }

        String command = parts[0];

        if (command.equals("START")) {
            System.out.println("[AI " + playerName + "] Game starts!");

        } else if (command.equals("HAND")) {
            // Update hand
            if (parts.length >= 2) {
                updateHand(parts[1]);
            }

        } else if (command.equals("STOCK")) {
            // STOCK~PLAYER~CARD
            // If it's my stock, remember the top card
            if (parts.length >= 3 && parts[1].equals(playerName)) {
                stockTopCard = parts[2];
                System.out.println("[AI " + playerName + "] Stock top: " + stockTopCard);
            }

        } else if (command.equals("TABLE")) {
            // TABLE message contains building pile info
            updateTableInfo(parts);

        } else if (command.equals("TURN")) {
            if (parts.length >= 2) {
                if (parts[1].equals(playerName)) {
                    myTurn = true;
                    System.out.println("[AI " + playerName + "] My turn!");
                    playTurn();
                } else {
                    myTurn = false;
                }
            }

        } else if (command.equals("WINNER")) {
            System.out.println("[AI " + playerName + "] Game over!");
            running = false;

        } else if (command.equals("ROUND")) {
            // New round started
            if (parts.length >= 2) {
                System.out.println("[AI " + playerName + "] New round: " + parts[1]);
            }
        }
    }

    /**
     * Updates AI's hand
     */
    private void updateHand(String handData) {
        hand.clear();
        String[] cards = handData.split(",");
        for (String card : cards) {
            hand.add(card);
        }
        System.out.println("[AI " + playerName + "] Hand updated: " + hand.size() + " cards");
    }

    /**
     * NEW: Parse TABLE message to get building pile info
     * TABLE protocol: TABLE~players~B.0~B.1~B.2~B.3~...
     *
     * Building pile values:
     * - "1" to "12" = expects that card
     * - "X" = full (12 cards) or empty
     */
    private void updateTableInfo(String[] parts) {
        // TABLE has at least: TABLE~players~B.0~B.1~B.2~B.3
        if (parts.length >= 6) {
            buildingPileNext[0] = parts[2];  // B.0
            buildingPileNext[1] = parts[3];  // B.1
            buildingPileNext[2] = parts[4];  // B.2
            buildingPileNext[3] = parts[5];  // B.3

            System.out.println("[AI " + playerName + "] Building piles: " +
                              String.join(", ", buildingPileNext));
        }
    }

    /**
     * SMART AI STRATEGY
     * 1. Try to play stock pile (priority!)
     * 2. Only to building piles that accept the card
     * 3. Stop if no valid moves
     * 4. Discard to end turn
     */
    private void playTurn() {
        try {
            Thread.sleep(1000);  // Wait a moment (for realism)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Try stock pile moves (1-3 times)
        int movesPlayed = 0;
        int maxMoves = random.nextInt(3) + 1;

        for (int i = 0; i < maxMoves && stockTopCard != null; i++) {
            // Find a building pile that accepts our stock card
            int validPile = findValidBuildingPile(stockTopCard);

            if (validPile >= 0) {
                // Valid move found!
                String move = "PLAY~S~B." + validPile;
                sendMessage(move);
                System.out.println("[AI " + playerName + "] Smart stock move: " + move);
                movesPlayed++;

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                // No valid pile for stock card, stop trying
                System.out.println("[AI " + playerName + "] No valid move for stock card " +
                                  stockTopCard);
                break;
            }
        }

        // Discard to end turn (mandatory)
        if (!hand.isEmpty()) {
            String card = hand.get(0);  // Take first card
            int discardPile = random.nextInt(4);

            String discardMove = "PLAY~H." + card + "~D." + discardPile;
            sendMessage(discardMove);
            System.out.println("[AI " + playerName + "] Discard: " + discardMove);

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // End turn
        sendMessage("END");
        System.out.println("[AI " + playerName + "] END command sent");
        myTurn = false;
    }

    /**
     * NEW: Finds a building pile that accepts the card
     *
     * @param cardStr The card ("1" to "12" or "SB" for Skip-Bo)
     * @return Index of valid pile (0-3), or -1 if no valid pile
     */
    private int findValidBuildingPile(String cardStr) {
        if (buildingPileNext == null || cardStr == null) {
            return -1;
        }

        // Skip-Bo can go on any pile (except full piles)
        if (cardStr.equals("SB")) {
            for (int i = 0; i < 4; i++) {
                if (buildingPileNext[i] != null && !buildingPileNext[i].equals("X")) {
                    return i;  // First non-full pile
                }
            }
            return -1;
        }

        // Normal card: find pile that expects this card
        try {
            int cardNum = Integer.parseInt(cardStr);

            for (int i = 0; i < 4; i++) {
                String expected = buildingPileNext[i];
                if (expected == null || expected.equals("X")) {
                    continue;  // Pile is full or empty
                }

                int expectedNum = Integer.parseInt(expected);
                if (cardNum == expectedNum) {
                    return i;  // Found!
                }
            }
        } catch (NumberFormatException e) {
            // Invalid card format
            return -1;
        }

        return -1;  // No valid pile found
    }

    /**
     * Sends a message to the server
     */
    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}
