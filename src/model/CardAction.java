package model;

public interface CardAction {
    // executes the action
    void execute(Game game, Player player);

    // Checks if the action is valid according to the game rules.
    boolean isValid (Game game, Player player);
}
