package controller;

import model.*;
import protocol.server.*;
import protocol.common.ErrorCode;
import protocol.common.Feature;
import protocol.common.position.*;

import java.util.*;

public class GameManager {
    private Game game;
    private Server server;
    private Map<String, ClientHandler> playerClients;
    private Map<String, Player> players;
    private boolean gameStarted;
    private int requiredPlayers;

    public GameManager(Server server) {
        this.server = server;
        this.playerClients = new LinkedHashMap<>();
        this.players = new LinkedHashMap<>();
        this.gameStarted = false;
        this.requiredPlayers = 2; // Default to 2 players
    }

    public synchronized void addPlayer(String playerName, ClientHandler client) {
        if (gameStarted) {
            client.sendMessage(new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString());
            return;
        }

        if (playerClients.containsKey(playerName)) {
            client.sendMessage(new protocol.server.Error(ErrorCode.NAME_IN_USE).transformToProtocolString());
            return;
        }

        playerClients.put(playerName, client);
        Player player = new Player(playerName);
        players.put(playerName, player);

        // Broadcast WELCOME to all clients
        server.broadcast(new Welcome(playerName, new Feature[0]).transformToProtocolString());

        System.out.println("Player added: " + playerName + " (" + playerClients.size() + "/" + requiredPlayers + ")");

        // Check if we can start the game
        if (playerClients.size() >= requiredPlayers) {
            startGame();
        }
    }

    public synchronized void setRequiredPlayers(int count, ClientHandler requestingClient) {
        if (gameStarted) {
            requestingClient.sendMessage(new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString());
            return;
        }

        if (count < 2 || count > 6) {
            requestingClient.sendMessage(new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString());
            return;
        }

        this.requiredPlayers = count;
        System.out.println("Game will start with " + requiredPlayers + " players");

        // Check if we can start immediately
        if (playerClients.size() >= requiredPlayers) {
            startGame();
        }
    }

    private void startGame() {
        if (gameStarted) {
            return;
        }

        gameStarted = true;
        System.out.println("Starting game with " + playerClients.size() + " players");

        // Create player list
        List<Player> playerList = new ArrayList<>(players.values());
        game = new Game(playerList);

        // Send START command with player names
        String[] playerNames = playerClients.keySet().toArray(new String[0]);
        server.broadcast(new Start(playerNames).transformToProtocolString());

        // Send initial game state to all players
        broadcastGameState();

        // Announce whose turn it is
        Player currentPlayer = game.getCurrentPlayer();
        server.broadcast(new Turn(currentPlayer.getName()).transformToProtocolString());
    }

    public synchronized void handleMove(String playerName, Position from, Position to) {
        if (!gameStarted) {
            ClientHandler client = playerClients.get(playerName);
            if (client != null) {
                client.sendMessage(new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString());
            }
            return;
        }

        Player player = players.get(playerName);
        if (player == null) {
            return;
        }

        // Check if it's this player's turn
        if (!game.getCurrentPlayer().equals(player)) {
            ClientHandler client = playerClients.get(playerName);
            if (client != null) {
                client.sendMessage(new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString());
            }
            return;
        }

        try {
            // Convert protocol position to CardAction
            CardAction action = createCardAction(player, from, to);

            if (action != null) {
                // Execute the move
                game.doMove(List.of(action), player);

                // Broadcast the move to all players
                server.broadcast(new protocol.server.Play(from, to, playerName).transformToProtocolString());

                // Check if stock pile card changed after move
                if (from instanceof StockPilePosition) {
                    broadcastStockUpdate(player);
                }

                // Broadcast updated game state
                broadcastGameState();

                // Check for winner
                if (game.getStockPile(player).isEmpty()) {
                    handleRoundEnd();
                }
            } else {
                ClientHandler client = playerClients.get(playerName);
                if (client != null) {
                    client.sendMessage(new protocol.server.Error(ErrorCode.INVALID_MOVE).transformToProtocolString());
                }
            }
        } catch (GameException e) {
            ClientHandler client = playerClients.get(playerName);
            if (client != null) {
                client.sendMessage(new protocol.server.Error(ErrorCode.INVALID_MOVE).transformToProtocolString());
            }
        }
    }

