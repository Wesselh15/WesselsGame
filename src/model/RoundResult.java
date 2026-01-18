package model;

import java.util.HashMap;
import java.util.Map;

/**
 * Bevat het resultaat van een afgelopen ronde
 * Wordt gebruikt voor multi-round scoring systeem
 */
public class RoundResult {
    public final Player roundWinner;        // Wie deze ronde heeft gewonnen
    public final int pointsScored;          // Hoeveel punten de winnaar kreeg
    public final Map<Player, Integer> allScores;  // Totale scores van alle spelers
    public final boolean gameOver;          // Is het hele spel afgelopen? (>= 500 punten)
    public final Player overallWinner;      // Overall winnaar (null als game niet over is)

    /**
     * Maakt een nieuw RoundResult object
     *
     * @param roundWinner De winnaar van deze ronde
     * @param pointsScored Aantal punten dat de winnaar verdient
     * @param allScores Map met alle spelers en hun totale scores
     * @param gameOver Of het hele spel nu afgelopen is
     * @param overallWinner De overall winnaar (null als game niet over is)
     */
    public RoundResult(Player roundWinner, int pointsScored,
                       Map<Player, Integer> allScores,
                       boolean gameOver, Player overallWinner) {
        this.roundWinner = roundWinner;
        this.pointsScored = pointsScored;
        this.allScores = new HashMap<>(allScores);  // Maak kopie voor veiligheid
        this.gameOver = gameOver;
        this.overallWinner = overallWinner;
    }
}
