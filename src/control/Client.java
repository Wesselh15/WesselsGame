package control;

import protocol.common.Feature;
import protocol.common.position.*;

import java.io.*;
import java.net.Socket;

public class Client {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String playerName;
    private boolean connected;
    private boolean inGame;
    private ClientView view;

    public Client(String host, int port, ClientView view) {
        this.host = host;
        this.port = port;
        this.connected = false;
        this.inGame = false;
        this.view = view;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            // Start listener thread
            Thread listenerThread = new Thread(this::listenForMessages);
            listenerThread.setDaemon(true);
            listenerThread.start();

            view.showConnected(host, port);
            return true;
        } catch (IOException e) {
            view.showConnectionError(e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        connected = false;

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
            view.showDisconnectionError(e.getMessage());
        }
    }

    private void listenForMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                handleServerMessage(message.trim());
            }
        } catch (IOException e) {
            if (connected) {
                view.showConnectionLost(e.getMessage());
                connected = false;
            }
        }
    }

    private void handleServerMessage(String message) {
        if (message.isEmpty()) {
            return;
        }

        String[] parts = message.split(protocol.Command.SEPERATOR, -1);
        String command = parts[0];

        try {
            switch (command) {
                case "WELCOME":
                    handleWelcome(parts);
                    break;
                case "QUEUE":
                    view.showAddedToQueue();
                    break;
                case "START":
                    handleStart(parts);
                    break;
                case "TURN":
                    handleTurn(parts);
                    break;
                case "HAND":
                    handleHand(parts);
                    break;
                case "STOCK":
                    handleStock(parts);
                    break;
                case "TABLE":
                    handleTable(parts);
                    break;
                case "PLAY":
                    handlePlay(parts);
                    break;
                case "WINNER":
                    handleWinner(parts);
                    break;
                case "ERROR":
                    handleError(parts);
                    break;
                default:
                    view.showUnknownMessage(message);
            }
        } catch (Exception e) {
            view.showMessageHandlingError(e.getMessage());
        }
    }

    private void handleWelcome(String[] parts) {
        if (parts.length >= 2) {
            String name = parts[1];
            view.showPlayerJoined(name);
        }
    }

    private void handleStart(String[] parts) {
        if (parts.length >= 2) {
            String[] players = parts[1].split(protocol.Command.LIST_SEPERATOR);
            view.showGameStarted(players);
            inGame = true;
        }
    }

    private void handleTurn(String[] parts) {
        if (parts.length >= 2) {
            String currentPlayer = parts[1];
            boolean isYourTurn = currentPlayer.equals(playerName);
            view.showTurn(currentPlayer, isYourTurn);
        }
    }

    private void handleHand(String[] parts) {
        if (parts.length >= 2) {
            String[] cards = parts[1].split(protocol.Command.LIST_SEPERATOR);
            view.showHand(cards);
        }
    }

    private void handleStock(String[] parts) {
        if (parts.length >= 3) {
            String player = parts[1];
            String topCard = parts[2];
            view.showStockPile(player, topCard);
        }
    }

    private void handleTable(String[] parts) {
        if (parts.length >= 3) {
            // Building piles
            String[] buildingPiles = parts[1].split("\\" + protocol.Command.VALUE_SEPERATOR);

            // Player tables
            String[] playerTables = parts[2].split(protocol.Command.LIST_SEPERATOR);
            String[][] playerDiscardPiles = new String[playerTables.length][];
            for (int i = 0; i < playerTables.length; i++) {
                playerDiscardPiles[i] = playerTables[i].split("\\" + protocol.Command.VALUE_SEPERATOR);
            }

            view.showTableState(buildingPiles, playerDiscardPiles);
        }
    }

    private void handlePlay(String[] parts) {
        if (parts.length >= 4) {
            String player = parts[1];
            String from = parts[2];
            String to = parts[3];
            view.showPlay(player, from, to);
        }
    }

    private void handleWinner(String[] parts) {
        if (parts.length >= 2) {
            String[] scoreData = parts[1].split(protocol.Command.LIST_SEPERATOR);
            String[] formattedScores = new String[scoreData.length];

            for (int i = 0; i < scoreData.length; i++) {
                String[] scoreParts = scoreData[i].split("\\" + protocol.Command.VALUE_SEPERATOR);
                if (scoreParts.length >= 2) {
                    formattedScores[i] = scoreParts[0] + ": " + scoreParts[1] + " points";
                }
            }

            view.showWinner(formattedScores);
            inGame = false;
        }
    }

    private void handleError(String[] parts) {
        if (parts.length >= 2) {
            String errorCode = parts[1];
            String errorMessage = (view instanceof TextualClientView)
                ? ((TextualClientView) view).getErrorMessage(errorCode)
                : "Error " + errorCode;
            view.showError(errorCode, errorMessage);
        }
    }

    public void sendHello(String name) {
        this.playerName = name;
        protocol.client.Hello hello = new protocol.client.Hello(
            name,
            new Feature[0]
        );
        sendMessage(hello.transformToProtocolString());
    }

    public void sendGame(int numberOfPlayers) {
        protocol.client.Game game = new protocol.client.Game(numberOfPlayers);
        sendMessage(game.transformToProtocolString());
    }

    public void sendPlay(String from, String to) {
        try {
            Position fromPos = parsePositionString(from);
            Position toPos = parsePositionString(to);

            if (fromPos != null && toPos != null) {
                protocol.client.Play play = new protocol.client.Play(fromPos, toPos);
                sendMessage(play.transformToProtocolString());
            } else {
                view.showInvalidPositionFormat();
            }
        } catch (Exception e) {
            view.showPlaySendError(e.getMessage());
        }
    }

    public void sendEnd() {
        protocol.client.End end = new protocol.client.End();
        sendMessage(end.transformToProtocolString());
    }

    public void sendHandRequest() {
        protocol.client.Hand hand = new protocol.client.Hand();
        sendMessage(hand.transformToProtocolString());
    }

    public void sendTableRequest() {
        protocol.client.Table table = new protocol.client.Table();
        sendMessage(table.transformToProtocolString());
    }

    private Position parsePositionString(String posStr) {
        try {
            String[] parts = posStr.split("\\.");
            String type = parts[0].toUpperCase();

            switch (type) {
                case "S":
                    return new StockPilePosition();
                case "H":
                    if (parts.length > 1) {
                        protocol.common.Card card = parseCard(parts[1]);
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

    private protocol.common.Card parseCard(String cardStr) {
        try {
            if (cardStr.equalsIgnoreCase("SB")) {
                return new protocol.common.Card();
            } else {
                int number = Integer.parseInt(cardStr);
                return new protocol.common.Card(number);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    public void run() {
        String name = view.promptPlayerName();
        if (name == null) {
            return;
        }

        sendHello(name);

        // Wait a bit for server response
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ignore
        }

        int numPlayers = view.promptNumberOfPlayers();
        if (numPlayers == -1) {
            return;
        }
        sendGame(numPlayers);

        // Main command loop
        while (connected) {
            String input = view.readCommand();

            if (input.isEmpty()) {
                continue;
            }

            String[] parts = input.split("\\s+");
            String command = parts[0].toLowerCase();

            switch (command) {
                case "play":
                    if (parts.length >= 3) {
                        sendPlay(parts[1], parts[2]);
                    } else {
                        view.showUsage("play <from> <to>");
                    }
                    break;
                case "hand":
                    sendHandRequest();
                    break;
                case "table":
                    sendTableRequest();
                    break;
                case "help":
                    view.showHelp();
                    break;
                case "quit":
                case "exit":
                    disconnect();
                    return;
                default:
                    view.showUnknownCommand();
            }
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 8888;

        if (args.length >= 1) {
            host = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        ClientView view = new TextualClientView();
        Client client = new Client(host, port, view);
        if (client.connect()) {
            client.run();
        }
    }
}
