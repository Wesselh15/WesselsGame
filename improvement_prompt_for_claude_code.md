# Skip-Bo Game Verbetering Prompt voor Claude Code

**Niveau:** Junior Developer  
**Doel:** Game werkend maken met multi-round scoring systeem

---

## 🎯 OVERZICHT VAN TAKEN

1. ✅ Verwijder dode code
2. ✅ Versimpel GameManager (te complex voor junior)
3. ✅ Verplaats game logica naar Game class
4. ✅ Implementeer multi-round scoring systeem (500 punten om te winnen)
5. ✅ Maak AI slimmer (geen invalid moves meer)
6. ✅ Test en valideer alles werkt

---

## 📋 TAAK 1: VERWIJDER DODE CODE

### Locaties van dode code:

**Game.java:**
- Geen dode code meer! (scores en round zijn al verwijderd)

**GameManager.java:**
- `parseFeatures()` method wordt gebruikt maar kan simpeler
- Veel duplicate error handling code

**Player.java:**
- Check of er ongebruikte fields zijn

### Instructies:
```
1. Scan alle files op unused imports
2. Scan alle files op unused methods
3. Scan alle files op unused variables
4. Verwijder alles wat niet gebruikt wordt
5. Document wat je hebt verwijderd in comments
```

---

## 📋 TAAK 2: VERSIMPEL GAMEMANAGER

### Huidige problemen:
- GameManager is 545 regels (TE LANG!)
- Bevat game logica (moet in Game class)
- Bevat complex error handling
- Moeilijk te begrijpen voor junior

### Vereenvoudigingen:

**Split GameManager in 2 classes:**

1. **GameManager** - Alleen lobby en game lifecycle:
   - addPlayer()
   - setRequiredPlayers()
   - startGame()
   - endGame()
   - removePlayer()

2. **GameController** (NIEUW) - Protocol naar Game vertaling:
   - handleMove()
   - handleEndTurn()
   - sendGameState()
   - sendStockCard()
   - sendHand()

**Voorbeeld structuur:**
```java
// GameManager.java (SIMPLIFIED)
public class GameManager {
    private GameController gameController;
    private List<String> playerNames;
    private List<ClientHandler> clients;
    
    public void addPlayer(String name, ClientHandler client) {
        // Simple: add to list, check if enough players, maybe start
    }
    
    public void startGame() {
        // Simple: create Game, create GameController, start
    }
}

// GameController.java (NEW)
public class GameController {
    private Game game;
    private Server server;
    
    public void handleMove(String playerName, Position from, Position to) {
        // Convert protocol to game action
        // Ask game to execute
        // Broadcast result
    }
}
```

### Instructies:
```
1. Maak nieuwe file: controller/GameController.java
2. Verplaats alle handleMove/handleEndTurn/sendGameState methods naar GameController
3. Laat GameManager ALLEEN lobby management doen
4. Update alle references
5. Test dat alles nog werkt
```

---

## 📋 TAAK 3: VERPLAATS GAME LOGICA NAAR GAME CLASS

### Wat moet naar Game.java:

**Uit GameManager naar Game:**

1. **Position naar CardAction conversie:**
```java
// Dit zit nu in GameManager.positionToAction()
// Moet naar Game.createMoveAction(Position from, Position to, Player player)
```

2. **Win check logica:**
```java
// hasPlayerWon() is al in Game ✅
// Maar calculateScore() moet toegevoegd!
```

3. **Scoring system** (NIEUW!):
```java
// Game.java TOEVOEGEN:
public int calculateRoundScore(Player winner) {
    // Winner krijgt: 
    // - 25 punten voor winnen
    // - 5 punten per kaart in opponent stock piles
    
    int score = 25;  // Win bonus
    for (Player opponent : players) {
        if (opponent != winner) {
            score += getStockPile(opponent).size() * 5;
        }
    }
    return score;
}

public void addScore(Player player, int points) {
    // Add to player's total score
}

public int getScore(Player player) {
    // Get player's current score
}

public Player getOverallWinner() {
    // Return player with >= 500 points, or null
}
```

