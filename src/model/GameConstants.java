package model;

/**
 * Constants for Skip-Bo game rules
 */
public class GameConstants {
    /** Number of building piles (shared) */
    public static final int NUM_BUILDING_PILES = 4;

    /** Number of discard piles per player */
    public static final int NUM_DISCARD_PILES = 4;

    /** Number of cards in hand */
    public static final int HAND_SIZE = 5;

    /** Minimum number of players */
    public static final int MIN_PLAYERS = 2;

    /** Maximum number of players */
    public static final int MAX_PLAYERS = 6;

    /** Size of building pile when full */
    public static final int BUILDING_PILE_FULL_SIZE = 12;

    /** Stock pile size for 2-4 players */
    public static final int STOCK_SIZE_SMALL_GAME = 30;

    /** Stock pile size for 5-6 players */
    public static final int STOCK_SIZE_LARGE_GAME = 20;

    // Private constructor to prevent instantiation
    private GameConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