    public synchronized void handleEndTurn(String playerName) {
        if (!gameStarted) {
            ClientHandler client = playerClients.get(playerName);
            if (client != null) {
                client.sendMessage(new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString());
            }
            return;
        }

        Player player = players.get(playerName);
        if (player == null || !game.getCurrentPlayer().equals(player)) {
            ClientHandler client = playerClients.get(playerName);
            if (client != null) {
                client.sendMessage(new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString());
            }
            return;
        }

        // Turn is already ended by discarding a card in doMove
        // Just announce next player's turn
        Player nextPlayer = game.getCurrentPlayer();
        server.broadcast(new Turn(nextPlayer.getName()).transformToProtocolString());
        broadcastGameState();
    }

    public synchronized void sendTableToPlayer(String playerName) {
        if (!gameStarted) {
            return;
        }

        ClientHandler client = playerClients.get(playerName);
        if (client != null) {
            String tableMsg = buildTableMessage();
            client.sendMessage(tableMsg);
        }
    }

    public synchronized void sendHandToPlayer(String playerName) {
        if (!gameStarted) {
            return;
        }

        Player player = players.get(playerName);
        ClientHandler client = playerClients.get(playerName);

        if (player != null && client != null) {
            List<Card> hand = game.getHand(player);
            String[] cardStrings = convertToCardStrings(hand);
            client.sendMessage(new protocol.server.Hand(cardStrings).transformToProtocolString());
        }
    }

