package controller;

import model.*;
import protocol.server.*;
import protocol.common.ErrorCode;
import protocol.common.position.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static model.GameConstants.*;

/**
 * GameController translates protocol messages to game actions
 * This makes the code simpler:
 * - GameManager = lobby management (add players, start game)
 * - GameController = game logic (handle moves, track scores)
 */
public class GameController {
    private Game game;
    private Server server;
    private List<String> playerNames;      // Names of all players
    private List<ClientHandler> playerClients;  // Connections to all players

    /**
     * Creates a new GameController
     * @param game The game object
     * @param server The server for broadcasting
     * @param playerNames The names of all players
     * @param playerClients The client handlers of all players
     */
    public GameController(Game game, Server server,
                         List<String> playerNames,
                         List<ClientHandler> playerClients) {
        this.game = game;
        this.server = server;
        this.playerNames = playerNames;
        this.playerClients = playerClients;
    }

    /**
     * Processes a move from a player (PLAY command)
     * Checks if the move is valid and executes it
     */
    public void handleMove(String playerName, Position from, Position to) {
        Player player = getPlayerByName(playerName);
        if (player == null) {
            return;
        }

        try {
            // Convert position to card action
            CardAction action = positionToAction(player, from, to);

            if (action != null) {
                // Execute the move in the game
                List<CardAction> actions = new ArrayList<>();
                actions.add(action);
                game.doMove(actions, player);

                // Broadcast the move to all players
                String playMsg = new protocol.server.Play(from, to, playerName).transformToProtocolString();
                server.broadcast(playMsg);

                // If played from stock pile: send new top card
                if (from instanceof StockPilePosition) {
                    sendStockTopCard(player);
                }

                // Send updated game state
                sendGameStateToAll();

                // Check if player won this ROUND
                if (game.hasPlayerWon(player)) {
                    handleRoundWin(player);
                }
            } else {
                sendErrorToPlayer(playerName, ErrorCode.INVALID_MOVE);
            }
        } catch (GameException e) {
            sendErrorToPlayer(playerName, ErrorCode.INVALID_MOVE);
        }
    }

    /**
     * Processes when a player wins a round
     * Checks if the entire game is now over (>= 500 points)
     */
    private void handleRoundWin(Player player) {
        // Finish round and calculate scores
        RoundResult result = game.finishRound(player);

        // Announce round winner
        announceRoundWinner(result);

        if (result.gameOver) {
            // Game is completely over! Overall winner has >= 500 points
            announceOverallWinner(result.overallWinner);
        } else {
            // Start new round (scores are preserved)
            game.startNewRound();
            announceNewRound();
        }
    }

    /**
     * Announces the winner of a round (not the entire game!)
     * Sends WINNER message with all scores
     */
    private void announceRoundWinner(RoundResult result) {
        // Create score list for protocol
        List<Winner.Score> scores = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            int score = result.allScores.get(p);
            scores.add(new Winner.Score(p.getName(), score));
        }

        Winner.Score[] scoreArray = scores.toArray(new Winner.Score[0]);
        String msg = new Winner(scoreArray).transformToProtocolString();
        server.broadcast(msg);

