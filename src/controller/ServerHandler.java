package controller;

import protocol.Command;

import java.io.BufferedReader;
import java.io.IOException;

public class ServerHandler implements Runnable {
    private BufferedReader in;
    private Client client;
    private boolean running;



    public ServerHandler(BufferedReader in, Client client) {
        this.in = in;
        this.client = client;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            String message;
            // Keep reading messages until connection is lost or we stop
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

        switch (command) {
            case "WELCOME":
                if (parts.length >= 2) {
                    System.out.println(">>> Player joined: " + parts[1]);
                }
                break;

            case "START":
                if (parts.length >= 2) {
                    String[] players = parts[1].split(Command.LIST_SEPERATOR);
                    System.out.println(">>> Game starting with players: " + String.join(", ", players));
                }
                break;

            case "TABLE":
                System.out.println(">>> Table state updated");
                displayTable(message);
                break;

            case "HAND":
                System.out.println(">>> Your hand updated");
                displayHand(message);
                break;

            case "TURN":
                if (parts.length >= 2) {
                    System.out.println(">>> It's " + parts[1] + "'s turn");
                }
                break;

            case "PLAY":
                if (parts.length >= 4) {
                    System.out.println(">>> " + parts[1] + " played: " + parts[2] + " -> " + parts[3]);
                }
                break;

            case "STOCK":
                if (parts.length >= 3) {
                    System.out.println(">>> " + parts[1] + "'s stock top card: " + parts[2]);
                }
                break;

            case "ROUND":
                System.out.println(">>> Round ended!");
                if (parts.length >= 2) {
                    displayScores(parts[1]);
                }
                break;

            case "WINNER":
                System.out.println(">>> GAME OVER!");
                if (parts.length >= 2) {
                    displayWinner(parts[1]);
                }
                break;

            case "ERROR":
                if (parts.length >= 2) {
                    System.out.println(">>> ERROR: " + parts[1]);
                }
                break;

            default:
                System.out.println(">>> " + message);
                break;
        }
    }

    private void displayTable(String message) {
        String[] parts = message.split(Command.SEPERATOR);
        if (parts.length < 2) {
            return;
        }

        String tableData = parts[1];
        String[] piles = tableData.split(Command.LIST_SEPERATOR);

        for (String pile : piles) {
            String[] pileInfo = pile.split("\\" + Command.VALUE_SEPERATOR);
            if (pileInfo.length >= 2) {
                String pileName = pileInfo[0];
                String pileType = pileInfo[1];
                System.out.println("  " + pileName + " (" + pileType + ")");
            }
        }
    }

    private void displayHand(String message) {
        String[] parts = message.split(Command.SEPERATOR);
        if (parts.length < 2) {
            return;
        }

        String handData = parts[1];
        String[] cards = handData.split(Command.LIST_SEPERATOR);
        System.out.println("  Your hand: " + String.join(", ", cards));
    }

    private void displayScores(String scoreData) {
        String[] scores = scoreData.split(Command.LIST_SEPERATOR);
        for (String score : scores) {
            String[] playerScore = score.split("\\" + Command.VALUE_SEPERATOR);
            if (playerScore.length >= 2) {
                System.out.println("  " + playerScore[0] + ": " + playerScore[1] + " points");
            }
        }
    }

    private void displayWinner(String winnerData) {
        String[] scores = winnerData.split(Command.LIST_SEPERATOR);
        for (String score : scores) {
            String[] playerScore = score.split("\\" + Command.VALUE_SEPERATOR);
            if (playerScore.length >= 2) {
                System.out.println("  " + playerScore[0] + ": " + playerScore[1] + " points");
            }
        }
    }

    public void stop() {
        running = false;
    }
}
