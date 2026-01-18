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
 * Slimme AI client die alleen valide moves doet
 * De AI weet:
 * - Wat de building piles verwachten (1-12 of X als vol)
 * - Wat zijn stock top card is
 * - Welke moves geldig zijn
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

    // NIEUW: Smart AI tracking
    private String stockTopCard;           // Wat is mijn stock top card?
    private String[] buildingPileNext;     // Wat verwacht elke building pile? (1-12 of X)

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

            System.out.println("[AI " + playerName + "] Verbonden met server");

            // Stuur HELLO
            Hello hello = new Hello(playerName, new Feature[0]);
            sendMessage(hello.transformToProtocolString());

            return true;
        } catch (IOException e) {
            System.out.println("[AI " + playerName + "] Kon niet verbinden met server");
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
                System.err.println("[AI " + playerName + "] Verbinding verloren");
            }
        }
    }

    /**
     * Verwerkt berichten van de server
     * LET OP: Nu ook STOCK en TABLE berichten voor slimme moves!
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
            System.out.println("[AI " + playerName + "] Game start!");

        } else if (command.equals("HAND")) {
            // Update hand
            if (parts.length >= 2) {
                updateHand(parts[1]);
            }

        } else if (command.equals("STOCK")) {
            // STOCK~PLAYER~CARD
            // Als het mijn stock is, onthoud de top card
            if (parts.length >= 3 && parts[1].equals(playerName)) {
                stockTopCard = parts[2];
                System.out.println("[AI " + playerName + "] Stock top: " + stockTopCard);
            }

        } else if (command.equals("TABLE")) {
            // TABLE bericht bevat building pile info
            updateTableInfo(parts);

        } else if (command.equals("TURN")) {
            if (parts.length >= 2) {
                if (parts[1].equals(playerName)) {
                    myTurn = true;
                    System.out.println("[AI " + playerName + "] Mijn beurt!");
                    playTurn();
                } else {
                    myTurn = false;
                }
            }

        } else if (command.equals("WINNER")) {
            System.out.println("[AI " + playerName + "] Game over!");
            running = false;

        } else if (command.equals("ROUND")) {
            // Nieuwe ronde gestart
            if (parts.length >= 2) {
                System.out.println("[AI " + playerName + "] Nieuwe ronde: " + parts[1]);
            }
        }
    }

    /**
     * Update hand van AI
     */
    private void updateHand(String handData) {
        hand.clear();
        String[] cards = handData.split(",");
        for (String card : cards) {
            hand.add(card);
        }
        System.out.println("[AI " + playerName + "] Hand updated: " + hand.size() + " kaarten");
    }

    /**
     * NIEUW: Parse TABLE bericht om building pile info te krijgen
     * TABLE protocol: TABLE~players~B.0~B.1~B.2~B.3~...
     *
     * Building pile waarden:
     * - "1" tot "12" = verwacht die kaart
     * - "X" = vol (12 kaarten) of leeg
     */
    private void updateTableInfo(String[] parts) {
        // TABLE heeft minimaal: TABLE~players~B.0~B.1~B.2~B.3
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
     * SLIMME AI STRATEGIE
     * 1. Probeer stock pile te spelen (prioriteit!)
     * 2. Alleen naar building piles die de kaart accepteren
     * 3. Stop als geen valide moves
     * 4. Discard om beurt te eindigen
     */
    private void playTurn() {
        try {
            Thread.sleep(1000);  // Wacht even (voor realisme)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Probeer stock pile moves (1-3 keer)
        int movesPlayed = 0;
        int maxMoves = random.nextInt(3) + 1;

        for (int i = 0; i < maxMoves && stockTopCard != null; i++) {
            // Zoek een building pile die onze stock card accepteert
            int validPile = findValidBuildingPile(stockTopCard);

            if (validPile >= 0) {
                // Valide move gevonden!
                String move = "PLAY~S~B." + validPile;
                sendMessage(move);
                System.out.println("[AI " + playerName + "] Slimme stock move: " + move);
                movesPlayed++;

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                // Geen valide pile voor stock card, stop proberen
                System.out.println("[AI " + playerName + "] Geen valide move voor stock card " +
                                  stockTopCard);
                break;
            }
        }

        // Discard om beurt te eindigen (verplicht)
        if (!hand.isEmpty()) {
            String card = hand.get(0);  // Neem eerste kaart
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

        // Beëindig beurt
        sendMessage("END");
        System.out.println("[AI " + playerName + "] END command gestuurd");
        myTurn = false;
    }

    /**
     * NIEUW: Zoekt een building pile die de kaart accepteert
     *
     * @param cardStr De kaart ("1" tot "12" of "SB" voor Skip-Bo)
     * @return Index van valide pile (0-3), of -1 als geen valide pile
     */
    private int findValidBuildingPile(String cardStr) {
        if (buildingPileNext == null || cardStr == null) {
            return -1;
        }

        // Skip-Bo kan op elke pile (behalve volle piles)
        if (cardStr.equals("SB")) {
            for (int i = 0; i < 4; i++) {
                if (buildingPileNext[i] != null && !buildingPileNext[i].equals("X")) {
                    return i;  // Eerste niet-volle pile
                }
            }
            return -1;
        }

        // Normale kaart: zoek pile die deze kaart verwacht
        try {
            int cardNum = Integer.parseInt(cardStr);

            for (int i = 0; i < 4; i++) {
                String expected = buildingPileNext[i];
                if (expected == null || expected.equals("X")) {
                    continue;  // Pile is vol of leeg
                }

                int expectedNum = Integer.parseInt(expected);
                if (cardNum == expectedNum) {
                    return i;  // Gevonden!
                }
            }
        } catch (NumberFormatException e) {
            // Ongeldige kaart format
            return -1;
        }

        return -1;  // Geen valide pile gevonden
    }

    /**
     * Stuurt een bericht naar de server
     */
    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}
