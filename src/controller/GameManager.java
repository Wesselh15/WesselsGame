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

                // Send updated game state
                sendGameStateToAll();

                // Check if player won
                if (game.getStockPile(player).isEmpty()) {
                    announceWinner(player);
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

    private String createTableMessage() {
        // Get building piles info
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

        // Get each player's discard piles
        List<protocol.server.Table.PlayerTable> playerTables = new ArrayList<>();
        List<Player> players = game.getPlayers();

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            String name = player.getName();

            String dp1 = null, dp2 = null, dp3 = null, dp4 = null;

            DiscardPile dpile0 = game.getDiscardPile(player, 0);
            if (!dpile0.isEmpty()) {
                Card top = dpile0.topCard();
                dp1 = cardToString(top);
            }

            DiscardPile dpile1 = game.getDiscardPile(player, 1);
            if (!dpile1.isEmpty()) {
                Card top = dpile1.topCard();
                dp2 = cardToString(top);
            }

            DiscardPile dpile2 = game.getDiscardPile(player, 2);
            if (!dpile2.isEmpty()) {
                Card top = dpile2.topCard();
                dp3 = cardToString(top);
            }

            DiscardPile dpile3 = game.getDiscardPile(player, 3);
            if (!dpile3.isEmpty()) {
                Card top = dpile3.topCard();
                dp4 = cardToString(top);
            }

            protocol.server.Table.PlayerTable pt = new protocol.server.Table.PlayerTable(name, 0, dp1, dp2, dp3, dp4);
            playerTables.add(pt);
        }

        protocol.server.Table.PlayerTable[] ptArray = new protocol.server.Table.PlayerTable[playerTables.size()];
        for (int i = 0; i < playerTables.size(); i++) {
            ptArray[i] = playerTables.get(i);
        }

        protocol.server.Table table = new protocol.server.Table(ptArray, bp1, bp2, bp3, bp4);
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

    private Card findCardInHand(Player player, int cardNumber) {
        List<Card> hand = game.getHand(player);
        for (int i = 0; i < hand.size(); i++) {
            Card card = hand.get(i);
            if (!card.isSkipBo() && card.getNumber() == cardNumber) {
                return card;
            }
        }
        return null;
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
                actualCard = findCardInHand(player, cardNum);
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
