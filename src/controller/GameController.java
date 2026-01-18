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
 * GameController vertaalt protocol berichten naar game acties
 * Dit maakt de code simpeler:
 * - GameManager = lobby management (spelers toevoegen, game starten)
 * - GameController = game logica (moves handlen, scores bijhouden)
 */
public class GameController {
    private Game game;
    private Server server;
    private List<String> playerNames;      // Namen van alle spelers
    private List<ClientHandler> playerClients;  // Connecties naar alle spelers

    /**
     * Maakt een nieuwe GameController
     * @param game Het game object
     * @param server De server voor broadcasting
     * @param playerNames De namen van alle spelers
     * @param playerClients De client handlers van alle spelers
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
     * Verwerkt een move van een speler (PLAY command)
     * Controleert of de move geldig is en voert hem uit
     */
    public void handleMove(String playerName, Position from, Position to) {
        Player player = getPlayerByName(playerName);
        if (player == null) {
            return;
        }

        try {
            // Converteer position naar card action
            CardAction action = positionToAction(player, from, to);

            if (action != null) {
                // Voer de move uit in het spel
                List<CardAction> actions = new ArrayList<>();
                actions.add(action);
                game.doMove(actions, player);

                // Broadcast de move naar alle spelers
                String playMsg = new protocol.server.Play(from, to, playerName).transformToProtocolString();
                server.broadcast(playMsg);

                // Als van stock pile gespeeld: stuur nieuwe top card
                if (from instanceof StockPilePosition) {
                    sendStockTopCard(player);
                }

                // Stuur updated game state
                sendGameStateToAll();

                // Check of speler deze RONDE heeft gewonnen
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
     * Verwerkt als een speler een ronde wint
     * Check of het hele spel nu ook afgelopen is (>= 500 punten)
     */
    private void handleRoundWin(Player player) {
        // Sluit ronde af en bereken scores
        RoundResult result = game.finishRound(player);

        // Kondig ronde winnaar aan
        announceRoundWinner(result);

        if (result.gameOver) {
            // Spel is helemaal afgelopen! Overall winnaar heeft >= 500 punten
            announceOverallWinner(result.overallWinner);
        } else {
            // Start nieuwe ronde (scores blijven behouden)
            game.startNewRound();
            announceNewRound();
        }
    }

    /**
     * Kondigt de winnaar van een ronde aan (niet het hele spel!)
     * Stuurt WINNER message met alle scores
     */
    private void announceRoundWinner(RoundResult result) {
        // Maak score lijst voor protocol
        List<Winner.Score> scores = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            int score = result.allScores.get(p);
            scores.add(new Winner.Score(p.getName(), score));
        }

        Winner.Score[] scoreArray = scores.toArray(new Winner.Score[0]);
        String msg = new Winner(scoreArray).transformToProtocolString();
        server.broadcast(msg);

        System.out.println("Ronde " + game.getRoundNumber() + " winnaar: " +
                           result.roundWinner.getName() +
                           " (+" + result.pointsScored + " punten)");
    }

    /**
     * Kondigt de overall winnaar aan (>= 500 punten)
     * Dit is het EINDE van het hele spel!
     */
    private void announceOverallWinner(Player winner) {
        System.out.println("======================");
        System.out.println("SPEL AFGELOPEN!");
        System.out.println("Overall winnaar: " + winner.getName());
        System.out.println("======================");

        // WINNER bericht is al verstuurd door announceRoundWinner
        // Hier hoeven we alleen te loggen
    }

    /**
     * Kondigt een nieuwe ronde aan
     * Stuurt ROUND bericht en nieuwe game state
     */
    private void announceNewRound() {
        // Maak score lijst voor ROUND bericht
        List<Round.Score> scores = new ArrayList<>();
        Map<Player, Integer> allScores = game.getAllScores();
        for (Player p : game.getPlayers()) {
            int score = allScores.get(p);
            scores.add(new Round.Score(p.getName(), score));
        }

        Round.Score[] scoreArray = scores.toArray(new Round.Score[0]);
        String msg = new Round(scoreArray).transformToProtocolString();
        server.broadcast(msg);

        // Stuur nieuwe game state
        sendGameStateToAll();

        // Kondig aan wie er aan de beurt is
        Player currentPlayer = game.getCurrentPlayer();
        String turnMsg = new Turn(currentPlayer.getName()).transformToProtocolString();
        server.broadcast(turnMsg);

        System.out.println("Nieuwe ronde gestart! Ronde " + game.getRoundNumber());
    }

    /**
     * Beëindigt de beurt van een speler (END command)
     */
    public void endTurn(String playerName) {
        Player player = getPlayerByName(playerName);
        if (player == null) {
            return;
        }

        // Check of het deze speler's beurt is
        Player currentPlayer = game.getCurrentPlayer();
        if (currentPlayer != player) {
            sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        try {
            // Beëindig beurt (gaat naar volgende speler)
            game.endTurn();

            // Haal nieuwe current player op
            Player nextPlayer = game.getCurrentPlayer();

            // Broadcast TURN bericht
            String turnMsg = new Turn(nextPlayer.getName()).transformToProtocolString();
            server.broadcast(turnMsg);

            // Stuur HAND naar nieuwe speler
            sendHandToPlayer(nextPlayer.getName());

            // Stuur stock pile top card voor nieuwe speler
            sendStockTopCard(nextPlayer);

        } catch (GameException e) {
            sendErrorToPlayer(playerName, ErrorCode.INVALID_MOVE);
        }
    }

    /**
     * Stuurt TABLE bericht naar een specifieke speler
     */
    public void sendTableToPlayer(String playerName) {
        ClientHandler client = getClientByName(playerName);
        if (client != null) {
            String tableMsg = createTableMessage();
            client.sendMessage(tableMsg);
        }
    }

    /**
     * Stuurt HAND bericht naar een specifieke speler
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
     * Stuurt game state naar alle spelers
     * - TABLE naar iedereen
     * - HAND naar elke speler individueel
     */
    public void sendGameStateToAll() {
        // Stuur table naar iedereen
        String tableMsg = createTableMessage();
        server.broadcast(tableMsg);

        // Stuur elke speler zijn hand
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
     * Stuurt STOCK bericht (top card van stock pile)
     * Dit wordt naar ALLE spelers gestuurd (want iedereen kan het zien)
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
     * Maakt een TABLE protocol bericht
     * Bevat: building piles + discard piles van alle spelers
     */
    private String createTableMessage() {
        // Building piles info
        String[] buildingPileValues = new String[NUM_BUILDING_PILES];
        for (int i = 0; i < NUM_BUILDING_PILES; i++) {
            BuildingPile pile = game.getBuildingPile(i);
            if (pile.isFull() || pile.isEmpty()) {
                buildingPileValues[i] = null;  // Lege of volle pile = X
            } else {
                int nextExpected = pile.size() + 1;
                buildingPileValues[i] = String.valueOf(nextExpected);
            }
        }

        // Discard piles van alle spelers
        List<protocol.server.Table.PlayerTable> playerTables = new ArrayList<>();
        List<Player> players = game.getPlayers();

        for (Player player : players) {
            String name = player.getName();

            // Haal 4 discard piles op
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

        // Maak TABLE bericht
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
     * Converteert Position objecten naar CardAction objecten
     * Dit is de "vertaling" van protocol naar game logica
     */
    private CardAction positionToAction(Player player, Position from, Position to) {
        // Van hand naar building of discard pile
        if (from instanceof HandPosition && to instanceof NumberedPilePosition) {
            HandPosition handPos = (HandPosition) from;
            NumberedPilePosition pilePos = (NumberedPilePosition) to;

            Integer cardNum = handPos.getCard().getNumber();
            Card actualCard;

            if (cardNum == null) {
                // Skip-Bo kaart
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

        // Van stock naar building pile
        if (from instanceof StockPilePosition && to instanceof NumberedPilePosition) {
            NumberedPilePosition pilePos = (NumberedPilePosition) to;
            if (pilePos.getType().equals("B")) {
                return new CardActionStockPileToBuildingPile(pilePos.getIndex());
            }
        }

        // Van discard naar building pile
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
     * Converteert een Card naar String (voor protocol)
     */
    private String cardToString(Card card) {
        if (card.isSkipBo()) {
            return "SB";
        } else {
            return String.valueOf(card.getNumber());
        }
    }

    /**
     * Converteert een lijst van Cards naar String array (voor protocol)
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
     * Zoekt een Player object op basis van naam
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
     * Zoekt een ClientHandler op basis van speler naam
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
     * Stuurt een error bericht naar een specifieke speler
     */
    private void sendErrorToPlayer(String playerName, ErrorCode errorCode) {
        ClientHandler client = getClientByName(playerName);
        if (client != null) {
            String errorMsg = new protocol.server.Error(errorCode).transformToProtocolString();
            client.sendMessage(errorMsg);
        }
    }
}