### Instructies:
```
1. Voeg scoring fields toe aan Game.java:
   - Map<Player, Integer> totalScores
   
2. Implementeer scoring methods:
   - calculateRoundScore(Player winner)
   - addScore(Player player, int points)
   - getScore(Player player)
   - getOverallWinner()
   
3. Verplaats positionToAction() naar Game class:
   - Rename naar createMoveAction()
   - Return CardAction
   
4. Update GameController om nieuwe methods te gebruiken

5. Document alle nieuwe methods met duidelijke comments
```

---

## 📋 TAAK 4: IMPLEMENTEER MULTI-ROUND SCORING

### Skip-Bo Regels (uit regels_skipbo.pdf):

**Scoring:**
- Winner van round: **25 punten**
- Per kaart in opponent stock pile: **5 punten**
- Eerste speler tot **500 punten** wint overall game

**Voorbeeld:**
```
Round 1:
- Alice wint (stock pile leeg)
- Bob heeft nog 15 kaarten in stock pile
- Charlie heeft nog 20 kaarten in stock pile

Alice's score = 25 + (15 * 5) + (20 * 5) = 25 + 75 + 100 = 200 punten

Round 2:
Alice: 200 punten (van round 1)
Bob wint deze round, krijgt 180 punten
Bob: 180 punten totaal
Alice: 200 punten totaal

... speel door tot iemand >= 500 punten heeft
```

### Implementatie Plan:

**Game.java:**
```java
public class Game {
    private Map<Player, Integer> totalScores;  // ADD THIS
    private int roundNumber;  // ADD THIS
    
    public Game(List<Player> players) {
        // ... existing code ...
        
        // ADD:
        this.totalScores = new HashMap<>();
        for (Player p : players) {
            totalScores.put(p, 0);
        }
        this.roundNumber = 1;
    }
    
    // ADD METHOD:
    public RoundResult finishRound(Player winner) {
        int score = calculateRoundScore(winner);
        addScore(winner, score);
        
        Player overallWinner = getOverallWinner();
        boolean gameOver = (overallWinner != null);
        
        return new RoundResult(winner, score, totalScores, gameOver, overallWinner);
    }
    
    // ADD METHOD:
    public void startNewRound() {
        // Reset for new round but keep scores!
        roundNumber++;
        
        // Regenerate cards
        drawPile = CardGenerator.generateCards();
        Collections.shuffle(drawPile);
        
        // Reset building piles
        buildingPiles.clear();
        for (int i = 0; i < NUM_BUILDING_PILES; i++) {
            buildingPiles.add(new BuildingPile());
        }
        
        // Reset stock piles
        int cardsToHandout = players.size() <= 4 ? STOCK_SIZE_SMALL_GAME : STOCK_SIZE_LARGE_GAME;
        for (Player player : players) {
            List<Card> handOut = new ArrayList<>(drawPile.subList(0, cardsToHandout));
            stockPiles.put(player, new StockPile(handOut));
            hand.get(player).clear();
            drawPile.removeAll(handOut);
            
            // Reset discard piles
            for (DiscardPile dp : discardPiles.get(player)) {
                dp.clear();
            }
        }
        
        // Random first player for new round
        Random r = new Random();
        currentPlayerIndex = r.nextInt(players.size());
        handCards(players.get(currentPlayerIndex));
    }
}

// NEW CLASS: model/RoundResult.java
public class RoundResult {
    public final Player roundWinner;
    public final int pointsScored;
    public final Map<Player, Integer> allScores;
    public final boolean gameOver;
    public final Player overallWinner;
    
    public RoundResult(Player roundWinner, int pointsScored, 
                       Map<Player, Integer> allScores,
                       boolean gameOver, Player overallWinner) {
        this.roundWinner = roundWinner;
        this.pointsScored = pointsScored;
        this.allScores = new HashMap<>(allScores);
        this.gameOver = gameOver;
        this.overallWinner = overallWinner;
    }
}
```

