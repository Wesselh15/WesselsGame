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
 * Simple AI client that plays random valid moves
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

    private List<String> hand;
    private boolean myTurn;

    public AIClient(String host, int port, String playerName) {
        this.host = host;
        this.port = port;
        this.playerName = playerName;
        this.random = new Random();
        this.hand = new ArrayList<>();
        this.myTurn = false;
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
            System.out.println("[AI " + playerName + "] Game starting!");
        } else if (command.equals("HAND")) {
            if (parts.length >= 2) {
                updateHand(parts[1]);
            }
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
        }
    }

    private void updateHand(String handData) {
        hand.clear();
        String[] cards = handData.split(",");
        for (String card : cards) {
            hand.add(card);
        }
        System.out.println("[AI " + playerName + "] Hand updated: " + hand.size() + " cards");
    }

    private void playTurn() {
        // Wait a bit to simulate thinking
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[AI] Thinking interrupted");
        }

        // Try to play a few random cards
        int movesPlayed = 0;
        int maxMoves = random.nextInt(3) + 1; // Play 1-3 moves

        for (int i = 0; i < maxMoves && !hand.isEmpty(); i++) {
            // Pick random card from hand
            int cardIndex = random.nextInt(hand.size());
            String card = hand.get(cardIndex);

            // Try random building pile
            int buildingPile = random.nextInt(4);

            String move = "PLAY~H." + card + "~B." + buildingPile;
            sendMessage(move);
            System.out.println("[AI " + playerName + "] Attempting move: " + move);

            movesPlayed++;

            // Wait a bit between moves
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[AI] Move delay interrupted");
            }
        }

        // End turn by discarding
        if (!hand.isEmpty()) {
            int cardIndex = random.nextInt(hand.size());
            String card = hand.get(cardIndex);
            int discardPile = random.nextInt(4);

            String discardMove = "PLAY~H." + card + "~D." + discardPile;
            sendMessage(discardMove);
            System.out.println("[AI " + playerName + "] Discarding: " + discardMove);

            // Wait a bit before sending END
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[AI] End delay interrupted");
            }

            // Protocol: After discard, must send END command to end turn
            sendMessage("END");
            System.out.println("[AI " + playerName + "] Sent END command");
        }

        myTurn = false;
    }

    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}
