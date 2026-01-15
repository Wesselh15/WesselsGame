package control;

import model.*;
import protocol.common.ErrorCode;
import protocol.common.position.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {
    private Socket socket;
    private Server server;
    private BufferedReader in;
    private PrintWriter out;
    private String playerName;
    private Game game;
    private Player player;
    private boolean running;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

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

    private void handleMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        String[] parts = message.split(protocol.Command.SEPERATOR, -1);
        String command = parts[0];

        try {
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

    private void handleHello(String[] parts) {
        if (parts.length < 2) {
            sendError(ErrorCode.INVALID_COMMAND);
            return;
        }

        String name = parts[1];

        // Validate player name
        if (name == null || name.isEmpty() || name.contains(protocol.Command.SEPERATOR)) {
            sendError(ErrorCode.INVALID_PLAYER_NAME);
            return;
        }

        // Register with server
        if (server.registerClient(name, this)) {
            this.playerName = name;

            // Send HELLO confirmation back
            protocol.server.Welcome welcome = new protocol.server.Welcome(
                name,
                new protocol.common.Feature[0]
            );
            sendMessage(welcome.transformToProtocolString());
        } else {
            sendError(ErrorCode.NAME_IN_USE);
            running = false;
        }
    }

    private void handleGame(String[] parts) {
        if (playerName == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

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

            if (numberOfPlayers < 2 || numberOfPlayers > 6) {
                sendError(ErrorCode.INVALID_COMMAND);
                return;
            }

            server.addToGameQueue(numberOfPlayers, this);
        } catch (NumberFormatException e) {
            sendError(ErrorCode.INVALID_COMMAND);
        }
    }

    private void handlePlay(String[] parts) {
        if (game == null || player == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        if (game.getCurrentPlayer() != player) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        if (parts.length < 3) {
            sendError(ErrorCode.INVALID_COMMAND);
            return;
        }

        try {
            String fromStr = parts[1];
            String toStr = parts[2];

            CardAction action = parseMove(fromStr, toStr);

            if (action == null) {
                sendError(ErrorCode.INVALID_MOVE);
                return;
            }

            // Execute the move
            List<CardAction> actions = new ArrayList<>();
            actions.add(action);

            try {
                game.doMove(actions, player);

                // Broadcast the play to all players
                protocol.server.Play playCmd = new protocol.server.Play(
                    parsePosition(fromStr),
                    parsePosition(toStr),
                    playerName
                );
                server.broadcastToGame(game, playCmd.transformToProtocolString());

                // Send updated game state
                server.sendGameState(game);

                // Check for winner
                checkWinner();
            } catch (GameException e) {
                sendError(ErrorCode.INVALID_MOVE);
            }
        } catch (Exception e) {
            System.err.println("Error parsing play: " + e.getMessage());
            sendError(ErrorCode.INVALID_MOVE);
        }
    }

    private void handleEnd() {
        if (game == null || player == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        if (game.getCurrentPlayer() != player) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // End turn by doing an empty move list (just to trigger next player)
        // This is a special case - player wants to end turn without discarding
        sendError(ErrorCode.INVALID_MOVE);
    }

    private void handleHandRequest() {
        if (game == null || player == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        List<model.Card> hand = game.getHand(player);
        String[] cardStrings = new String[hand.size()];

        for (int i = 0; i < hand.size(); i++) {
            cardStrings[i] = server.convertCardToProtocol(hand.get(i));
        }

        protocol.server.Hand handCmd = new protocol.server.Hand(cardStrings);
        sendMessage(handCmd.transformToProtocolString());
    }

    private void handleTableRequest() {
        if (game == null) {
            sendError(ErrorCode.COMMAND_NOT_ALLOWED);
            return;
        }

        // Build building piles
        String bp1 = game.getBuildingPile(0).isEmpty() ? null : 
            String.valueOf(game.getBuildingPile(0).size());
        String bp2 = game.getBuildingPile(1).isEmpty() ? null : 
            String.valueOf(game.getBuildingPile(1).size());
        String bp3 = game.getBuildingPile(2).isEmpty() ? null : 
            String.valueOf(game.getBuildingPile(2).size());
        String bp4 = game.getBuildingPile(3).isEmpty() ? null : 
            String.valueOf(game.getBuildingPile(3).size());

        // Build player table information
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

        protocol.server.Table table = new protocol.server.Table(
            playerTables.toArray(new protocol.server.Table.PlayerTable[0]),
            bp1, bp2, bp3, bp4
        );

        sendMessage(table.transformToProtocolString());
    }

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
                int buildingPileIndex = Integer.parseInt(toParts[1]) - 1;
                return new CardActionStockPileToBuildingPile(buildingPileIndex);
            }

            // Hand to building pile
            if (fromType.equals("H") && toType.equals("B")) {
                String cardStr = fromParts[1];
                model.Card card = findCardInHand(cardStr);
                int buildingPileIndex = Integer.parseInt(toParts[1]) - 1;
                if (card != null) {
                    return new CardActionHandToBuildingPile(card, buildingPileIndex);
                }
            }

            // Hand to discard pile
            if (fromType.equals("H") && toType.equals("D")) {
                String cardStr = fromParts[1];
                model.Card card = findCardInHand(cardStr);
                int discardPileIndex = Integer.parseInt(toParts[1]) - 1;
                if (card != null) {
                    return new CardActionHandToDiscardPile(card, discardPileIndex);
                }
            }

            // Discard pile to building pile
            if (fromType.equals("D") && toType.equals("B")) {
                int discardPileIndex = Integer.parseInt(fromParts[1]) - 1;
                int buildingPileIndex = Integer.parseInt(toParts[1]) - 1;
                return new CardActionDiscardPileToBuildingPile(discardPileIndex, buildingPileIndex);
            }

            return null;
        } catch (Exception e) {
            System.err.println("Error parsing move: " + e.getMessage());
            return null;
        }
    }

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

    private protocol.common.Card parseProtocolCard(String cardStr) {
        try {
            if (cardStr.equals("SB")) {
                return new protocol.common.Card();
            } else if (!cardStr.equals("X")) {
                int number = Integer.parseInt(cardStr);
                return new protocol.common.Card(number);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private model.Card findCardInHand(String cardStr) {
        if (game == null || player == null) {
            return null;
        }

        List<model.Card> hand = game.getHand(player);

        if (cardStr.equals("SB")) {
            for (model.Card card : hand) {
                if (card.isSkipBo()) {
                    return card;
                }
            }
        } else if (!cardStr.equals("X")) {
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

    private void checkWinner() {
        for (Player p : game.getPlayers()) {
            if (game.getStockPile(p).isEmpty()) {
                // We have a winner
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

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void sendError(ErrorCode errorCode) {
        protocol.server.Error error = new protocol.server.Error(errorCode);
        sendMessage(error.transformToProtocolString());
    }

    public void setGame(Game game, Player player) {
        this.game = game;
        this.player = player;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Player getPlayer() {
        return player;
    }

    private void cleanup() {
        running = false;

        if (playerName != null) {
            server.unregisterClient(playerName);
        }

        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error cleaning up client handler: " + e.getMessage());
        }
    }
}