**GameController.java:**
```java
public void handleMove(String playerName, Position from, Position to) {
    // ... existing move handling ...
    
    // Check if player won THIS ROUND
    if (game.hasPlayerWon(player)) {
        RoundResult result = game.finishRound(player);
        announceRoundWinner(result);
        
        if (result.gameOver) {
            announceOverallWinner(result.overallWinner);
            endGame();
        } else {
            // Start new round
            game.startNewRound();
            announceNewRound();
        }
    }
}

private void announceRoundWinner(RoundResult result) {
    // Create score list for protocol
    List<Winner.Score> scores = new ArrayList<>();
    for (Player p : game.getPlayers()) {
        int score = result.allScores.get(p);
        scores.add(new Winner.Score(p.getName(), score));
    }
    
    Winner.Score[] scoreArray = scores.toArray(new Winner.Score[0]);
    String msg = new Winner(scoreArray).transformToProtocolString();
    server.broadcast(msg);
    
    System.out.println("Round " + game.getRoundNumber() + " winner: " + 
                       result.roundWinner.getName() + 
                       " (+" + result.pointsScored + " points)");
}

private void announceNewRound() {
    // Use Round protocol message
    String msg = new Round(game.getRoundNumber()).transformToProtocolString();
    server.broadcast(msg);
    
    // Send new game state
    sendGameStateToAll();
    
    // Announce whose turn it is
    Player currentPlayer = game.getCurrentPlayer();
    String turnMsg = new Turn(currentPlayer.getName()).transformToProtocolString();
    server.broadcast(turnMsg);
}
```

### Protocol Messages voor Multi-Round:

**Round.java** (bestaat al!):
```java
// protocol/server/Round.java
public class Round implements Command {
    public static final String COMMAND = "ROUND";
    public int roundNumber;
    
    public Round(int roundNumber) {
        this.roundNumber = roundNumber;
    }
    
    @Override
    public String transformToProtocolString() {
        return COMMAND + SEPERATOR + roundNumber;
    }
}
```

**Winner.java** (bestaat al!):
```java
// Used for BOTH round winners and overall winner
// Score array contains ALL players' TOTAL scores
```

### Instructies:
```
1. Voeg totalScores en roundNumber toe aan Game.java

2. Implementeer scoring methods:
   - calculateRoundScore(Player winner)
   - addScore(Player player, int points)
   - getScore(Player player)
   - getOverallWinner()

3. Implementeer round management:
   - finishRound(Player winner) → returns RoundResult
   - startNewRound()

4. Maak RoundResult class in model package

5. Update GameController om rounds te handlen:
   - announceRoundWinner(RoundResult)
   - announceNewRound()
   - Check for overall winner

6. Voeg DiscardPile.clear() method toe:
   public void clear() {
       cards.clear();
   }

7. Test multi-round flow:
   - Round 1 winner gets points
   - New round starts automatically
   - Scores carry over
   - Game ends at 500 points
```

---

## 📋 TAAK 5: MAAK AI SLIMMER

### Huidige Probleem:

AI doet random moves die vaak invalid zijn:
```java
// Huidige AI:
for (int i = 0; i < maxMoves; i++) {
    int buildingPile = random.nextInt(4);
    String move = "PLAY~S~B." + buildingPile;  // Random pile!
    sendMessage(move);
}
```

**Waarom dit faalt:**
- Building pile 0 verwacht kaart "3"
- Stock top card is "7"
- Move is invalid!

### Nieuwe AI Strategie:

**Slimme AI moet:**
1. Weten wat building piles verwachten
2. Weten wat stock top card is
3. Alleen valide moves proberen

**Implementatie:**

