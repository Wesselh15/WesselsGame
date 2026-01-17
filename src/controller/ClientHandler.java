package controller;

import protocol.Command;
import protocol.common.position.*;
import protocol.common.Card;

import java.io.*;
import java.net.*;

/**
 * Handles communication with one connected client
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Server server;
    private String clientName;
    private boolean running;

    public ClientHandler(Socket socket, Server server){
        this.socket = socket;
        this.server = server;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while (running && (message = in.readLine()) != null) {
                handleClientMessage(message.trim());
            }
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleClientMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        System.out.println("Received from " + clientName + ": " + message);

        // Split message into parts
        String[] parts = message.split(Command.SEPERATOR);
        if (parts.length == 0) {
            return;
        }

        String command = parts[0];
        GameManager gameManager = server.getGameManager();

        // Handle each command type
        if (command.equals("HELLO")) {
            if (parts.length >= 2) {
                String playerName = parts[1];
                this.clientName = playerName;
                gameManager.addPlayer(playerName, this);
            }
        } else if (command.equals("GAME")) {
            if (parts.length >= 2) {
                int numPlayers = Integer.parseInt(parts[1]);
                gameManager.setRequiredPlayers(numPlayers, this);
            }
        } else if (command.equals("PLAY")) {
            if (parts.length >= 3 && clientName != null) {
                Position from = parsePosition(parts[1]);
                Position to = parsePosition(parts[2]);
                if (from != null && to != null) {
                    gameManager.handleMove(clientName, from, to);
                }
            }
        } else if (command.equals("TABLE")) {
            if (clientName != null) {
                gameManager.sendTableToPlayer(clientName);
            }
        } else if (command.equals("HAND")) {
            if (clientName != null) {
                gameManager.sendHandToPlayer(clientName);
            }
        } else {
            System.out.println("Unknown command: " + command);
        }
    }

    private Position parsePosition(String posStr) {
        try {
            // Split position string by '.'
            String[] parts = posStr.split("\\.");
            if (parts.length == 0) {
                return null;
            }

            String type = parts[0];

            // Hand position: H.5 or H.SB
            if (type.equals("H")) {
                if (parts.length >= 2) {
                    if (parts[1].equals("X")) {
                        return new HandPosition(null);
                    } else if (parts[1].equals("SB")) {
                        return new HandPosition(new Card());
                    } else {
                        int cardNumber = Integer.parseInt(parts[1]);
                        return new HandPosition(new Card(cardNumber));
                    }
                }
            }

            // Stock pile position: S
            if (type.equals("S")) {
                return new StockPilePosition();
            }

            // Building pile: B.0
            if (type.equals("B")) {
                if (parts.length >= 2) {
                    int pileNumber = Integer.parseInt(parts[1]);
                    return new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, pileNumber);
                }
            }

            // Discard pile: D.0
            if (type.equals("D")) {
                if (parts.length >= 2) {
                    int pileNumber = Integer.parseInt(parts[1]);
                    return new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, pileNumber);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing position: " + e.getMessage());
        }

        return null;
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void cleanup() {
        running = false;

        // Tell the game manager this player disconnected
        if (clientName != null) {
            server.getGameManager().removePlayer(clientName);
        }

        // Tell the server this client disconnected
        server.removeClient(this);

        // Close all connections
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error cleaning up: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
    }
}
