package control;

import model.*;
import protocol.common.ErrorCode;
import protocol.common.position.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * ClientHandler manages one connected player on the server side.
 * Each player gets their own ClientHandler when they connect.
 * This class runs in a separate thread to handle that player's messages.
 */
public class ClientHandler implements Runnable {
    // Network connection to this specific client
    private Socket socket;
    private BufferedReader in;   // Reads messages FROM client
    private PrintWriter out;     // Sends messages TO client

    // References
    private Server server;        // The main server
    private Game game;            // The game this client is playing in (null if not in game yet)
    private Player player;        // This client's player object in the game

    // State
    private String playerName;    // This client's chosen name
    private boolean running;      // Is this handler still running?

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.running = true;
    }

    /**
     * Main loop - runs in a separate thread.
     * Reads messages from the client and handles them.
     */
    @Override
    public void run() {
        try {
            // Set up input/output streams
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // Read and handle messages until client disconnects
            String message;
            while (running && (message = in.readLine()) != null) {
                handleMessage(message.trim());
            }
        } catch (IOException e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    /**
     * Handle a message from the client.
     * Messages are in protocol format: COMMAND~param1~param2~...
     */
    private void handleMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        // Split message by protocol separator
        String[] parts = message.split(protocol.Command.SEPERATOR, -1);
        String command = parts[0];

        try {
            // Handle different client commands
            switch (command) {
                case "HELLO":
                    handleHello(parts);
                    break;
                case "GAME":
                    handleGame(parts);
                    break;
                case "PLAY":
                    handlePlay(parts);
                    break;
                case "END":
                    handleEnd();
                    break;
                case "HAND":
                    handleHandRequest();
                    break;
                case "TABLE":
                    handleTableRequest();
                    break;
                default:
                    sendError(ErrorCode.INVALID_COMMAND);
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            sendError(ErrorCode.INVALID_COMMAND);
        }
    }

    /**
     * Handle HELLO message - client is introducing themselves with a name.
     * Format: HELLO~playerName~features
     */
    private void handleHello(String[] parts) {
        if (parts.length < 2) {
            sendError(ErrorCode.INVALID_COMMAND);
            return;
        }

        String name = parts[1];

        // Validate player name (no empty names, no special characters)
        if (name == null || name.isEmpty() || name.contains(protocol.Command.SEPERATOR)) {
            sendError(ErrorCode.INVALID_PLAYER_NAME);
            return;
        }

        // Register with server (server checks if name is already taken)
        if (server.registerClient(name, this)) {
            this.playerName = name;

            // Send WELCOME confirmation back to client
            protocol.server.Welcome welcome = new protocol.server.Welcome(
                name,
                new protocol.common.Feature[0]
            );
            sendMessage(welcome.transformToProtocolString());
        } else {
            // Name is already taken
            sendError(ErrorCode.NAME_IN_USE);
            running = false;
        }
    }

    /**
     * Handle GAME message - client wants to join a game with N players.
     * Format: GAME~numberOfPlayers
     */
    private void handleGame(String[] parts) {
        // Check if client has introduced themselves first
        if (playerName == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Check if client is already in a game
        if (game != null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        if (parts.length < 2) {
            sendError(ErrorCode.INVALID_COMMAND);
            return;
        }

        try {
            int numberOfPlayers = Integer.parseInt(parts[1]);

            // Validate number of players (Skip-Bo supports 2-6 players)
            if (numberOfPlayers < 2 || numberOfPlayers > 6) {
                sendError(ErrorCode.INVALID_COMMAND);
                return;
            }

            // Add this client to the game queue
            server.addToGameQueue(numberOfPlayers, this);
        } catch (NumberFormatException e) {
            sendError(ErrorCode.INVALID_COMMAND);
        }
    }

    /**
     * Handle PLAY message - client wants to make a move.
     * Format: PLAY~from~to
     * Example: PLAY~H.5~B.1 (play card 5 from hand to building pile 1)
     */
    private void handlePlay(String[] parts) {
        // Check if client is in a game
        if (game == null || player == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Check if it's this player's turn
        if (game.getCurrentPlayer() != player) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        if (parts.length < 3) {
            sendError(ErrorCode.INVALID_COMMAND);
            return;
        }

        try {
            String fromStr = parts[1];  // Where card is coming from
            String toStr = parts[2];    // Where card is going to

            // Parse the move into a CardAction
            CardAction action = parseMove(fromStr, toStr);

            if (action == null) {
                sendError(ErrorCode.INVALID_MOVE);
                return;
            }

            // Execute the move in the game
            List<CardAction> actions = new ArrayList<>();
            actions.add(action);

            try {
                game.doMove(actions, player);

                // Broadcast the play to all players in the game
                protocol.server.Play playCmd = new protocol.server.Play(
                    parsePosition(fromStr),
                    parsePosition(toStr),
                    playerName
                );
                server.broadcastToGame(game, playCmd.transformToProtocolString());

                // Send updated game state to all players
                server.sendGameState(game);

                // Check if someone won
                checkWinner();
            } catch (GameException e) {
                // Move was invalid according to game rules
                sendError(ErrorCode.INVALID_MOVE);
            }
        } catch (Exception e) {
            System.err.println("Error parsing play: " + e.getMessage());
            sendError(ErrorCode.INVALID_MOVE);
        }
    }

    /**
     * Handle END message - client wants to end their turn.
     * Format: END
     */
    private void handleEnd() {
        // Check if client is in a game
        if (game == null || player == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Check if it's this player's turn
        if (game.getCurrentPlayer() != player) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // In Skip-Bo, you must discard a card to end your turn
        // So just sending END without a discard is not allowed
        sendError(ErrorCode.INVALID_MOVE);
    }

    /**
     * Handle HAND request - client wants to see their hand.
     * Format: HAND
     */
    private void handleHandRequest() {
        // Check if client is in a game
        if (game == null || player == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Get player's hand from game
        List<model.Card> hand = game.getHand(player);
        String[] cardStrings = new String[hand.size()];

        // Convert cards to protocol format
        for (int i = 0; i < hand.size(); i++) {
            cardStrings[i] = server.convertCardToProtocol(hand.get(i));
        }

        // Send hand to client
        protocol.server.Hand handCmd = new protocol.server.Hand(cardStrings);
        sendMessage(handCmd.transformToProtocolString());
    }

    /**
     * Handle TABLE request - client wants to see the table state.
     * Format: TABLE
     */
    private void handleTableRequest() {
        // Check if client is in a game
        if (game == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Get building piles (4 piles shared by all players)
        String bp1 = game.getBuildingPile(0).isEmpty() ? null :
            String.valueOf(game.getBuildingPile(0).size());
        String bp2 = game.getBuildingPile(1).isEmpty() ? null :
            String.valueOf(game.getBuildingPile(1).size());
        String bp3 = game.getBuildingPile(2).isEmpty() ? null :
            String.valueOf(game.getBuildingPile(2).size());
        String bp4 = game.getBuildingPile(3).isEmpty() ? null :
            String.valueOf(game.getBuildingPile(3).size());

        // Get each player's discard piles (4 piles per player)
        List<protocol.server.Table.PlayerTable> playerTables = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            String dp1 = server.convertCardToProtocol(game.getDiscardPile(p, 0).topCard());
            String dp2 = server.convertCardToProtocol(game.getDiscardPile(p, 1).topCard());
            String dp3 = server.convertCardToProtocol(game.getDiscardPile(p, 2).topCard());
            String dp4 = server.convertCardToProtocol(game.getDiscardPile(p, 3).topCard());

            Integer score = game.getScores().get(p);
            playerTables.add(new protocol.server.Table.PlayerTable(
                p.getName(),
                score != null ? score : 0,
                dp1,
                dp2,
                dp3,
                dp4
            ));
        }

        // Send table state to client
        protocol.server.Table table = new protocol.server.Table(
            playerTables.toArray(new protocol.server.Table.PlayerTable[0]),
            bp1, bp2, bp3, bp4
        );

        sendMessage(table.transformToProtocolString());
    }

    /**
     * Parse a move from position strings into a CardAction.
     * Examples:
     *   "S" to "B.1" = play from stock pile to building pile 1
     *   "H.5" to "B.2" = play card 5 from hand to building pile 2
     *   "H.3" to "D.1" = discard card 3 from hand to discard pile 1
     *   "D.1" to "B.3" = play top card from discard pile 1 to building pile 3
     */
    private CardAction parseMove(String fromStr, String toStr) {
        try {
            // Parse FROM position
            String[] fromParts = fromStr.split("\\" + protocol.Command.VALUE_SEPERATOR);
            String fromType = fromParts[0];

            // Parse TO position
            String[] toParts = toStr.split("\\" + protocol.Command.VALUE_SEPERATOR);
            String toType = toParts[0];

            // Stock pile to building pile
            if (fromType.equals("S") && toType.equals("B")) {
                int buildingPileIndex = Integer.parseInt(toParts[1]) - 1;  // Convert 1-4 to 0-3
                return new CardActionStockPileToBuildingPile(buildingPileIndex);
            }

            // Hand to building pile
            if (fromType.equals("H") && toType.equals("B")) {
                String cardStr = fromParts[1];
                model.Card card = findCardInHand(cardStr);
                int buildingPileIndex = Integer.parseInt(toParts[1]) - 1;  // Convert 1-4 to 0-3
                if (card != null) {
                    return new CardActionHandToBuildingPile(card, buildingPileIndex);
                }
            }

            // Hand to discard pile (this ends the turn)
            if (fromType.equals("H") && toType.equals("D")) {
                String cardStr = fromParts[1];
                model.Card card = findCardInHand(cardStr);
                int discardPileIndex = Integer.parseInt(toParts[1]) - 1;  // Convert 1-4 to 0-3
                if (card != null) {
                    return new CardActionHandToDiscardPile(card, discardPileIndex);
                }
            }

            // Discard pile to building pile
            if (fromType.equals("D") && toType.equals("B")) {
                int discardPileIndex = Integer.parseInt(fromParts[1]) - 1;  // Convert 1-4 to 0-3
                int buildingPileIndex = Integer.parseInt(toParts[1]) - 1;  // Convert 1-4 to 0-3
                return new CardActionDiscardPileToBuildingPile(discardPileIndex, buildingPileIndex);
            }

            return null;
        } catch (Exception e) {
            System.err.println("Error parsing move: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse a position string into a Position object (for protocol).
     */
    private Position parsePosition(String posStr) {
        try {
            String[] parts = posStr.split("\\" + protocol.Command.VALUE_SEPERATOR);
            String type = parts[0];

            switch (type) {
                case "S":
                    return new StockPilePosition();
                case "H":
                    if (parts.length > 1) {
                        protocol.common.Card card = parseProtocolCard(parts[1]);
                        return new HandPosition(card);
                    }
                    return new HandPosition(null);
                case "B":
                    int bpNum = Integer.parseInt(parts[1]);
                    return new NumberedPilePosition(NumberedPilePosition.Pile.BUILDING_PILE, bpNum);
                case "D":
                    int dpNum = Integer.parseInt(parts[1]);
                    return new NumberedPilePosition(NumberedPilePosition.Pile.DISCARD_PILE, dpNum);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parse a card string from protocol format.
     * "SB" = Skip-Bo card
     * "5" = number card
     * "X" = empty/null
     */
    private protocol.common.Card parseProtocolCard(String cardStr) {
        try {
            if (cardStr.equals("SB")) {
                return new protocol.common.Card();  // Skip-Bo card
            } else if (!cardStr.equals("X")) {
                int number = Integer.parseInt(cardStr);
                return new protocol.common.Card(number);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Find a card in the player's hand by its string representation.
     * "SB" = Skip-Bo card
     * "5" = card with number 5
     */
    private model.Card findCardInHand(String cardStr) {
        if (game == null || player == null) {
            return null;
        }

        List<model.Card> hand = game.getHand(player);

        // Looking for Skip-Bo card
        if (cardStr.equals("SB")) {
            for (model.Card card : hand) {
                if (card.isSkipBo()) {
                    return card;
                }
            }
        }
        // Looking for numbered card
        else if (!cardStr.equals("X")) {
            try {
                int number = Integer.parseInt(cardStr);
                for (model.Card card : hand) {
                    if (!card.isSkipBo() && card.getNumber() == number) {
                        return card;
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        return null;
    }

    /**
     * Check if any player won the game (stock pile is empty).
     */
    private void checkWinner() {
        for (Player p : game.getPlayers()) {
            if (game.getStockPile(p).isEmpty()) {
                // We have a winner! Send WINNER message to all players
                protocol.server.Winner.Score[] scores = new protocol.server.Winner.Score[game.getPlayers().size()];
                for (int i = 0; i < game.getPlayers().size(); i++) {
                    Player player = game.getPlayers().get(i);
                    Integer score = game.getScores().get(player);
                    scores[i] = new protocol.server.Winner.Score(
                        player.getName(),
                        score != null ? score : 0
                    );
                }

                protocol.server.Winner winner = new protocol.server.Winner(scores);
                server.broadcastToGame(game, winner.transformToProtocolString());
                return;
            }
        }
    }

    /**
     * Send a message to this client.
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Send an error message to this client.
     */
    private void sendError(ErrorCode errorCode) {
        protocol.server.Error error = new protocol.server.Error(errorCode);
        sendMessage(error.transformToProtocolString());
    }

    /**
     * Set the game and player for this client (called when game starts).
     */
    public void setGame(Game game, Player player) {
        this.game = game;
        this.player = player;
    }

    /**
     * Get this client's player name.
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * Get this client's player object.
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Clean up when client disconnects.
     */
    private void cleanup() {
        running = false;

        if (playerName != null) {
            server.unregisterClient(playerName);
        }

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("Error cleaning up client handler: " + e.getMessage());
        }
    }
}