```java
// AIClient.java - ADD FIELDS:
private String stockTopCard;  // What's on top of my stock
private String[] buildingPileNext;  // What each building pile expects (1-12 or X if full)

// AIClient.java - UPDATE handleMessage():
private void handleMessage(String message) {
    String[] parts = message.split("~");
    String command = parts[0];
    
    if (command.equals("STOCK")) {
        if (parts.length >= 3 && parts[1].equals(playerName)) {
            stockTopCard = parts[2];  // Update my stock top card
            System.out.println("[AI " + playerName + "] Stock top: " + stockTopCard);
        }
    } else if (command.equals("TABLE")) {
        updateTableInfo(parts);
    }
    // ... rest of handlers
}

// AIClient.java - NEW METHOD:
private void updateTableInfo(String[] parts) {
    // TABLE protocol: TABLE~players~B.0~B.1~B.2~B.3
    // Find building pile values
    if (parts.length >= 6) {
        buildingPileNext = new String[4];
        buildingPileNext[0] = parts[2];  // B.0
        buildingPileNext[1] = parts[3];  // B.1
        buildingPileNext[2] = parts[4];  // B.2
        buildingPileNext[3] = parts[5];  // B.3
        
        System.out.println("[AI " + playerName + "] Building piles: " + 
                          String.join(", ", buildingPileNext));
    }
}

// AIClient.java - SMART playTurn():
private void playTurn() {
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // ✅ SMART STRATEGY: Try stock pile first
    int movesPlayed = 0;
    int maxMoves = random.nextInt(3) + 1;
    
    for (int i = 0; i < maxMoves && stockTopCard != null; i++) {
        // Find a building pile that can accept our stock top card
        int validPile = findValidBuildingPile(stockTopCard);
        
        if (validPile >= 0) {
            String move = "PLAY~S~B." + validPile;
            sendMessage(move);
            System.out.println("[AI " + playerName + "] Smart stock move: " + move);
            movesPlayed++;
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            // No valid pile for stock card, stop trying
            System.out.println("[AI " + playerName + "] No valid move for stock card " + stockTopCard);
            break;
        }
    }
    
    // Discard to end turn
    if (!hand.isEmpty()) {
        String card = hand.get(0);
        int discardPile = random.nextInt(4);
        
        String discardMove = "PLAY~H." + card + "~D." + discardPile;
        sendMessage(discardMove);
        System.out.println("[AI " + playerName + "] Discarding: " + discardMove);
        
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    sendMessage("END");
    myTurn = false;
}

// AIClient.java - NEW METHOD:
private int findValidBuildingPile(String cardStr) {
    if (buildingPileNext == null || cardStr == null) {
        return -1;
    }
    
    // Skip-Bo can go anywhere (except full piles)
    if (cardStr.equals("SB")) {
        for (int i = 0; i < 4; i++) {
            if (!buildingPileNext[i].equals("X")) {  // X means full
                return i;
            }
        }
        return -1;
    }
    
    // Try to parse card number
    try {
        int cardNum = Integer.parseInt(cardStr);
        
        // Find pile that expects this card
        for (int i = 0; i < 4; i++) {
            String expected = buildingPileNext[i];
            if (expected.equals("X")) continue;  // Pile is full
            
            int expectedNum = Integer.parseInt(expected);
            if (cardNum == expectedNum) {
                return i;  // Found valid pile!
            }
        }
    } catch (NumberFormatException e) {
        // Invalid card format
        return -1;
    }
    
    return -1;  // No valid pile found
}
```

### Instructies:
```
1. Voeg fields toe aan AIClient:
   - stockTopCard (String)
   - buildingPileNext (String[])

2. Update handleMessage() om STOCK en TABLE te parsen

3. Implementeer updateTableInfo(String[] parts)

4. Implementeer findValidBuildingPile(String cardStr)

5. Update playTurn() om smart moves te doen:
   - Check wat building piles verwachten
   - Alleen probeer moves die valide zijn
   - Stop als geen valide moves

6. Test dat AI geen invalid moves meer doet

7. Optioneel: Voeg logging toe om AI decisions te zien
```

---

## 📋 TAAK 6: TEST EN VALIDEER

### Test Scenario's:

**Test 1: Single Round (Basic)**
```
1. Start server
2. Connect 2 clients (1 human, 1 AI)
3. Start game with GAME~2
4. Play until one wins
5. Verify:
   - Winner announced with correct score
   - Game ends (no new round)
```

**Test 2: Multi-Round to 500**
```
1. Start server
2. Connect 2 clients
3. Play multiple rounds
4. Verify:
   - Each round winner gets points
   - Scores carry over
   - New round starts automatically
   - Game ends when someone hits 500
```

**Test 3: AI Plays Smart**
```
1. Start server
2. Connect 1 human + 1 AI
3. Observe AI turns
4. Verify:
   - AI only attempts valid moves
   - AI doesn't spam invalid moves
   - AI can actually make progress
```

**Test 4: Disconnect Handling**
```
1. Start game with 2 players
2. Disconnect one player mid-game
3. Verify:
   - Game ends gracefully
   - No crashes
   - Other player notified
```

