package control;

import model.Game;
import model.Player;
import protocol.common.Feature;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server for Skip-Bo multiplayer game.
 * Manages client connections, game queues, and active games.
 */
public class Server {
    private int port;
    private ServerSocket serverSocket;
    private boolean running;

    // Connected clients: playerName -> ClientHandler
    private Map<String, ClientHandler> connectedClients;

    // Game queues: numberOfPlayers -> list of waiting clients
    // Example: 2 -> [client1, client2] means 2 clients waiting for a 2-player game
    private Map<Integer, List<ClientHandler>> gameQueues;

    // Active games: Game -> list of clients in that game
    private Map<Game, List<ClientHandler>> activeGames;

    public Server(int port) {
        this.port = port;
        this.connectedClients = new ConcurrentHashMap<>();
        this.gameQueues = new ConcurrentHashMap<>();
        this.activeGames = new ConcurrentHashMap<>();
        this.running = false;
    }

    /**
     * Start the server and listen for client connections.
     * This method blocks until the server is stopped.
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Server started on port " + port);

            // Accept client connections in a loop
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    // Create a handler for this client and start it in a new thread
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

    /**
     * Stop the server.
     */
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

    /**
     * Register a new client with a player name.
     * Returns false if the name is already taken.
     */
    public synchronized boolean registerClient(String playerName, ClientHandler handler) {
        // Check if name is already in use
        if (connectedClients.containsKey(playerName)) {
            return false;
        }

        // Add to connected clients
        connectedClients.put(playerName, handler);

        // Broadcast WELCOME message to all other clients
        protocol.server.Welcome welcome = new protocol.server.Welcome(
            playerName,
            new Feature[0]
        );
        broadcast(welcome.transformToProtocolString(), handler);

        return true;
    }

    /**
     * Unregister a client (called when they disconnect).
     */
    public synchronized void unregisterClient(String playerName) {
        ClientHandler handler = connectedClients.remove(playerName);

        if (handler != null) {
            // Remove from any game queue they might be in
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

    /**
     * Add a client to a game queue.
     * When enough players are in the queue, a game starts automatically.
     */
    public synchronized void addToGameQueue(int numberOfPlayers, ClientHandler handler) {
        // Get or create the queue for this number of players
        if (!gameQueues.containsKey(numberOfPlayers)) {
            gameQueues.put(numberOfPlayers, new ArrayList<>());
        }

        List<ClientHandler> queue = gameQueues.get(numberOfPlayers);

        // Add client to queue if not already in it
        if (!queue.contains(handler)) {
            queue.add(handler);

            // Send QUEUE confirmation to client
            protocol.server.Queue queueCmd = new protocol.server.Queue();
            handler.sendMessage(queueCmd.transformToProtocolString());

            // Check if we have enough players to start a game
            if (queue.size() >= numberOfPlayers) {
                startGame(numberOfPlayers);
            }
        }
    }

    /**
     * Start a game with the specified number of players.
     * Takes players from the front of the queue.
     */
    private synchronized void startGame(int numberOfPlayers) {
        List<ClientHandler> queue = gameQueues.get(numberOfPlayers);

        // Check if we have enough players
        if (queue == null || queue.size() < numberOfPlayers) {
            return;
        }

        // Take the first N players from the queue
        List<ClientHandler> gamePlayers = new ArrayList<>();
        for (int i = 0; i < numberOfPlayers; i++) {
            gamePlayers.add(queue.remove(0));  // Remove from front of queue
        }

        // Create Player objects for the game
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

        // Tell each handler about their game and player
        for (int i = 0; i < gamePlayers.size(); i++) {
            gamePlayers.get(i).setGame(game, players.get(i));
        }

        // Send START command to all players
        protocol.server.Start start = new protocol.server.Start(playerNames);
        String startMsg = start.transformToProtocolString();
        for (ClientHandler handler : gamePlayers) {
            handler.sendMessage(startMsg);
        }

        // Send initial game state to all players
        sendGameState(game);
    }

    /**
     * Send the current game state to all players in a game.
     * This includes:
     * - Whose turn it is
     * - Each player's hand
     * - Each player's stock pile top card
     */
    public void sendGameState(Game game) {
        List<ClientHandler> handlers = activeGames.get(game);
        if (handlers == null) {
            return;
        }

        // Send TURN message to all players
        Player currentPlayer = game.getCurrentPlayer();
        protocol.server.Turn turn = new protocol.server.Turn(currentPlayer.getName());
        broadcastToGame(game, turn.transformToProtocolString());

        // Send HAND to each player individually (only they should see their hand)
        for (ClientHandler handler : handlers) {
            sendHandToPlayer(game, handler);
        }

        // Send STOCK for all players (everyone can see everyone's stock pile)
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

    /**
     * Send a player's hand to them (private information).
     */
    private void sendHandToPlayer(Game game, ClientHandler handler) {
        Player player = handler.getPlayer();
        List<model.Card> hand = game.getHand(player);

        // Convert cards to protocol format
        String[] cardStrings = new String[hand.size()];
        for (int i = 0; i < hand.size(); i++) {
            cardStrings[i] = convertCardToProtocol(hand.get(i));
        }

        // Send to this player only
        protocol.server.Hand handCmd = new protocol.server.Hand(cardStrings);
        handler.sendMessage(handCmd.transformToProtocolString());
    }

    /**
     * Broadcast a message to all players in a specific game.
     */
    public void broadcastToGame(Game game, String message) {
        List<ClientHandler> handlers = activeGames.get(game);
        if (handlers != null) {
            for (ClientHandler handler : handlers) {
                handler.sendMessage(message);
            }
        }
    }

    /**
     * Broadcast a message to all connected clients except one.
     */
    public void broadcast(String message, ClientHandler exclude) {
        for (ClientHandler handler : connectedClients.values()) {
            if (handler != exclude) {
                handler.sendMessage(message);
            }
        }
    }

    /**
     * Convert a game card to protocol format.
     * null -> "X" (empty)
     * Skip-Bo card -> "SB"
     * Number card -> "5" (the number)
     */
    public String convertCardToProtocol(model.Card card) {
        if (card == null) {
            return "X";
        }

        if (card.isSkipBo()) {
            return "SB";
        }

        return String.valueOf(card.getNumber());
    }

    /**
     * Main method - start the server.
     */
    public static void main(String[] args) {
        int port = 8888;

        // Read port from command line arguments
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + port);
            }
        }

        // Create and start server
        Server server = new Server(port);
        server.start();
    }
}
