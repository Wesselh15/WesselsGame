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
 * Manages a single game instance
 * Handles player connections and converts protocol messages to game actions
 */
public class GameManager {
    private Game game;
    private Server server;

    // Lists to track players and their connections
    private List<String> playerNames;
    private List<ClientHandler> playerClients;

    private int requiredPlayers;

    public GameManager(Server server) {
        this.server = server;
        this.playerNames = new ArrayList<>();
        this.playerClients = new ArrayList<>();
        // Protocol: No default required players, must be set via GAME command
        this.requiredPlayers = -1;
    }

    /**
     * Adds a player to the game lobby
     * Protocol: HELLO~NAME~FEATURES -> WELCOME~NAME~FEATURES (only to requesting client)
     */
    public void addPlayer(String playerName, String featuresStr, ClientHandler client) {
        // Check if game already started
        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
            client.sendMessage(errorMsg);
            return;
        }

        // Check if name already taken (ERROR~002)
        for (String existingName : playerNames) {
            if (existingName.equals(playerName)) {
                String errorMsg = new protocol.server.Error(ErrorCode.NAME_IN_USE).transformToProtocolString();
                client.sendMessage(errorMsg);
                return;
            }
        }

        // Add player to waiting list
        playerNames.add(playerName);
        playerClients.add(client);

        // Parse features string to Feature array (simple implementation)
        Feature[] features = parseFeatures(featuresStr);

        // Protocol: Send WELCOME only to this client (NOT broadcast)
        String welcomeMsg = new Welcome(playerName, features).transformToProtocolString();
        client.sendMessage(welcomeMsg);

        System.out.println("Player added: " + playerName + " (" + playerNames.size() + "/" + requiredPlayers + ")");