### Instructies:
```
1. Compileer alle code

2. Run Test 1 (single round)
   - Document results
   - Fix any bugs

3. Run Test 2 (multi-round)
   - Document results
   - Fix any bugs

4. Run Test 3 (AI smart)
   - Document results
   - Fix any bugs

5. Run Test 4 (disconnect)
   - Document results
   - Fix any bugs

6. Create test report met:
   - Wat werkt
   - Wat niet werkt
   - Bugs gevonden
   - Bugs gefixed
```

---

## 🎯 DELIVERABLES

Na voltooiing moet je hebben:

1. ✅ **Cleaned Code**
   - Geen dode code
   - Alle imports clean
   - Alle unused methods weg

2. ✅ **Simplified GameManager**
   - < 200 regels
   - Alleen lobby management
   - GameController apart

3. ✅ **Game Logic in Game Class**
   - Scoring system geïmplementeerd
   - Multi-round support
   - Position→CardAction conversie

4. ✅ **Smart AI**
   - Geen invalid moves meer
   - Kan daadwerkelijk winnen
   - Duidelijke logging

5. ✅ **Test Report**
   - Alle scenarios getest
   - Bugs gedocumenteerd
   - Fixes gedocumenteerd

---

## 📝 EXTRA VERBETERINGEN (OPTIONEEL)

Als je tijd over hebt:

### 1. Better Error Messages
```java
// In plaats van:
throw new GameException("Invalid action");

// Gebruik:
throw new GameException("Cannot play card " + card + 
                       " on building pile " + pile + 
                       " (expects " + expected + ")");
```

### 2. Logging System
```java
// Add logger class voor better debugging
public class GameLogger {
    public static void logMove(Player player, String from, String to) {
        System.out.println("[GAME] " + player.getName() + ": " + from + " → " + to);
    }
    
    public static void logScore(Player player, int points, int total) {
        System.out.println("[SCORE] " + player.getName() + ": +" + points + " (total: " + total + ")");
    }
}
```

### 3. Input Validation Helper
```java
// Add to Game.java
public boolean isValidMove(Position from, Position to, Player player) {
    try {
        CardAction action = createMoveAction(from, to, player);
        return action != null && action.isValid(this, player);
    } catch (Exception e) {
        return false;
    }
}
```

---

## 🚀 VOLGORDE VAN UITVOERING

**Aanbevolen volgorde:**

1. **Eerst:** TAAK 1 (Verwijder dode code) - 10 min
2. **Daarna:** TAAK 3 (Game logica naar Game) - 30 min
3. **Dan:** TAAK 4 (Multi-round scoring) - 45 min
4. **Dan:** TAAK 2 (Versimpel GameManager) - 30 min
5. **Dan:** TAAK 5 (Slimme AI) - 30 min
6. **Laatste:** TAAK 6 (Test alles) - 30 min

**Totaal: ~3 uur werk**

---

## ✅ SUCCESS CRITERIA

Je bent klaar als:

1. ✅ Code compileert zonder errors
2. ✅ Game start en speelt
3. ✅ Multiple rounds werken
4. ✅ Scores worden bijgehouden
5. ✅ Iemand wint bij 500 punten
6. ✅ AI doet alleen valide moves
7. ✅ GameManager is < 200 regels
8. ✅ Game logica zit in Game class
9. ✅ Geen dode code
10. ✅ Alles is getest en werkt

---

## 📚 REFERENTIES

**Skip-Bo Regels:** Zie regels_skipbo.pdf
- Pagina 1: Stock pile setup (30 cards voor 2-4 players, 20 voor 5-6)
- Pagina 3: Scoring (25 + 5 per remaining card)
- Pagina 3: Multiple games to 500 points

**Protocol Documentatie:** Zie Skip-BO__1_.docx
- WINNER message met scores
- ROUND message voor nieuwe rounds
- TABLE message met building pile status

**Current Code:** Zie WesselsGame-master__7_.zip

---

Veel succes! Dit is veel werk maar alles is stap-voor-stap uitgelegd. 

Neem je tijd en test goed na elke stap! 🎯
