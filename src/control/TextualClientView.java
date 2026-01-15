package control;

import java.util.Scanner;

/**
 * Textual (console-based) implementation of ClientView.
 * Contains all UI logic for displaying game state and reading user input.
 * Can be moved to a separate view package in the future.
 */
public class TextualClientView implements ClientView {
    private Scanner scanner;

    public TextualClientView() {
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void showConnected(String host, int port) {
        System.out.println("Connected to server at " + host + ":" + port);
    }

    @Override
    public void showConnectionError(String message) {
        System.err.println("Could not connect to server: " + message);
    }

    @Override
    public void showDisconnectionError(String message) {
        System.err.println("Error disconnecting: " + message);
    }

    @Override
    public void showConnectionLost(String message) {
        System.err.println("Connection lost: " + message);
    }

    @Override
    public void showUnknownMessage(String message) {
        System.out.println("Unknown server message: " + message);
    }

    @Override
    public void showMessageHandlingError(String message) {
        System.err.println("Error handling server message: " + message);
    }

    @Override
    public void showPlayerJoined(String playerName) {
        System.out.println("Player '" + playerName + "' has joined the server");
    }

    @Override
    public void showAddedToQueue() {
        System.out.println("You have been added to the game queue. Waiting for other players...");
    }

    @Override
    public void showGameStarted(String[] playerNames) {
        System.out.println("\n=== GAME STARTED ===");
        System.out.println("Players: " + String.join(", ", playerNames));
        System.out.println("====================\n");
    }

    @Override
    public void showTurn(String playerName, boolean isYourTurn) {
        System.out.println("\n--- It's " + playerName + "'s turn ---");
        if (isYourTurn) {
            System.out.println("It's YOUR turn!");
            showHelp();
        }
    }

    @Override
    public void showHand(String[] cards) {
        System.out.println("Your hand: " + String.join(", ", cards));
    }

    @Override
    public void showStockPile(String playerName, String topCard) {
        System.out.println(playerName + "'s stock pile top card: " + topCard);
    }

    @Override
    public void showTableState(String[] buildingPiles, String[][] playerDiscardPiles) {
        System.out.println("\n=== TABLE STATE ===");

        // Building piles
        System.out.println("Building Piles: " +
            "1:" + buildingPiles[0] + " " +
            "2:" + buildingPiles[1] + " " +
            "3:" + buildingPiles[2] + " " +
            "4:" + buildingPiles[3]);

        // Player discard piles
        for (String[] playerPiles : playerDiscardPiles) {
            if (playerPiles.length >= 5) {
                System.out.println(playerPiles[0] + "'s discard piles: " +
                    "1:" + playerPiles[1] + " " +
                    "2:" + playerPiles[2] + " " +
                    "3:" + playerPiles[3] + " " +
                    "4:" + playerPiles[4]);
            }
        }
        System.out.println("==================\n");
    }

    @Override
    public void showPlay(String playerName, String from, String to) {
        System.out.println(playerName + " played: " + from + " -> " + to);
    }

    @Override
    public void showWinner(String[] scores) {
        System.out.println("\n=== GAME OVER ===");
        for (String score : scores) {
            System.out.println(score);
        }
        System.out.println("=================\n");
    }

    @Override
    public void showError(String errorCode, String errorMessage) {
        System.out.println("ERROR " + errorCode + ": " + errorMessage);
    }

    @Override
    public void showInvalidPositionFormat() {
        System.out.println("Invalid position format");
    }

    @Override
    public void showPlaySendError(String message) {
        System.out.println("Error sending play: " + message);
    }

    @Override
    public void showHelp() {
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

    @Override
    public String promptPlayerName() {
        System.out.print("Enter your name: ");
        String name = scanner.nextLine().trim();

        if (name.isEmpty()) {
            System.out.println("Invalid name");
            return null;
        }

        return name;
    }

    @Override
    public int promptNumberOfPlayers() {
        System.out.print("Enter number of players (2-6): ");
        try {
            int numPlayers = Integer.parseInt(scanner.nextLine().trim());
            return numPlayers;
        } catch (NumberFormatException e) {
            System.out.println("Invalid number");
            return -1;
        }
    }

    @Override
    public String readCommand() {
        return scanner.nextLine().trim();
    }

    @Override
    public void showUsage(String usage) {
        System.out.println("Usage: " + usage);
    }

    @Override
    public void showUnknownCommand() {
        System.out.println("Unknown command. Type 'help' for help.");
    }

    /**
     * Get error message for error code.
     */
    public String getErrorMessage(String code) {
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
}
