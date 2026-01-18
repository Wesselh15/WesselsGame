package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Random;
import java.util.List;
import java.util.HashMap;

import static model.GameConstants.*;


public class Game {

    private List<Player> players;
    private Map<Player, Integer> scores;

    private Map<Player, List<DiscardPile>> discardPiles;
    private Map<Player, StockPile> stockPiles;
    private Map<Player, List<model.Card>> hand;

    private List<Card> drawPile;
    private List<BuildingPile> buildingPiles;

    private int currentPlayerIndex;
    private int round;

    public Game(List<Player> players){
        // Generate and Shuffle cards
        drawPile = CardGenerator.generateCards();
        Collections.shuffle(drawPile);

        // Assign Players
        this.players = players;

        // Generate new maps
        this.stockPiles = new HashMap<>();
        this.hand = new HashMap<>();
        this.discardPiles = new HashMap<>();
        this.scores = new HashMap<>();

        // Generates 4 building piles (shared by all players)
        this.buildingPiles = new ArrayList<>();
        for (int i = 0; i < NUM_BUILDING_PILES; i++) {
            buildingPiles.add(new BuildingPile());
        }

        // Assign cards to players
        int cardsToHandout = players.size() <= MAX_PLAYERS / 2 ? STOCK_SIZE_SMALL_GAME : STOCK_SIZE_LARGE_GAME;
        for (Player player : players){
            List<Card> handOut = new ArrayList<>(drawPile.subList(0, cardsToHandout));
            stockPiles.put(player, new StockPile(handOut));
            hand.put(player, new ArrayList<>());
            drawPile.removeAll(handOut);

            // Initialize 4 discard piles per player
            List<DiscardPile> playerDiscardPiles = new ArrayList<>();
            for (int i = 0; i < NUM_DISCARD_PILES; i++) {
                playerDiscardPiles.add(new DiscardPile());
            }
            discardPiles.put(player, playerDiscardPiles);
        }

        // determine FirstPlayer
        Random r = new Random();
        currentPlayerIndex = r.nextInt(players.size());
        handCards(players.get(currentPlayerIndex));

        // set to the first round
        this.round = 0;
    }

    public void handCards(Player player) {
        // Calculate how many cards to draw
        int cardsToDraw = HAND_SIZE - hand.get(player).size();

        // Don't draw more than available in draw pile
        if (cardsToDraw > drawPile.size()) {
            cardsToDraw = drawPile.size();
        }

        // Only draw if there are cards available
        if (cardsToDraw > 0 && !drawPile.isEmpty()) {
            List<Card> handOut = new ArrayList<>(drawPile.subList(0, cardsToDraw));
            hand.get(player).addAll(handOut);
            drawPile.removeAll(handOut);
        }
    }

    public void doMove(List<CardAction> cardActions, Player player) throws GameException {
        // Check if it is the players turn
        if (players.get(currentPlayerIndex) != player) {
            throw new GameException("It's not " + player + "'s turn");
        }

        // Validate every actions first
        for (CardAction cardAction : cardActions) {
            if (!cardAction.isValid(this, player)) {
                throw new GameException("Invalid action: " + cardAction);
            }
        }

        // Execute all actions
        for (CardAction cardAction : cardActions) {
            cardAction.execute(this, player);

            // Checks if any building pile is full and clears it
            for (model.BuildingPile pile : buildingPiles) {
                if (pile.isFull()) {
                    pile.clear();
                }
            }

            // Refill hand ONLY if hand is empty during turn (not after discard)
            if (hand.get(player).isEmpty() && !(cardAction instanceof model.CardActionHandToDiscardPile)) {
                handCards(player);
            }
        }

        // NOTE: Discard no longer automatically ends the turn
        // Client must send END command explicitly to end the turn
    }

    /**
     * Ends the current player's turn and advances to the next player
     * Called when END command is received from the client
     */
    public void endTurn() throws GameException {
        // Move to the next player
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        // Refill hand for the new current player
        handCards(players.get(currentPlayerIndex));
    }


    // Getter methods
    public List<model.Card> getHand(Player player) {
        return hand.get(player);
    }

    public StockPile getStockPile(Player player) {
        return stockPiles.get(player);
    }

    public DiscardPile getDiscardPile(Player player, int index) {
        return discardPiles.get(player).get(index);
    }

    public model.BuildingPile getBuildingPile(int index) {
        return buildingPiles.get(index);
    }

    public Player getCurrentPlayer() {
        return players.get(currentPlayerIndex);
    }

    public List<Player> getPlayers() {
        return players;
    }

    public int getRound() {
        return round;
    }

    public Map<Player, Integer> getScores() {
        return scores;
    }

    // Game logic methods

    public boolean isPlayersTurn(Player player) {
        return players.get(currentPlayerIndex) == player;
    }

    public boolean hasPlayerWon(Player player) {
        StockPile stockPile = stockPiles.get(player);
        return stockPile != null && stockPile.isEmpty();
    }

    public Card findCardInHand(Player player, int cardNumber) {
        List<Card> playerHand = hand.get(player);
        if (playerHand == null) {
            return null;
        }

        for (Card card : playerHand) {
            if (!card.isSkipBo() && card.getNumber() == cardNumber) {
                return card;
            }
        }
        return null;
    }
}
