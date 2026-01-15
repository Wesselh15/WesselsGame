package control;

/**
 * Interface for client view operations.
 * This interface separates UI concerns from business logic.
 * Can be moved to a separate view package in the future.
 */
public interface ClientView {
    /**
     * Display a connection success message.
     */
    void showConnected(String host, int port);

    /**
     * Display a connection error message.
     */
    void showConnectionError(String message);

    /**
     * Display a disconnection error message.
     */
    void showDisconnectionError(String message);

    /**
     * Display a connection lost message.
     */
    void showConnectionLost(String message);

    /**
     * Display an unknown server message.
     */
    void showUnknownMessage(String message);

    /**
     * Display a message handling error.
     */
    void showMessageHandlingError(String message);

    /**
     * Display welcome message when a player joins.
     */
    void showPlayerJoined(String playerName);

    /**
     * Display message when added to game queue.
     */
    void showAddedToQueue();

    /**
     * Display game start information.
     */
    void showGameStarted(String[] playerNames);

    /**
     * Display turn notification.
     */
    void showTurn(String playerName, boolean isYourTurn);

    /**
     * Display player's hand.
     */
    void showHand(String[] cards);

    /**
     * Display stock pile information.
     */
    void showStockPile(String playerName, String topCard);

    /**
     * Display table state (building piles and discard piles).
     */
    void showTableState(String[] buildingPiles, String[][] playerDiscardPiles);

    /**
     * Display a play action.
     */
    void showPlay(String playerName, String from, String to);

    /**
     * Display winner and final scores.
     */
    void showWinner(String[] scores);

    /**
     * Display an error message.
     */
    void showError(String errorCode, String errorMessage);

    /**
     * Display invalid position format error.
     */
    void showInvalidPositionFormat();

    /**
     * Display play sending error.
     */
    void showPlaySendError(String message);

    /**
     * Display help information.
     */
    void showHelp();

    /**
     * Prompt for and read player name.
     * @return player name or null if invalid
     */
    String promptPlayerName();

    /**
     * Prompt for and read number of players.
     * @return number of players or -1 if invalid
     */
    int promptNumberOfPlayers();

    /**
     * Read next command from user.
     * @return command string
     */
    String readCommand();

    /**
     * Show usage message for a command.
     */
    void showUsage(String usage);

    /**
     * Show unknown command message.
     */
    void showUnknownCommand();
}
