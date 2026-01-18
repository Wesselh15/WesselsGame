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
 * GameManager beheert de lobby en game lifecycle
 * SIMPEL: Alleen spelers toevoegen, game starten, game eindigen
 * GAME LOGICA zit in GameController!
 */
public class GameManager {
    private Game game;
    private Server server;
    private GameController gameController;  // Nieuwe: doet de game logica

    // Lobby tracking
    private List<String> playerNames;
    private List<ClientHandler> playerClients;
    private int requiredPlayers;

    public GameManager(Server server) {
        this.server = server;
        this.playerNames = new ArrayList<>();
        this.playerClients = new ArrayList<>();
        this.requiredPlayers = -1;  // Nog niet gezet
    }

    /**
     * Voegt een speler toe aan de lobby
     * Protocol: HELLO~NAME~FEATURES -> WELCOME~NAME~FEATURES
     */
    public void addPlayer(String playerName, String featuresStr, ClientHandler client) {
        // Check: game al gestart?
        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED)
                                .transformToProtocolString();
            client.sendMessage(errorMsg);
            return;
        }

        // Check: naam al in gebruik?
        for (String existingName : playerNames) {
            if (existingName.equals(playerName)) {
                String errorMsg = new protocol.server.Error(ErrorCode.NAME_IN_USE)
                                    .transformToProtocolString();
                client.sendMessage(errorMsg);
                return;
            }
        }

        // Voeg speler toe aan lobby
        playerNames.add(playerName);
        playerClients.add(client);

        // Parse features
        Feature[] features = parseFeatures(featuresStr);

        // Stuur WELCOME (alleen naar deze client)
        String welcomeMsg = new Welcome(playerName, features).transformToProtocolString();
        client.sendMessage(welcomeMsg);

        System.out.println("Speler toegevoegd: " + playerName +
                          " (" + playerNames.size() + "/" + requiredPlayers + ")");

        // Check of we genoeg spelers hebben om te starten
        if (requiredPlayers > 0 && playerNames.size() >= requiredPlayers) {
            System.out.println("Genoeg spelers! Game start automatisch...");
            startGame();
        } else if (requiredPlayers > 0) {
            System.out.println("Wachten op " + (requiredPlayers - playerNames.size()) +
                              " meer speler(s)");
        } else {
            System.out.println("Wachten op GAME~AMOUNT command");
        }
    }

    /**
     * Zet het vereiste aantal spelers
     * Protocol: GAME~AMOUNT -> QUEUE of START
     */
    public void setRequiredPlayers(int count, ClientHandler requestingClient) {
        // Check: game al gestart?
        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED)
                                .transformToProtocolString();
            requestingClient.sendMessage(errorMsg);
            return;
        }

        // Check: al gezet?
        if (requiredPlayers != -1) {
            System.out.println("Required players al gezet: " + requiredPlayers);
            String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED)
                                .transformToProtocolString();
            requestingClient.sendMessage(errorMsg);
            return;
        }

        // Valideer: 2-6 spelers
        if (count < MIN_PLAYERS || count > MAX_PLAYERS) {
            String errorMsg = new protocol.server.Error(ErrorCode.INVALID_COMMAND)
                                .transformToProtocolString();
            requestingClient.sendMessage(errorMsg);
            return;
        }

        this.requiredPlayers = count;
        System.out.println("Game start met " + requiredPlayers + " spelers");

        // Check of we genoeg spelers hebben
        if (playerNames.size() >= requiredPlayers) {
            startGame();
        } else {
            // Stuur QUEUE bericht (nog niet genoeg spelers)
            String queueMsg = new Queue().transformToProtocolString();
            server.broadcast(queueMsg);
            System.out.println("Wachten op meer spelers: " + playerNames.size() +
                              "/" + requiredPlayers);
        }
    }

    /**
     * Start het spel!
     * Maakt Game object en GameController
     */
    private void startGame() {
        if (game != null) {
            return;  // Al gestart
        }

        System.out.println("Game start met " + playerNames.size() + " spelers");

        // Maak Player objecten
        List<Player> players = new ArrayList<>();
        for (String name : playerNames) {
            players.add(new Player(name));
        }

        // Maak Game
        game = new Game(players);

        // Maak GameController (doet alle game logica!)
        gameController = new GameController(game, server, playerNames, playerClients);

        // Stuur START bericht
        String[] names = playerNames.toArray(new String[0]);
        String startMsg = new Start(names).transformToProtocolString();
        server.broadcast(startMsg);

        // Stuur initiële game state (via GameController)
        gameController.sendGameStateToAll();

        // Stuur alle stock pile top cards
        for (Player player : players) {
            gameController.sendStockTopCard(player);
        }

        // Kondig aan wie er begint
        Player currentPlayer = game.getCurrentPlayer();
        String turnMsg = new Turn(currentPlayer.getName()).transformToProtocolString();
        server.broadcast(turnMsg);

        System.out.println("Game gestart! " + currentPlayer.getName() + " begint.");
    }

    /**
     * Verwerkt een move (PLAY command)
     * Delegeert naar GameController
     */
    public void handleMove(String playerName, Position from, Position to) {
        if (game == null || gameController == null) {
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        gameController.handleMove(playerName, from, to);
    }

    /**
     * Beëindigt een beurt (END command)
     * Delegeert naar GameController
     */
    public void endTurn(String playerName) {
        if (game == null || gameController == null) {
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        gameController.endTurn(playerName);
    }

    /**
     * Stuurt TABLE naar een speler (TABLE command)
     * Delegeert naar GameController
     */
    public void sendTableToPlayer(String playerName) {
        if (game == null || gameController == null) {
            return;
        }

        gameController.sendTableToPlayer(playerName);
    }

    /**
     * Stuurt HAND naar een speler (HAND command)
     * Delegeert naar GameController
     */
    public void sendHandToPlayer(String playerName) {
        if (game == null || gameController == null) {
            return;
        }

        gameController.sendHandToPlayer(playerName);
    }

    /**
     * Verwijdert een speler (disconnect)
     * Protocol: Broadcast ERROR~103 en beëindig game
     */
    public void removePlayer(String playerName) {
        // Verwijder uit lijsten
        for (int i = 0; i < playerNames.size(); i++) {
            if (playerNames.get(i).equals(playerName)) {
                playerNames.remove(i);
                playerClients.remove(i);
                break;
            }
        }

        // Als game bezig is: beëindig game (voorkomt bugs)
        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.PLAYER_DISCONNECTED)
                                .transformToProtocolString();
            server.broadcast(errorMsg);

            System.out.println("Game beëindigd door disconnect: " + playerName);
            game = null;
            gameController = null;
            requiredPlayers = -1;  // Reset voor nieuwe game
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Parse features string (bijv. "CLM") naar Feature array
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
     * Zoekt een ClientHandler op basis van naam
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
     * Stuurt error naar een speler
     */
    private void sendErrorToPlayer(String playerName, ErrorCode errorCode) {
        ClientHandler client = getClientByName(playerName);
        if (client != null) {
            String errorMsg = new protocol.server.Error(errorCode)
                                .transformToProtocolString();
            client.sendMessage(errorMsg);
        }
    }
}
