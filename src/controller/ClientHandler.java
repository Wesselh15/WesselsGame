package controller;

import protocol.Command;
import protocol.common.position.*;
import protocol.common.Card;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Server server;
    private String clientName;
    private boolean running; // checks which handlers are still running
    String message;

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

        String[] parts = message.split(Command.SEPERATOR);
        if (parts.length == 0) {
            return;
        }

        String command = parts[0];
        GameManager gameManager = server.getGameManager();

        try {
            switch (command) {
                case "HELLO":
                    if (parts.length >= 2) {
                        String playerName = parts[1];
                        this.clientName = playerName;
                        gameManager.addPlayer(playerName, this);
                    }
                    break;

                case "GAME":
                    if (parts.length >= 2) {
                        int numPlayers = Integer.parseInt(parts[1]);
                        gameManager.setRequiredPlayers(numPlayers, this);
                    }
                    break;

                case "PLAY":
                    if (parts.length >= 3 && clientName != null) {
                        Position from = parsePosition(parts[1]);
                        Position to = parsePosition(parts[2]);
                        if (from != null && to != null) {
                            gameManager.handleMove(clientName, from, to);
                        }
                    }
                    break;

                case "TABLE":
                    if (clientName != null) {
                        gameManager.sendTableToPlayer(clientName);
                    }
                    break;

                case "HAND":
                    if (clientName != null) {
                        gameManager.sendHandToPlayer(clientName);
                    }
                    break;

                case "END":
                    if (clientName != null) {
                        gameManager.handleEndTurn(clientName);
                    }
                    break;

                default:
                    System.out.println("Unknown command: " + command);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Error handling command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Position parsePosition(String posStr) {
        try {
            String[] parts = posStr.split("\\" + Command.VALUE_SEPERATOR);
            if (parts.length == 0) {
                return null;
            }

            String type = parts[0];
            switch (type) {
                case "H":
                    // Hand position: H.CardNumber
                    if (parts.length >= 2) {
                        if (parts[1].equals("X")) {
                            return new HandPosition(null);
                        } else {
                            int cardNumber = Integer.parseInt(parts[1]);
                            return new HandPosition(new Card(cardNumber));
                        }
                    }
                    break;

                case "S":
                    // Stock pile position
                    return new StockPilePosition();

                case "B":
                    // Building pile: B.PileNumber
                    if (parts.length >= 2) {
                        int pileNumber = Integer.parseInt(parts[1]);
                        return new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, pileNumber);
                    }
                    break;

                case "D":
                    // Discard pile: D.PileNumber
                    if (parts.length >= 2) {
                        int pileNumber = Integer.parseInt(parts[1]);
                        return new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, pileNumber);
                    }
                    break;
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

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String name) {
        this.clientName = name;
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