        // NOTE: Game does NOT auto-start anymore
        // Client must send GAME~AMOUNT command
    }

    /**
     * Parses features string (e.g., "CLM") into Feature array
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
     * Sets the required number of players for the game
     * Protocol: GAME~AMOUNT -> QUEUE or START
     */
    public void setRequiredPlayers(int count, ClientHandler requestingClient) {
        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
            requestingClient.sendMessage(errorMsg);
            return;
        }

        // Validate: must be 2-6 players
        if (count < MIN_PLAYERS || count > MAX_PLAYERS) {
            String errorMsg = new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString();
            requestingClient.sendMessage(errorMsg);
            return;
        }

        this.requiredPlayers = count;
        System.out.println("Game will start with " + requiredPlayers + " players");

        // Check if we have enough players
        if (playerNames.size() >= requiredPlayers) {
            // Start the game immediately
            startGame();
        } else {
            // Send QUEUE message to all players (not enough players yet)
            String queueMsg = new Queue().transformToProtocolString();
            server.broadcast(queueMsg);
            System.out.println("Waiting for more players: " + playerNames.size() + "/" + requiredPlayers);
        }
    }

    private void startGame() {
        if (game != null) {
            return;
        }

        System.out.println("Starting game with " + playerNames.size() + " players");

        // Create Player objects for the game
        List<Player> players = new ArrayList<>();
        for (String name : playerNames) {
            Player player = new Player(name);
            players.add(player);
        }

        // Create the game
        game = new Game(players);

        // Tell everyone the game is starting
        String[] names = playerNames.toArray(new String[0]);
        String startMsg = new Start(names).transformToProtocolString();
        server.broadcast(startMsg);

        // Send initial game state
        sendGameStateToAll();

        // Send all players' stock pile top cards
        for (Player player : players) {
            sendStockTopCard(player);
        }

        // Tell everyone whose turn it is
        Player currentPlayer = game.getCurrentPlayer();
        String turnMsg = new Turn(currentPlayer.getName()).transformToProtocolString();
        server.broadcast(turnMsg);
    }

    public void handleMove(String playerName, Position from, Position to) {
        if (game == null) {
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        Player player = getPlayerByName(playerName);
        if (player == null) {
            return;
        }

        try {
            // Convert position to card action
            CardAction action = positionToAction(player, from, to);

            if (action != null) {
                // Let the game execute the move
                List<CardAction> actions = new ArrayList<>();
                actions.add(action);
                game.doMove(actions, player);

                // Tell everyone about the move
                String playMsg = new protocol.server.Play(from, to, playerName).transformToProtocolString();
                server.broadcast(playMsg);

                // If stock pile was played from, send new top card
                if (from instanceof StockPilePosition) {
                    sendStockTopCard(player);
                }

                // Send updated game state
                sendGameStateToAll();

                // Check if player won
                if (game.hasPlayerWon(player)) {
                    announceWinner(player);
                    return;
                }

                // NOTE: Turn does NOT change automatically anymore
                // Client must send END command to end the turn
            } else {
                sendErrorToPlayer(playerName, ErrorCode.INVALID_MOVE);
            }
        } catch (GameException e) {
            sendErrorToPlayer(playerName, ErrorCode.INVALID_MOVE);
        }
    }

    /**
     * Ends the turn for the specified player
     * Protocol: END command
     */
    public void endTurn(String playerName) {
        if (game == null) {
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        Player player = getPlayerByName(playerName);
        if (player == null) {
            return;
        }

        // Check if it's this player's turn
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer != player) {
            // Not your turn - send error
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        try {
            // End the turn (advances to next player and refills their hand)
            game.endTurn();

            // Get the new current player
            Player nextPlayer = game.getCurrentPlayer();

            // Broadcast TURN message to everyone
            String turnMsg = new Turn(nextPlayer.getName()).transformToProtocolString();
            server.broadcast(turnMsg);

            // Send HAND to the new current player
            sendHandToPlayer(nextPlayer.getName());

            // Send stock pile top card for new player
            sendStockTopCard(nextPlayer);

        } catch (GameException e) {
            sendErrorToPlayer(playerName, ErrorCode.INVALID_MOVE);
        }
    }

    public void sendTableToPlayer(String playerName) {
        if (game == null) {
            return;
        }

        ClientHandler client = getClientByName(playerName);
        if (client != null) {
            String tableMsg = createTableMessage();
            client.sendMessage(tableMsg);
        }
    }

    public void sendHandToPlayer(String playerName) {
        if (game == null) {
            return;
        }

        Player player = getPlayerByName(playerName);
        ClientHandler client = getClientByName(playerName);

        if (player != null && client != null) {
            List<Card> hand = game.getHand(player);
            String[] cardStrings = cardsToStrings(hand);
            String handMsg = new protocol.server.Hand(cardStrings).transformToProtocolString();
            client.sendMessage(handMsg);
        }
    }

    private void sendGameStateToAll() {
        // Send table to everyone
        String tableMsg = createTableMessage();
        server.broadcast(tableMsg);

        // Send each player their hand
        List<Player> players = game.getPlayers();
        for (Player player : players) {
            String name = player.getName();
            ClientHandler client = getClientByName(name);

            if (client != null) {
                List<Card> hand = game.getHand(player);
                String[] cardStrings = cardsToStrings(hand);
                String handMsg = new protocol.server.Hand(cardStrings).transformToProtocolString();
                client.sendMessage(handMsg);
            }
        }
    }

    private void sendStockTopCard(Player player) {
        // Send stock pile top card to all players (it's visible to everyone)
        StockPile stockPile = game.getStockPile(player);
        if (!stockPile.isEmpty()) {
            Card topCard = stockPile.topCard();
            String cardStr = cardToString(topCard);
            String stockMsg = new Stock(player.getName(), cardStr).transformToProtocolString();
            server.broadcast(stockMsg);
        }
    }

    private String createTableMessage() {
        // Get building piles info using a loop
        // Protocol: Show the next expected card number for each building pile
        String[] buildingPileValues = new String[NUM_BUILDING_PILES];
        for (int i = 0; i < NUM_BUILDING_PILES; i++) {
            BuildingPile pile = game.getBuildingPile(i);
            // Next expected card = current size + 1
            // If pile is empty, next expected is 1
            // If pile is full (size 12), it will be cleared, so show null (X)
            if (pile.isFull()) {
                buildingPileValues[i] = null; // Full pile shows as X
            } else if (pile.isEmpty()) {
                buildingPileValues[i] = "1"; // Empty pile expects card 1
            } else {
                int nextExpected = pile.size() + 1;
                buildingPileValues[i] = String.valueOf(nextExpected);
            }
        }

        // Get each player's discard piles
        List<protocol.server.Table.PlayerTable> playerTables = new ArrayList<>();
        List<Player> players = game.getPlayers();

        for (Player player : players) {
            String name = player.getName();

            // Use a loop for discard piles
            String[] discardPileValues = new String[NUM_DISCARD_PILES];
            for (int j = 0; j < NUM_DISCARD_PILES; j++) {
                DiscardPile dpile = game.getDiscardPile(player, j);
                if (!dpile.isEmpty()) {
                    Card top = dpile.topCard();
                    discardPileValues[j] = cardToString(top);
                } else {
                    discardPileValues[j] = null;
                }
            }

            protocol.server.Table.PlayerTable pt = new protocol.server.Table.PlayerTable(
                name, 0,
                discardPileValues[0],
                discardPileValues[1],
                discardPileValues[2],
                discardPileValues[3]
            );
            playerTables.add(pt);
        }

        // Convert list to array
        protocol.server.Table.PlayerTable[] ptArray = playerTables.toArray(new protocol.server.Table.PlayerTable[0]);

        protocol.server.Table table = new protocol.server.Table(
            ptArray,
            buildingPileValues[0],
            buildingPileValues[1],
            buildingPileValues[2],
            buildingPileValues[3]
        );
        return table.transformToProtocolString();
    }

    private void announceWinner(Player winner) {
        List<Winner.Score> scoreList = new ArrayList<>();
        List<Player> players = game.getPlayers();

        for (Player player : players) {
            int score = player.equals(winner) ? 100 : 0;
            Winner.Score s = new Winner.Score(player.getName(), score);
            scoreList.add(s);
        }

        Winner.Score[] scores = scoreList.toArray(new Winner.Score[0]);

        String winnerMsg = new Winner(scores).transformToProtocolString();
        server.broadcast(winnerMsg);
        System.out.println("Game ended. Winner: " + winner.getName());
    }

    /**
     * Removes a player (when they disconnect)
     * Protocol: Broadcasts ERROR~103 and advances turn if it was current player's turn
     */
    public void removePlayer(String playerName) {
        // Check if it was the current player's turn before removing
        boolean wasCurrentPlayer = false;
        if (game != null) {
            Player currentPlayer = game.getCurrentPlayer();
            if (currentPlayer != null && currentPlayer.getName().equals(playerName)) {
                wasCurrentPlayer = true;
            }
        }

        // Find and remove player
        for (int i = 0; i < playerNames.size(); i++) {
            if (playerNames.get(i).equals(playerName)) {
                playerNames.remove(i);
                playerClients.remove(i);
                break;
            }
        }

        if (game != null) {
            // Broadcast disconnect error
            String errorMsg = new protocol.server.Error(ErrorCode.PLAYER_DISCONNECTED).transformToProtocolString();
            server.broadcast(errorMsg);

            // If it was current player's turn, advance to next player
            if (wasCurrentPlayer) {
                try {
                    game.endTurn();
                    Player nextPlayer = game.getCurrentPlayer();

                    // Broadcast whose turn it is now
                    String turnMsg = new Turn(nextPlayer.getName()).transformToProtocolString();
                    server.broadcast(turnMsg);

                    // Send hand to new current player
                    sendHandToPlayer(nextPlayer.getName());
                } catch (GameException e) {
                    System.err.println("Error advancing turn after disconnect: " + e.getMessage());
                }
            }
        }
    }

    // Helper methods

    private Player getPlayerByName(String name) {
        if (game == null) {
            return null;
        }

        List<Player> players = game.getPlayers();
        for (Player player : players) {
            if (player.getName().equals(name)) {
                return player;
            }
        }
        return null;
    }

    private ClientHandler getClientByName(String name) {
        for (int i = 0; i < playerNames.size(); i++) {
            if (playerNames.get(i).equals(name)) {
                return playerClients.get(i);
            }
        }
        return null;
    }

    private String cardToString(Card card) {
        if (card.isSkipBo()) {
            return "SB";
        } else {
            return String.valueOf(card.getNumber());
        }
    }

    private String[] cardsToStrings(List<Card> cards) {
        String[] result = new String[cards.size()];
        int index = 0;
        for (Card card : cards) {
            result[index++] = cardToString(card);
        }
        return result;
    }

    private CardAction positionToAction(Player player, Position from, Position to) {
        // From hand to building or discard pile
        if (from instanceof HandPosition && to instanceof NumberedPilePosition) {
            HandPosition handPos = (HandPosition) from;
            NumberedPilePosition pilePos = (NumberedPilePosition) to;

            Integer cardNum = handPos.getCard().getNumber();
            Card actualCard;

            if (cardNum == null) {
                // Skip-Bo card
                List<Card> hand = game.getHand(player);
                actualCard = null;
                for (Card card : hand) {
                    if (card.isSkipBo()) {
                        actualCard = card;
                        break;
                    }
                }
            } else {
                actualCard = game.findCardInHand(player, cardNum);
            }

            if (actualCard == null) {
                return null;
            }

            if (pilePos.getType().equals("B")) {
                return new CardActionHandToBuildingPile(actualCard, pilePos.getIndex());
            } else if (pilePos.getType().equals("D")) {
                return new CardActionHandToDiscardPile(actualCard, pilePos.getIndex());
            }
        }

        // From stock to building pile
        if (from instanceof StockPilePosition && to instanceof NumberedPilePosition) {
            NumberedPilePosition pilePos = (NumberedPilePosition) to;
            if (pilePos.getType().equals("B")) {
                return new CardActionStockPileToBuildingPile(pilePos.getIndex());
            }
        }

        // From discard to building pile
        if (from instanceof NumberedPilePosition && to instanceof NumberedPilePosition) {
            NumberedPilePosition fromPile = (NumberedPilePosition) from;
            NumberedPilePosition toPile = (NumberedPilePosition) to;

            if (fromPile.getType().equals("D") && toPile.getType().equals("B")) {
                return new CardActionDiscardPileToBuildingPile(fromPile.getIndex(), toPile.getIndex());
            }
        }

        return null;
    }

    /**
     * Sends an error message to a specific player
     *
     * @param playerName Name of the player to send error to
     * @param errorCode The error code to send
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
