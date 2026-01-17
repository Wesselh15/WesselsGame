package controller;

import model.*;
import protocol.server.*;
import protocol.common.ErrorCode;
import protocol.common.Feature;
import protocol.common.position.*;

import java.util.ArrayList;
import java.util.List;

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
        this.requiredPlayers = 2;
    }

    public void addPlayer(String playerName, ClientHandler client) {
        // Check if game already started
        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
            client.sendMessage(errorMsg);
            return;
        }

        // Check if name already taken
        for (int i = 0; i < playerNames.size(); i++) {
            if (playerNames.get(i).equals(playerName)) {
                String errorMsg = new protocol.server.Error(ErrorCode.NAME_IN_USE).transformToProtocolString();
                client.sendMessage(errorMsg);
                return;
            }
        }

        // Add player to waiting list
        playerNames.add(playerName);
        playerClients.add(client);

        // Tell everyone a new player joined
        String welcomeMsg = new Welcome(playerName, new Feature[0]).transformToProtocolString();
        server.broadcast(welcomeMsg);

        System.out.println("Player added: " + playerName + " (" + playerNames.size() + "/" + requiredPlayers + ")");

        // Start game if we have enough players
        if (playerNames.size() >= requiredPlayers) {
            startGame();
        }
    }

    public void setRequiredPlayers(int count, ClientHandler requestingClient) {
        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
            requestingClient.sendMessage(errorMsg);
            return;
        }

        if (count < 2 || count > 6) {
            String errorMsg = new protocol.server.Error(ErrorCode.INVALID_COMMAND).transformToProtocolString();
            requestingClient.sendMessage(errorMsg);
            return;
        }

        this.requiredPlayers = count;
        System.out.println("Game will start with " + requiredPlayers + " players");

        if (playerNames.size() >= requiredPlayers) {
            startGame();
        }
    }

    private void startGame() {
        if (game != null) {
            return;
        }

        System.out.println("Starting game with " + playerNames.size() + " players");

        // Create Player objects for the game
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < playerNames.size(); i++) {
            Player player = new Player(playerNames.get(i));
            players.add(player);
        }

        // Create the game
        game = new Game(players);

        // Tell everyone the game is starting
        String[] names = new String[playerNames.size()];
        for (int i = 0; i < playerNames.size(); i++) {
            names[i] = playerNames.get(i);
        }
        String startMsg = new Start(names).transformToProtocolString();
        server.broadcast(startMsg);

        // Send initial game state
        sendGameStateToAll();

        // Send all players' stock pile top cards
        for (int i = 0; i < players.size(); i++) {
            sendStockTopCard(players.get(i));
        }

        // Tell everyone whose turn it is
        Player currentPlayer = game.getCurrentPlayer();
        String turnMsg = new Turn(currentPlayer.getName()).transformToProtocolString();
        server.broadcast(turnMsg);
    }

    public void handleMove(String playerName, Position from, Position to) {
        if (game == null) {
            ClientHandler client = getClientByName(playerName);
            if (client != null) {
                String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED).transformToProtocolString();
                client.sendMessage(errorMsg);
            }
            return;
        }

        Player player = getPlayerByName(playerName);
        if (player == null) {
            return;
        }

        // Remember whose turn it was before the move
        Player playerBeforeMove = game.getCurrentPlayer();

        try {
            // Convert position to card action
            CardAction action = positionToAction(player, from, to);

            if (action != null) {
                // Check if this is a discard action (ends turn)
                boolean isDiscard = (action instanceof CardActionHandToDiscardPile);

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

                // If turn changed (because of discard), announce new turn
                Player playerAfterMove = game.getCurrentPlayer();
                if (playerBeforeMove != playerAfterMove) {
                    // Send TURN message to all clients
                    String turnMsg = new Turn(playerAfterMove.getName()).transformToProtocolString();
                    server.broadcast(turnMsg);

                    // Send stock pile top card for new player
                    sendStockTopCard(playerAfterMove);
                }
            } else {
                ClientHandler client = getClientByName(playerName);
                if (client != null) {
                    String errorMsg = new protocol.server.Error(ErrorCode.INVALID_MOVE).transformToProtocolString();
                    client.sendMessage(errorMsg);
                }
            }
        } catch (GameException e) {
            ClientHandler client = getClientByName(playerName);
            if (client != null) {
                String errorMsg = new protocol.server.Error(ErrorCode.INVALID_MOVE).transformToProtocolString();
                client.sendMessage(errorMsg);
            }
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
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
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
        String[] buildingPileValues = new String[4];
        for (int i = 0; i < 4; i++) {
            BuildingPile pile = game.getBuildingPile(i);
            if (!pile.isEmpty()) {
                buildingPileValues[i] = String.valueOf(pile.size());
            } else {
                buildingPileValues[i] = null;
            }
        }

        // Get each player's discard piles
        List<protocol.server.Table.PlayerTable> playerTables = new ArrayList<>();
        List<Player> players = game.getPlayers();

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            String name = player.getName();

            // Use a loop for discard piles
            String[] discardPileValues = new String[4];
            for (int j = 0; j < 4; j++) {
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
        protocol.server.Table.PlayerTable[] ptArray = new protocol.server.Table.PlayerTable[playerTables.size()];
        for (int i = 0; i < playerTables.size(); i++) {
            ptArray[i] = playerTables.get(i);
        }

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

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            int score = player.equals(winner) ? 100 : 0;
            Winner.Score s = new Winner.Score(player.getName(), score);
            scoreList.add(s);
        }

        Winner.Score[] scores = new Winner.Score[scoreList.size()];
        for (int i = 0; i < scoreList.size(); i++) {
            scores[i] = scoreList.get(i);
        }

        String winnerMsg = new Winner(scores).transformToProtocolString();
        server.broadcast(winnerMsg);
        System.out.println("Game ended. Winner: " + winner.getName());
    }

    public void removePlayer(String playerName) {
        // Find and remove player
        for (int i = 0; i < playerNames.size(); i++) {
            if (playerNames.get(i).equals(playerName)) {
                playerNames.remove(i);
                playerClients.remove(i);
                break;
            }
        }

        if (game != null) {
            String errorMsg = new protocol.server.Error(ErrorCode.PLAYER_DISCONNECTED).transformToProtocolString();
            server.broadcast(errorMsg);
        }
    }

    // Helper methods

    private Player getPlayerByName(String name) {
        if (game == null) {
            return null;
        }

        List<Player> players = game.getPlayers();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getName().equals(name)) {
                return players.get(i);
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
        for (int i = 0; i < cards.size(); i++) {
            result[i] = cardToString(cards.get(i));
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
                for (int i = 0; i < hand.size(); i++) {
                    if (hand.get(i).isSkipBo()) {
                        actualCard = hand.get(i);
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
}