        System.out.println("Round " + game.getRoundNumber() + " winner: " +
                           result.roundWinner.getName() +
                           " (+" + result.pointsScored + " points)");
    }

    /**
     * Announces the overall winner (>= 500 points)
     * This is the END of the entire game!
     */
    private void announceOverallWinner(Player winner) {
        System.out.println("======================");
        System.out.println("GAME OVER!");
        System.out.println("Overall winner: " + winner.getName());
        System.out.println("======================");

        // WINNER message was already sent by announceRoundWinner
        // Here we only need to log
    }

    /**
     * Announces a new round
     * Sends ROUND message and new game state
     */
    private void announceNewRound() {
        // Create score list for ROUND message
        List<Round.Score> scores = new ArrayList<>();
        Map<Player, Integer> allScores = game.getAllScores();
        for (Player p : game.getPlayers()) {
            int score = allScores.get(p);
            scores.add(new Round.Score(p.getName(), score));
        }

        Round.Score[] scoreArray = scores.toArray(new Round.Score[0]);
        String msg = new Round(scoreArray).transformToProtocolString();
        server.broadcast(msg);

        // Send new game state
        sendGameStateToAll();

        // Announce whose turn it is
        Player currentPlayer = game.getCurrentPlayer();
        String turnMsg = new Turn(currentPlayer.getName()).transformToProtocolString();
        server.broadcast(turnMsg);

        System.out.println("New round started! Round " + game.getRoundNumber());
    }

    /**
     * Ends a player's turn (END command)
     */
    public void endTurn(String playerName) {
        Player player = getPlayerByName(playerName);
        if (player == null) {
            return;
        }

        // Check if it's this player's turn
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer != player) {
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        try {
            // End turn (goes to next player)
            game.endTurn();

            // Get new current player
            Player nextPlayer = game.getCurrentPlayer();

            // Broadcast TURN message
            String turnMsg = new Turn(nextPlayer.getName()).transformToProtocolString();
            server.broadcast(turnMsg);

            // Send HAND to new player
            sendHandToPlayer(nextPlayer.getName());

            // Send stock pile top card for new player
            sendStockTopCard(nextPlayer);

        } catch (GameException e) {
            sendErrorToPlayer(playerName, ErrorCode.INVALID_MOVE);
        }
    }

    /**
     * Sends TABLE message to a specific player
     */
    public void sendTableToPlayer(String playerName) {
        ClientHandler client = getClientByName(playerName);
        if (client != null) {
            String tableMsg = createTableMessage();
            client.sendMessage(tableMsg);
        }
    }

    /**
     * Sends HAND message to a specific player
     */
    public void sendHandToPlayer(String playerName) {
        Player player = getPlayerByName(playerName);
        ClientHandler client = getClientByName(playerName);

        if (player != null && client != null) {
            List<Card> hand = game.getHand(player);
            String[] cardStrings = cardsToStrings(hand);
            String handMsg = new protocol.server.Hand(cardStrings).transformToProtocolString();
            client.sendMessage(handMsg);
        }
    }

    /**
     * Sends game state to all players
     * - TABLE to everyone
     * - HAND to each player individually
     */
    public void sendGameStateToAll() {
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

    /**
     * Sends STOCK message (top card of stock pile)
     * This is sent to ALL players (because everyone can see it)
     */
    public void sendStockTopCard(Player player) {
        StockPile stockPile = game.getStockPile(player);
        if (!stockPile.isEmpty()) {
            Card topCard = stockPile.topCard();
            String cardStr = cardToString(topCard);
            String stockMsg = new Stock(player.getName(), cardStr).transformToProtocolString();
            server.broadcast(stockMsg);
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Creates a TABLE protocol message
     * Contains: building piles + discard piles of all players
     */
    private String createTableMessage() {
        // Building piles info
        String[] buildingPileValues = new String[NUM_BUILDING_PILES];
        for (int i = 0; i < NUM_BUILDING_PILES; i++) {
            BuildingPile pile = game.getBuildingPile(i);
            if (pile.isFull()) {
                buildingPileValues[i] = null;  // Full pile = X (no more cards)
            } else if (pile.isEmpty()) {
                buildingPileValues[i] = null;  // Empty pile = no card yet
            } else {
                int topValue = pile.size();  // Current top card value
                buildingPileValues[i] = String.valueOf(topValue);
            }
        }

        // Discard piles of all players
        List<protocol.server.Table.PlayerTable> playerTables = new ArrayList<>();
        List<Player> players = game.getPlayers();

        for (Player player : players) {
            String name = player.getName();

            // Get 4 discard piles
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
                name,
                0,  // Stock pile size (not shown in protocol, always 0)
                discardPileValues[0],
                discardPileValues[1],
                discardPileValues[2],
                discardPileValues[3]
            );
            playerTables.add(pt);
        }

        // Create TABLE message
        protocol.server.Table.PlayerTable[] ptArray = playerTables.toArray(
            new protocol.server.Table.PlayerTable[0]
        );

        protocol.server.Table table = new protocol.server.Table(
            ptArray,
            buildingPileValues[0],
            buildingPileValues[1],
            buildingPileValues[2],
            buildingPileValues[3]
        );
        return table.transformToProtocolString();
    }

    /**
     * Converts Position objects to CardAction objects
     * This is the "translation" from protocol to game logic
     */
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
                return new CardActionDiscardPileToBuildingPile(
                    fromPile.getIndex(),
                    toPile.getIndex()
                );
            }
        }

        return null;
    }

    /**
     * Converts a Card to String (for protocol)
     */
    private String cardToString(Card card) {
        if (card.isSkipBo()) {
            return "SB";
        } else {
            return String.valueOf(card.getNumber());
        }
    }

    /**
     * Converts a list of Cards to String array (for protocol)
     */
    private String[] cardsToStrings(List<Card> cards) {
        String[] result = new String[cards.size()];
        int index = 0;
        for (Card card : cards) {
            result[index++] = cardToString(card);
        }
        return result;
    }

    /**
     * Finds a Player object by name
     */
    private Player getPlayerByName(String name) {
        List<Player> players = game.getPlayers();
        for (Player player : players) {
            if (player.getName().equals(name)) {
                return player;
            }
        }
        return null;
    }

    /**
     * Finds a ClientHandler by player name
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
     * Sends an error message to a specific player
     */
    private void sendErrorToPlayer(String playerName, ErrorCode errorCode) {
        ClientHandler client = getClientByName(playerName);
        if (client != null) {
            String errorMsg = new protocol.server.Error(errorCode).transformToProtocolString();
            client.sendMessage(errorMsg);
        }
    }
}
