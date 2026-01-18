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

        // Handle each command type using switch (cleaner for protocol)
        if (command.equals("HELLO")) {
            // Protocol: HELLO~NAME~FEATURES
            if (parts.length >= 2) {
                String playerName = parts[1];

                // Validate player name: 1-30 chars, only [A-Za-z0-9_-]
                if (!isValidPlayerName(playerName)) {
                    String errorMsg = new protocol.server.Error(protocol.common.ErrorCode.INVALID_PLAYER_NAME).transformToProtocolString();
                    sendMessage(errorMsg);
                    return;
                }

                // Extract and validate features (optional, default empty)
                String features = "";
                if (parts.length >= 3) {
                    features = parts[2];
                    if (!isValidFeatures(features)) {
                        String errorMsg = new protocol.server.Error(protocol.common.ErrorCode.INVALID_COMMAND).transformToProtocolString();
                        sendMessage(errorMsg);
                        return;
                    }
                }

                this.clientName = playerName;
                gameManager.addPlayer(playerName, features, this);
            }
        } else if (command.equals("GAME")) {
            // Protocol: GAME~AMOUNT
            if (parts.length >= 2) {
                try {
                    int numPlayers = Integer.parseInt(parts[1]);
                    gameManager.setRequiredPlayers(numPlayers, this);
                } catch (NumberFormatException e) {
                    // Invalid number format -> ERROR~204
                    String errorMsg = new protocol.server.Error(protocol.common.ErrorCode.INVALID_COMMAND).transformToProtocolString();
                    sendMessage(errorMsg);
                }
            } else {
                // Missing parameter -> ERROR~204
                String errorMsg = new protocol.server.Error(protocol.common.ErrorCode.INVALID_COMMAND).transformToProtocolString();
                sendMessage(errorMsg);
            }
        } else if (command.equals("PLAY")) {
            // Protocol: PLAY~FROM~TO
            if (clientName == null) {
                // Not logged in yet
                String errorMsg = new protocol.server.Error(protocol.common.ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
                sendMessage(errorMsg);
            } else if (parts.length < 3) {
                // Missing parameters -> ERROR~204 (INVALID_COMMAND)
                String errorMsg = new protocol.server.Error(protocol.common.ErrorCode.INVALID_COMMAND).transformToProtocolString();
                sendMessage(errorMsg);
            } else {
                Position from = parsePosition(parts[1]);
                Position to = parsePosition(parts[2]);

                if (from == null || to == null) {
                    // Cannot parse positions -> ERROR~204 (INVALID_COMMAND)
                    String errorMsg = new protocol.server.Error(protocol.common.ErrorCode.INVALID_COMMAND).transformToProtocolString();
                    sendMessage(errorMsg);
                } else {
                    // Valid syntax, let GameManager handle the move
                    // GameManager will send ERROR~205 if not your turn
                    // GameManager will send ERROR~206 if invalid move
                    gameManager.handleMove(clientName, from, to);
                }
            }
        } else if (command.equals("END")) {
            // Protocol: END command ends the current player's turn
            if (clientName != null) {
                gameManager.endTurn(clientName);
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
            // Unknown command - send ERROR~204 (INVALID_COMMAND)
            System.out.println("Unknown command: " + command);
            String errorMsg = new protocol.server.Error(protocol.common.ErrorCode.INVALID_COMMAND).transformToProtocolString();
            sendMessage(errorMsg);
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

    /**
     * Validates player name according to protocol:
     * - Length: 1-30 characters
     * - Characters: only A-Z, a-z, 0-9, underscore, hyphen
     */
    private boolean isValidPlayerName(String name) {
        if (name == null || name.isEmpty() || name.length() > 30) {
            return false;
        }
        // Check if all characters are valid: [A-Za-z0-9_-]
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean isValid = (c >= 'A' && c <= 'Z') ||
                            (c >= 'a' && c <= 'z') ||
                            (c >= '0' && c <= '9') ||
                            c == '_' || c == '-';
            if (!isValid) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates features according to protocol:
     * - Only letters C, L, M allowed
     * - Must be in alphabetical order (e.g., CLM is ok, CML is not)
     */
    private boolean isValidFeatures(String features) {
        if (features == null) {
            return true; // Empty features is ok
        }
        if (features.isEmpty()) {
            return true;
        }

        // Check if all characters are C, L, or M
        for (int i = 0; i < features.length(); i++) {
            char c = features.charAt(i);
            if (c != 'C' && c != 'L' && c != 'M') {
                return false;
            }
        }

        // Check if alphabetically sorted
        for (int i = 0; i < features.length() - 1; i++) {
            if (features.charAt(i) > features.charAt(i + 1)) {
                return false; // Not in alphabetical order
            }
        }

        return true;
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
