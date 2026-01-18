package controller;

import model.*;
import protocol.server.*;
import protocol.common.ErrorCode;
import protocol.common.Feature;
import protocol.common.position.*;

import java.util.ArrayList;
import java.util.List;

import static model.GameConstants.*;

/**
 * GameManager manages the lobby and game lifecycle
 * SIMPLE: Only adds players, starts game, ends game
 * GAME LOGIC is in GameController!
 */
public class GameManager {
    private Game game;
    private Server server;
    private GameController gameController;  // New: handles the game logic

    // Lobby tracking
    private List<String> playerNames;
    private List<ClientHandler> playerClients;
    private int requiredPlayers;

    public GameManager(Server server) {
        this.server = server;
        this.playerNames = new ArrayList<>();
        this.playerClients = new ArrayList<>();
        this.requiredPlayers = -1;  // Not yet set
    }

    /**
     * Adds a player to the lobby
     * Protocol: HELLO~NAME~FEATURES -> WELCOME~NAME~FEATURES
     */
    public void addPlayer(String playerName, String featuresStr, ClientHandler client) {
        // Check: game already started?
        if (game != null) {
            sendErrorToClient(client, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Check: name already in use?
        for (String existingName : playerNames) {
            if (existingName.equals(playerName)) {
                sendErrorToClient(client, ErrorCode.NAME_IN_USE);
                return;
            }
        }

        // Add player to lobby
        playerNames.add(playerName);
        playerClients.add(client);

        // Parse features
        Feature[] features = parseFeatures(featuresStr);

        // Send WELCOME (only to this client)
        String welcomeMsg = new Welcome(playerName, features).transformToProtocolString();
        client.sendMessage(welcomeMsg);

        System.out.println("Player added: " + playerName +
                          " (" + playerNames.size() + "/" + requiredPlayers + ")");

        // Check if we have enough players to start
        if (requiredPlayers > 0 && playerNames.size() >= requiredPlayers) {
            System.out.println("Enough players! Game starts automatically...");
            startGame();
        } else if (requiredPlayers > 0) {
            System.out.println("Waiting for " + (requiredPlayers - playerNames.size()) +
                              " more player(s)");
        } else {
            System.out.println("Waiting for GAME~AMOUNT command");
        }
    }

    /**
     * Sets the required number of players
     * Protocol: GAME~AMOUNT -> QUEUE or START
     */
    public void setRequiredPlayers(int count, ClientHandler requestingClient) {
        // Check: game already started?
        if (game != null) {
            sendErrorToClient(requestingClient, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Check: already set?
        if (requiredPlayers != -1) {
            System.out.println("Required players already set: " + requiredPlayers);
            sendErrorToClient(requestingClient, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Validate: 2-6 players
        if (count < MIN_PLAYERS || count > MAX_PLAYERS) {
            sendErrorToClient(requestingClient, ErrorCode.INVALID_COMMAND);
            return;
        }

        this.requiredPlayers = count;
        System.out.println("Game starts with " + requiredPlayers + " players");

        // Check if we have enough players
        if (playerNames.size() >= requiredPlayers) {
            startGame();
        } else {
            // Send QUEUE message (not enough players yet)
            String queueMsg = new Queue().transformToProtocolString();
            server.broadcast(queueMsg);
            System.out.println("Waiting for more players: " + playerNames.size() +
                              "/" + requiredPlayers);
        }
    }

    /**
     * Starts the game!
     * Creates Game object and GameController
     */
    private void startGame() {
        if (game != null) {
            return;  // Already started
        }

        System.out.println("Game starts with " + playerNames.size() + " players");

        // Create Player objects
        List<Player> players = new ArrayList<>();
        for (String name : playerNames) {
            players.add(new Player(name));
        }

        // Create Game
        game = new Game(players);

        // Create GameController (handles all game logic!)
        gameController = new GameController(game, server, playerNames, playerClients);

        // Send START message
        String[] names = playerNames.toArray(new String[0]);
        String startMsg = new Start(names).transformToProtocolString();
        server.broadcast(startMsg);

        // Send initial game state (via GameController)
        gameController.sendGameStateToAll();

        // Send all stock pile top cards
        for (Player player : players) {
            gameController.sendStockTopCard(player);
        }

        // Announce who starts
        Player currentPlayer = game.getCurrentPlayer();
        String turnMsg = new Turn(currentPlayer.getName()).transformToProtocolString();
        server.broadcast(turnMsg);

        System.out.println("Game started! " + currentPlayer.getName() + " begins.");
    }

    /**
     * Processes a move (PLAY command)
     * Delegates to GameController
     */
    public void handleMove(String playerName, Position from, Position to) {
        if (game == null || gameController == null) {
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        gameController.handleMove(playerName, from, to);
    }

    /**
     * Ends a turn (END command)
     * Delegates to GameController
     */
    public void endTurn(String playerName) {
        if (game == null || gameController == null) {
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        gameController.endTurn(playerName);
    }

    /**
     * Sends TABLE to a player (TABLE command)
     * Delegates to GameController
     */
    public void sendTableToPlayer(String playerName) {
        if (game == null || gameController == null) {
            return;
        }

        gameController.sendTableToPlayer(playerName);
    }

    /**
     * Sends HAND to a player (HAND command)
     * Delegates to GameController
     */
    public void sendHandToPlayer(String playerName) {
        if (game == null || gameController == null) {
            return;
        }

        gameController.sendHandToPlayer(playerName);
    }

    /**
     * Removes a player (disconnect)
     * Protocol: Broadcast ERROR~103 and end game
     */
    public void removePlayer(String playerName) {
        // Remove from lists
        for (int i = 0; i < playerNames.size(); i++) {
            if (playerNames.get(i).equals(playerName)) {
                playerNames.remove(i);
                playerClients.remove(i);
                break;
            }
        }

        // If game in progress: end game (prevents bugs)
        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.PLAYER_DISCONNECTED)
                                .transformToProtocolString();
            server.broadcast(errorMsg);

            System.out.println("Game ended due to disconnect: " + playerName);
            game = null;
            gameController = null;
            requiredPlayers = -1;  // Reset for new game
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Parses features string (e.g. "CLM") to Feature array
     * C = CHAT, L = LOBBY, M = MASTER
     */
    private Feature[] parseFeatures(String featuresStr) {
        if (featuresStr == null || featuresStr.isEmpty()) {
            return new Feature[0];
        }

        Feature[] result = new Feature[featuresStr.length()];
        for (int i = 0; i < featuresStr.length(); i++) {
            char c = featuresStr.charAt(i);
            if (c == 'C') {
                result[i] = Feature.CHAT;
            } else if (c == 'L') {
                result[i] = Feature.LOBBY;
            } else if (c == 'M') {
                result[i] = Feature.MASTER;
            }
        }
        return result;
    }

    /**
     * Finds a ClientHandler by name
     */
    private ClientHandler getClientByName(String name) {
        for (int i = 0; i < playerNames.size(); i++) {
            if (playerNames.get(i).equals(name)) {
                return playerClients.get(i);
            }
        }
        return null;
    }

    /**
     * Sends error to a player
     */
    private void sendErrorToPlayer(String playerName, ErrorCode errorCode) {
        ClientHandler client = getClientByName(playerName);
        if (client != null) {
            String errorMsg = new protocol.server.Error(errorCode)
                                .transformToProtocolString();
            client.sendMessage(errorMsg);
        }
    }

    /**
     * Sends error to a client directly
     */
    private void sendErrorToClient(ClientHandler client, ErrorCode errorCode) {
        if (client != null) {
            String errorMsg = new protocol.server.Error(errorCode)
                                .transformToProtocolString();
            client.sendMessage(errorMsg);
        }
    }
}
