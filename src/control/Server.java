package control;

import model.Game;
import model.Player;
import protocol.common.Feature;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private int port;
    private ServerSocket serverSocket;
    private Map<String, ClientHandler> connectedClients;
    private Map<Integer, List<ClientHandler>> gameQueues;
    private Map<Game, List<ClientHandler>> activeGames;
    private boolean running;

    public Server(int port) {
        this.port = port;
        this.connectedClients = new ConcurrentHashMap<>();
        this.gameQueues = new ConcurrentHashMap<>();
        this.activeGames = new ConcurrentHashMap<>();
        this.running = false;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    new Thread(handler).start();
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
    }

    public synchronized boolean registerClient(String playerName, ClientHandler handler) {
        if (connectedClients.containsKey(playerName)) {
            return false;
        }
        connectedClients.put(playerName, handler);

        // Broadcast WELCOME to all connected clients
        protocol.server.Welcome welcome = new protocol.server.Welcome(
            playerName,
            new Feature[0]
        );
        broadcast(welcome.transformToProtocolString(), handler);

        return true;
    }

    public synchronized void unregisterClient(String playerName) {
        ClientHandler handler = connectedClients.remove(playerName);

        if (handler != null) {
            // Remove from any game queue
            for (List<ClientHandler> queue : gameQueues.values()) {
                queue.remove(handler);
            }

            // Handle disconnection from active game
            for (Map.Entry<Game, List<ClientHandler>> entry : activeGames.entrySet()) {
                List<ClientHandler> players = entry.getValue();
                if (players.contains(handler)) {
                    // Notify all players in the game about disconnection
                    protocol.server.Error error = new protocol.server.Error(
                        protocol.common.ErrorCode.PLAYER_DISCONNECTED
                    );
                    broadcastToGame(entry.getKey(), error.transformToProtocolString());

                    // Remove the game
                    activeGames.remove(entry.getKey());
                    break;
                }
            }
        }
    }

    public synchronized void addToGameQueue(int numberOfPlayers, ClientHandler handler) {
        if (!gameQueues.containsKey(numberOfPlayers)) {
            gameQueues.put(numberOfPlayers, new ArrayList<>());
        }

        List<ClientHandler> queue = gameQueues.get(numberOfPlayers);

        if (!queue.contains(handler)) {
            queue.add(handler);

            // Send QUEUE confirmation
            protocol.server.Queue queueCmd = new protocol.server.Queue();
            handler.sendMessage(queueCmd.transformToProtocolString());

            // Check if we can start a game
            if (queue.size() >= numberOfPlayers) {
                startGame(numberOfPlayers);
            }
        }
    }

    private synchronized void startGame(int numberOfPlayers) {
        List<ClientHandler> queue = gameQueues.get(numberOfPlayers);

        if (queue == null || queue.size() < numberOfPlayers) {
            return;
        }

        // Get the first N players from the queue
        List<ClientHandler> gamePlayers = new ArrayList<>();
        for (int i = 0; i < numberOfPlayers; i++) {
            gamePlayers.add(queue.remove(0));
        }

        // Create Player objects
        List<Player> players = new ArrayList<>();
        String[] playerNames = new String[numberOfPlayers];

        for (int i = 0; i < numberOfPlayers; i++) {
            String name = gamePlayers.get(i).getPlayerName();
            players.add(new Player(name));
            playerNames[i] = name;
        }

        // Create the game
        Game game = new Game(players);
        activeGames.put(game, gamePlayers);

        // Set the game for each handler
        for (int i = 0; i < gamePlayers.size(); i++) {
            gamePlayers.get(i).setGame(game, players.get(i));
        }

        // Send START command to all players
        protocol.server.Start start = new protocol.server.Start(playerNames);
        String startMsg = start.transformToProtocolString();
        for (ClientHandler handler : gamePlayers) {
            handler.sendMessage(startMsg);
        }

        // Send initial game state
        sendGameState(game);
    }

    public void sendGameState(Game game) {
        List<ClientHandler> handlers = activeGames.get(game);
        if (handlers == null) {
            return;
        }

        // Send TURN to all players
        Player currentPlayer = game.getCurrentPlayer();
        protocol.server.Turn turn = new protocol.server.Turn(currentPlayer.getName());
        broadcastToGame(game, turn.transformToProtocolString());

        // Send HAND to each player individually
        for (ClientHandler handler : handlers) {
            sendHandToPlayer(game, handler);
        }

        // Send STOCK for all players
        for (Player player : game.getPlayers()) {
            model.Card topCard = game.getStockPile(player).topCard();
            String cardStr = topCard != null ? convertCardToProtocol(topCard) : null;

            protocol.server.Stock stock = new protocol.server.Stock(
                player.getName(),
                cardStr
            );
            broadcastToGame(game, stock.transformToProtocolString());
        }
    }

    private void sendHandToPlayer(Game game, ClientHandler handler) {
        Player player = handler.getPlayer();
        List<model.Card> hand = game.getHand(player);

        String[] cardStrings = new String[hand.size()];
        for (int i = 0; i < hand.size(); i++) {
            cardStrings[i] = convertCardToProtocol(hand.get(i));
        }

        protocol.server.Hand handCmd = new protocol.server.Hand(cardStrings);
        handler.sendMessage(handCmd.transformToProtocolString());
    }

    public void broadcastToGame(Game game, String message) {
        List<ClientHandler> handlers = activeGames.get(game);
        if (handlers != null) {
            for (ClientHandler handler : handlers) {
                handler.sendMessage(message);
            }
        }
    }

    public void broadcast(String message, ClientHandler exclude) {
        for (ClientHandler handler : connectedClients.values()) {
            if (handler != exclude) {
                handler.sendMessage(message);
            }
        }
    }

    public String convertCardToProtocol(model.Card card) {
        if (card == null) {
            return "X";
        }

        if (card.isSkipBo()) {
            return "SB";
        }

        return String.valueOf(card.getNumber());
    }

    public static void main(String[] args) {
        int port = 8888;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        Server server = new Server(port);
        server.start();
    }
}