    private void broadcastGameState() {
        // Send TABLE to all players
        String tableMsg = buildTableMessage();
        server.broadcast(tableMsg);

        // Send HAND to each player (their own hand)
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            String playerName = entry.getKey();
            Player player = entry.getValue();
            ClientHandler client = playerClients.get(playerName);

            if (client != null) {
                List<Card> hand = game.getHand(player);
                String[] cardStrings = convertToCardStrings(hand);
                client.sendMessage(new protocol.server.Hand(cardStrings).transformToProtocolString());
            }
        }
    }

    private void broadcastStockUpdate(Player player) {
        StockPile stockPile = game.getStockPile(player);
        if (!stockPile.isEmpty()) {
            Card topCard = stockPile.topCard();
            String cardStr = topCard.isSkipBo() ? "SB" : String.valueOf(topCard.getNumber());
            server.broadcast(new Stock(player.getName(), cardStr).transformToProtocolString());
        }
    }

    private String buildTableMessage() {
        // Build building piles strings
        String bp1 = null, bp2 = null, bp3 = null, bp4 = null;

        BuildingPile pile0 = game.getBuildingPile(0);
        if (!pile0.isEmpty()) {
            bp1 = String.valueOf(pile0.size());
        }

        BuildingPile pile1 = game.getBuildingPile(1);
        if (!pile1.isEmpty()) {
            bp2 = String.valueOf(pile1.size());
        }

        BuildingPile pile2 = game.getBuildingPile(2);
        if (!pile2.isEmpty()) {
            bp3 = String.valueOf(pile2.size());
        }

        BuildingPile pile3 = game.getBuildingPile(3);
        if (!pile3.isEmpty()) {
            bp4 = String.valueOf(pile3.size());
        }

        // Build player tables
        List<protocol.server.Table.PlayerTable> playerTables = new ArrayList<>();
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            String playerName = entry.getKey();
            Player player = entry.getValue();

            String dp1 = null, dp2 = null, dp3 = null, dp4 = null;

            DiscardPile dpile0 = game.getDiscardPile(player, 0);
            if (!dpile0.isEmpty()) {
                Card top = dpile0.topCard();
                dp1 = top.isSkipBo() ? "SB" : String.valueOf(top.getNumber());
            }

            DiscardPile dpile1 = game.getDiscardPile(player, 1);
            if (!dpile1.isEmpty()) {
                Card top = dpile1.topCard();
                dp2 = top.isSkipBo() ? "SB" : String.valueOf(top.getNumber());
            }

            DiscardPile dpile2 = game.getDiscardPile(player, 2);
            if (!dpile2.isEmpty()) {
                Card top = dpile2.topCard();
                dp3 = top.isSkipBo() ? "SB" : String.valueOf(top.getNumber());
            }

            DiscardPile dpile3 = game.getDiscardPile(player, 3);
            if (!dpile3.isEmpty()) {
                Card top = dpile3.topCard();
                dp4 = top.isSkipBo() ? "SB" : String.valueOf(top.getNumber());
            }

            playerTables.add(new protocol.server.Table.PlayerTable(playerName, 0, dp1, dp2, dp3, dp4));
        }

        protocol.server.Table.PlayerTable[] playerTableArray = playerTables.toArray(new protocol.server.Table.PlayerTable[0]);
        return new protocol.server.Table(playerTableArray, bp1, bp2, bp3, bp4).transformToProtocolString();
    }

    private String[] convertToCardStrings(List<Card> cards) {
        String[] cardStrings = new String[cards.size()];
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            cardStrings[i] = card.isSkipBo() ? "SB" : String.valueOf(card.getNumber());
        }
        return cardStrings;
    }

    private Card findCardInHand(Player player, int cardNumber) {
        List<Card> hand = game.getHand(player);
        for (Card card : hand) {
            if (!card.isSkipBo() && card.getNumber() == cardNumber) {
                return card;
            }
        }
        return null;
    }

    private CardAction createCardAction(Player player, Position from, Position to) {
        // Determine the type of action based on from and to positions
        if (from instanceof HandPosition &&
            to instanceof NumberedPilePosition) {

            HandPosition handPos = (HandPosition) from;
            NumberedPilePosition pilePos = (NumberedPilePosition) to;

            // Find the actual card in the player's hand
            Integer cardNum = handPos.getCard().getNumber();
            Card actualCard;

            if (cardNum == null) {
                // Skip-Bo card
                List<Card> hand = game.getHand(player);
                actualCard = null;
                for (Card c : hand) {
                    if (c.isSkipBo()) {
                        actualCard = c;
                        break;
                    }
                }
            } else {
                actualCard = findCardInHand(player, cardNum);
            }

            if (actualCard == null) {
                return null;
            }

            // Check if it's a building pile (B) or discard pile (D)
            if (pilePos.getType().equals("B")) {
                return new CardActionHandToBuildingPile(actualCard, pilePos.getIndex());
            } else if (pilePos.getType().equals("D")) {
                return new CardActionHandToDiscardPile(actualCard, pilePos.getIndex());
            }
        } else if (from instanceof StockPilePosition &&
                   to instanceof NumberedPilePosition) {

            NumberedPilePosition pilePos = (NumberedPilePosition) to;

            if (pilePos.getType().equals("B")) {
                return new CardActionStockPileToBuildingPile(pilePos.getIndex());
            }
        } else if (from instanceof NumberedPilePosition &&
                   to instanceof NumberedPilePosition) {

            NumberedPilePosition fromPile = (NumberedPilePosition) from;
            NumberedPilePosition toPile = (NumberedPilePosition) to;

            if (fromPile.getType().equals("D") && toPile.getType().equals("B")) {
                return new CardActionDiscardPileToBuildingPile(fromPile.getIndex(), toPile.getIndex());
            }
        }

        return null;
    }

    private void handleRoundEnd() {
        // For simplicity, just announce winner when someone empties their stock pile
        Player winner = game.getCurrentPlayer();

        List<Winner.Score> scoreList = new ArrayList<>();
        for (Player player : game.getPlayers()) {
            // Simple scoring: winner gets 100 points, others get 0
            int score = player.equals(winner) ? 100 : 0;
            scoreList.add(new Winner.Score(player.getName(), score));
        }

        Winner.Score[] scores = scoreList.toArray(new Winner.Score[0]);
        server.broadcast(new Winner(scores).transformToProtocolString());
        System.out.println("Game ended. Winner: " + winner.getName());
    }

    public synchronized void removePlayer(String playerName) {
        playerClients.remove(playerName);

        if (gameStarted) {
            // Handle player disconnection during game
            server.broadcast(new protocol.server.Error(ErrorCode.PLAYER_DISCONNECTED).transformToProtocolString());
        }
    }

    public boolean isGameStarted() {
        return gameStarted;
    }
}
