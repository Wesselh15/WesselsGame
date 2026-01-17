package controller;

import protocol.Command;
import view.GameView;

import java.io.BufferedReader;
import java.io.IOException;

public class ServerHandler implements Runnable {
    private BufferedReader in;
    private Client client;
    private GameView view;
    private boolean running;

    public ServerHandler(BufferedReader in, Client client, GameView view) {
        this.in = in;
        this.client = client;
        this.view = view;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                handleServerMessage(message.trim());
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Connection to server lost: " + e.getMessage());
            }
        }
    }

    private void handleServerMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        String[] parts = message.split(Command.SEPERATOR);
        if (parts.length == 0) {
            return;
        }

        String command = parts[0];

        if (command.equals("WELCOME")) {
            if (parts.length >= 2) {
                view.showPlayerJoined(parts[1]);
            }
        } else if (command.equals("START")) {
            if (parts.length >= 2) {
                String[] players = parts[1].split(Command.LIST_SEPERATOR);
                view.showGameStarting(players);
            }
        } else if (command.equals("TURN")) {
            if (parts.length >= 2) {
                view.showTurn(parts[1]);
            }
        } else if (command.equals("HAND")) {
            if (parts.length >= 2) {
                String[] cards = parts[1].split(Command.LIST_SEPERATOR);
                view.showHand(cards);
            }
        } else if (command.equals("PLAY")) {
            if (parts.length >= 4) {
                view.showMove(parts[1], parts[2], parts[3]);
            }
        } else if (command.equals("STOCK")) {
            if (parts.length >= 3) {
                view.showStockTopCard(parts[1], parts[2]);
            }
        } else if (command.equals("ERROR")) {
            if (parts.length >= 2) {
                view.showError(parts[1]);
            }
        } else if (command.equals("WINNER")) {
            if (parts.length >= 2) {
                String[] scores = parts[1].split(Command.LIST_SEPERATOR);
                if (scores.length > 0) {
                    String[] winnerData = scores[0].split("\\" + Command.VALUE_SEPERATOR);
                    if (winnerData.length >= 2) {
                        view.showWinner(winnerData[0], Integer.parseInt(winnerData[1]));
                    }
                }
            }
        } else {
            view.showMessage(message);
        }
    }

    public void stop() {
        running = false;
    }
}
