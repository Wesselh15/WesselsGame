package view;

import model.*;
import java.util.List;

/**
 * Simple text-based view for displaying the game state
 */
public class GameView {

    public void showWelcome() {
        System.out.println("=================================");
        System.out.println("    SKIP-BO MULTIPLAYER GAME    ");
        System.out.println("=================================");
        System.out.println();
    }

    public void showCommands() {
        System.out.println("\nAvailable commands:");
        System.out.println("  GAME~2       - Start game with 2 players");
        System.out.println("  TABLE        - View the game table");
        System.out.println("  HAND         - View your hand");
        System.out.println("  PLAY~H.5~B.0 - Play card 5 from hand to building pile 0");
        System.out.println("  PLAY~S~B.1   - Play from stock pile to building pile 1");
        System.out.println("  PLAY~H.3~D.0 - Discard card 3 (ends your turn)");
        System.out.println("  quit         - Exit game");
        System.out.println();
    }

    public void showPlayerJoined(String playerName) {
        System.out.println(">>> Player joined: " + playerName);
    }

    public void showGameStarting(String[] players) {
        System.out.println("\n>>> GAME STARTING! <<<");
        System.out.print("Players: ");
        for (int i = 0; i < players.length; i++) {
            System.out.print(players[i]);
            if (i < players.length - 1) {
                System.out.print(", ");
            }
        }
        System.out.println("\n");
    }

    public void showTurn(String playerName) {
        System.out.println("\n>>> It's " + playerName + "'s turn <<<");
    }

    public void showHand(String[] cards) {
        System.out.println("\n--- YOUR HAND ---");
        if (cards.length == 0) {
            System.out.println("  (empty)");
        } else {
            System.out.print("  Cards: ");
            for (int i = 0; i < cards.length; i++) {
                System.out.print(cards[i]);
                if (i < cards.length - 1) {
                    System.out.print(", ");
                }
            }
            System.out.println();
        }
        System.out.println();
    }

    public void showMove(String playerName, String from, String to) {
        System.out.println(">>> " + playerName + " played: " + from + " -> " + to);
    }

    public void showError(String errorCode) {
        System.out.println(">>> ERROR: " + errorCode);
    }

    public void showWinner(String winnerName, int score) {
        System.out.println("\n=================================");
        System.out.println("       GAME OVER!");
        System.out.println("  WINNER: " + winnerName);
        System.out.println("  SCORE: " + score);
        System.out.println("=================================");
    }

    public void showMessage(String message) {
        System.out.println(">>> " + message);
    }
}
