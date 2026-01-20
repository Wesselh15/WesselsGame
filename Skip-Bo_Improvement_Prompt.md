# Skip-Bo Project Verbetering - Prompt voor Junior Java Developer

## Context
Je hebt een werkende Skip-Bo client-server implementatie met een basis AI-speler. De code voldoet aan de basisvereisten, maar er zijn aanzienlijke mogelijkheden voor verbetering in zowel de AIClient als de algemene code kwaliteit.

---

## 🎯 **Prioriteit 1: AIClient Strategie Verbeteren**

### Huidige Problemen in AIClient.java

#### 1. **Beperkte Spelstrategie**
**Probleem:** De AI speelt alleen kaarten van de stock pile en discard random.

```java
// HUIDIG - Te simpel
private void playTurn() {
    // Probeert alleen stock pile moves 1-3 keer
    for (int i = 0; i < maxMoves && stockTopCard != null; i++) {
        int validPile = findValidBuildingPile(stockTopCard);
        if (validPile >= 0) {
            String move = "PLAY~S~B." + validPile;
            sendMessage(move);
        }
    }
    // Gooit random kaart weg
    String discardMove = "PLAY~H." + card + "~D." + discardPile;
}
```

**Verbeteringen:**
1. **Gebruik je hand kaarten!** De AI negeert momenteel volledig zijn hand van 5 kaarten
2. **Speel van discard piles** als ze bruikbare top cards hebben
3. **Skip-Bo kaarten zijn waardevol** - gebruik ze strategisch
4. **Probeer meerdere kaarten te spelen** tot hand leeg is (en krijg 5 nieuwe kaarten!)

**Voorgestelde strategie:**
```java
// VERBETERD - Multi-source strategie
private void playTurn() {
    boolean madeMove = true;
    
    while (madeMove) {
        madeMove = false;
        
        // 1. HOOGSTE PRIORITEIT: Stock pile (om te winnen!)
        if (tryPlayStockPile()) {
            madeMove = true;
            continue;
        }
        
        // 2. Probeer hand kaarten (vooral Skip-Bo!)
        if (tryPlayFromHand()) {
            madeMove = true;
            continue;
        }
        
        // 3. Probeer discard pile tops
        if (tryPlayFromDiscardPiles()) {
            madeMove = true;
            continue;
        }
        
        // Als geen moves meer mogelijk, stop loop
    }
    
    // Discard strategisch (bewaar Skip-Bo kaarten!)
    discardStrategically();
    sendMessage("END");
}
```

#### 2. **Geen Tracking van Discard Piles**
**Probleem:** AI weet niet wat er op zijn eigen discard piles ligt.

**Oplossing:** Parse TABLE berichten correct:
```java
// TABLE~B.0.B.1.B.2.B.3~PLAYER.S.D.0.D.1.D.2.D.3,PLAYER2...
private void updateTableInfo(String[] parts) {
    if (parts.length >= 3) {
        // Building piles
        String[] buildingData = parts[1].split("\\.");
        for (int i = 0; i < 4 && i < buildingData.length; i++) {
            buildingPileNext[i] = buildingData[i];
        }
        
        // Player data - vind JOUW gegevens!
        String[] players = parts[2].split(",");
        for (String playerData : players) {
            String[] details = playerData.split("\\.");
            if (details[0].equals(playerName)) {
                stockTopCard = details[1];  // S
                myDiscardPiles[0] = details[2];  // D.0
                myDiscardPiles[1] = details[3];  // D.1
                myDiscardPiles[2] = details[4];  // D.2
                myDiscardPiles[3] = details[5];  // D.3
                break;
            }
        }
    }
}
```

#### 3. **Timing & Thread Safety**
**Problemen:**
- Hard-coded `Thread.sleep()` kan tot timeouts leiden (max 30 sec per turn!)
- Geen error handling voor InterruptedException
- Geen controle of berichten aangekomen zijn

**Verbeteringen:**
```java
// BETER - Configureerbare timing met limits
private static final long MOVE_DELAY_MS = 200;  // Sneller!
private static final long MAX_TURN_TIME_MS = 25_000;  // Safety margin

private void playTurn() {
    long turnStartTime = System.currentTimeMillis();
    
    while (madeMove && !isTimeExpired(turnStartTime)) {
        // Spel logica...
        
        if (madeMove) {
            sleepSafely(MOVE_DELAY_MS);
        }
    }
}

private boolean isTimeExpired(long startTime) {
    return (System.currentTimeMillis() - startTime) > MAX_TURN_TIME_MS;
}

private void sleepSafely(long millis) {
    try {
        Thread.sleep(millis);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        System.err.println("[AI " + playerName + "] Interrupted during sleep");
    }
}
```

---

## 🏗️ **Prioriteit 2: Architectuur & Design Patterns**

### 1. **Strategy Pattern voor AI**
**Probleem:** Alle AI logica zit in één grote klasse.

**Oplossing:** Maak verschillende AI strategieën:
```java
// Interface
public interface AIStrategy {
    List<Move> decideMoves(GameState state);
}

// Implementaties
public class AggressiveStrategy implements AIStrategy { }
public class ConservativeStrategy implements AIStrategy { }
public class SmartStrategy implements AIStrategy { }

// In AIClient
private AIStrategy strategy;

public AIClient(String host, int port, String name, AIStrategy strategy) {
    this.strategy = strategy;
    // ...
}
```

### 2. **State Pattern voor Client States**
**Huidige situatie:** Boolean flags (`myTurn`, `running`) zijn foutgevoelig.

**Beter:**
```java
public enum ClientState {
    DISCONNECTED,
    CONNECTED,
    IN_QUEUE,
    IN_GAME,
    MY_TURN,
    WAITING,
    GAME_OVER
}

private ClientState currentState = ClientState.DISCONNECTED;

// Met state transitions
private void transitionTo(ClientState newState) {
    System.out.println("[AI " + playerName + "] State: " 
        + currentState + " -> " + newState);
    this.currentState = newState;
}
```

### 3. **Model Classes voor Game State**
**Probleem:** Primitieve types en String arrays voor game state.

**Beter:**
```java
public class GameState {
    private final List<Card> hand;
    private final Card stockTop;
    private final Card[] discardTops;  // 4 piles
    private final BuildingPileState[] buildingPiles;  // 4 piles
    
    // Utility methods
    public List<Card> getPlayableCards() { }
    public List<Move> getPossibleMoves() { }
}

public class BuildingPileState {
    private int nextExpectedValue;  // 1-12 or 0 if empty/full
    
    public boolean accepts(Card card) {
        if (card.isSkipBo()) return nextExpectedValue > 0;
        return card.getValue() == nextExpectedValue;
    }
}
```

---

## 🧪 **Prioriteit 3: Testing & Validation**

### 1. **Unit Tests voor AI Logic**
```java
public class AIClientTest {
    @Test
    public void testFindValidBuildingPile_normalCard() {
        AIClient ai = new AIClient("localhost", 5555, "TestBot");
        ai.buildingPileNext = new String[]{"3", "7", "X", "1"};
        
        int pile = ai.findValidBuildingPile("7");
        assertEquals(1, pile);
    }
    
    @Test
    public void testFindValidBuildingPile_skipBo() {
        AIClient ai = new AIClient("localhost", 5555, "TestBot");
        ai.buildingPileNext = new String[]{"X", "5", "X", "X"};
        
        int pile = ai.findValidBuildingPile("SB");
        assertEquals(1, pile);  // Should find first non-full pile
    }
    
    @Test
    public void testStrategyChoosesOptimalMove() {
        // Test dat AI stock pile prioriteert boven hand
    }
}
```

### 2. **Protocol Message Validation**
```java
private void validateMessage(String message) throws ProtocolException {
    if (message == null || message.isEmpty()) {
        throw new ProtocolException("Empty message received");
    }
    
    String[] parts = message.split("~");
    if (parts.length == 0) {
        throw new ProtocolException("Invalid message format");
    }
    
    // Validate command
    String command = parts[0];
    if (!VALID_COMMANDS.contains(command)) {
        throw new ProtocolException("Unknown command: " + command);
    }
}
```

---

## 📝 **Prioriteit 4: Code Kwaliteit**

### 1. **Javadoc & Comments**
**SLECHT:**
```java
// Try stock pile moves (1-3 times)
int movesPlayed = 0;
```

**GOED:**
```java
/**
 * Attempts to play cards from the stock pile to building piles.
 * The stock pile is the highest priority source because emptying it wins the round.
 * 
 * @return true if at least one stock card was successfully played
 */
private boolean tryPlayStockPile() {
    // Implementation...
}
```

### 2. **Magic Numbers Elimineren**
**SLECHT:**
```java
for (int i = 0; i < 4; i++) {
    // ...
}
int discardPile = random.nextInt(4);
```

**GOED:**
```java
private static final int NUM_BUILDING_PILES = 4;
private static final int NUM_DISCARD_PILES = 4;

for (int i = 0; i < NUM_BUILDING_PILES; i++) {
    // ...
}
```

### 3. **Exception Handling**
**HUIDIG - Te algemeen:**
```java
} catch (IOException e) {
    System.out.println("Could not connect to server");
}
```

**BETER:**
```java
} catch (UnknownHostException e) {
    System.err.println("[AI " + playerName + "] Unknown host: " + host);
    // Mogelijk: probeer backup server
} catch (ConnectException e) {
    System.err.println("[AI " + playerName + "] Server not responding on port " + port);
    // Mogelijk: retry met backoff
} catch (IOException e) {
    System.err.println("[AI " + playerName + "] Connection error: " + e.getMessage());
}
```

---

## 🎓 **Prioriteit 5: Geavanceerde AI Strategieën**

### 1. **Minimax-achtige Evaluation**
```java
private Move chooseBestMove(List<Move> possibleMoves) {
    Move bestMove = null;
    int bestScore = Integer.MIN_VALUE;
    
    for (Move move : possibleMoves) {
        int score = evaluateMove(move);
        if (score > bestScore) {
            bestScore = score;
            bestMove = move;
        }
    }
    
    return bestMove;
}

private int evaluateMove(Move move) {
    int score = 0;
    
    // Prioriteiten:
    // 1. Stock pile moves (hoogste waarde!)
    if (move.from == MoveSource.STOCK) {
        score += 1000;
    }
    
    // 2. Skip-Bo kaarten bewaren in hand
    if (move.card.isSkipBo() && move.to == MoveDestination.DISCARD) {
        score -= 500;  // Penalty!
    }
    
    // 3. Prefer moves dat naar wint
    if (move.from == MoveSource.STOCK && stockPile.size() == 1) {
        score += 10000;  // WINNING MOVE!
    }
    
    // 4. Lege hand om 5 nieuwe kaarten te krijgen
    if (hand.size() == 1 && move.from == MoveSource.HAND) {
        score += 200;
    }
    
    return score;
}
```

### 2. **Opponent Modeling**
```java
private class OpponentModel {
    private String name;
    private int estimatedStockSize;
    private Card[] visibleDiscardTops;
    
    public int threatLevel() {
        // Hoe dichtbij is tegenstander bij winnen?
        return 100 - estimatedStockSize;
    }
}

// Defensief spelen als tegenstander bijna wint
if (opponents.stream().anyMatch(o -> o.threatLevel() > 90)) {
    // Probeer building piles vol te maken om tegenstander te blokkeren
}
```

---

## 🔍 **Prioriteit 6: Debugging & Monitoring**

### 1. **Logging Framework**
```java
private static final Logger LOGGER = Logger.getLogger(AIClient.class.getName());

// In plaats van System.out.println
LOGGER.info("[AI " + playerName + "] Connected to server");
LOGGER.warning("[AI " + playerName + "] No valid move found");
LOGGER.severe("[AI " + playerName + "] Protocol violation!");

// Met configurable verbosity
public enum LogLevel { SILENT, MINIMAL, VERBOSE, DEBUG }
private LogLevel logLevel = LogLevel.MINIMAL;
```

### 2. **Performance Metrics**
```java
private class AIMetrics {
    private long totalTurnTime;
    private int totalMoves;
    private int stockPileMoves;
    private int handMoves;
    private int discardMoves;
    private int wins;
    
    public void recordTurn(long duration, int moveCount) {
        totalTurnTime += duration;
        totalMoves += moveCount;
    }
    
    public void printStats() {
        System.out.println("=== AI Performance ===");
        System.out.println("Average turn time: " + (totalTurnTime / totalMoves) + "ms");
        System.out.println("Stock moves: " + stockPileMoves);
        System.out.println("Win rate: " + (wins * 100.0 / gamesPlayed) + "%");
    }
}
```

---

## 📋 **Checklist voor Verbetering**

### Must Have (voor 7.0+)
- [ ] AI speelt van hand kaarten
- [ ] AI speelt van discard piles
- [ ] Correcte TABLE message parsing (inclusief discard piles)
- [ ] Skip-Bo kaarten strategisch gebruiken
- [ ] Probeer meerdere kaarten per beurt te spelen
- [ ] Configureerbare timing (geen hard-coded sleeps)
- [ ] Proper exception handling
- [ ] Unit tests voor move logic
- [ ] Javadoc voor alle publieke methods

### Should Have (voor 8.0+)
- [ ] Strategy pattern implementatie
- [ ] GameState model class
- [ ] Move evaluation functie
- [ ] Geen magic numbers
- [ ] State pattern voor client states
- [ ] Logging framework
- [ ] System tests gedocumenteerd

### Nice to Have (voor 9.0+)
- [ ] Opponent modeling
- [ ] Meerdere AI difficulty levels
- [ ] Performance metrics
- [ ] Defensive play strategy
- [ ] Tournament mode met stats tracking

---

## 💡 **Specifieke Verbeter Oefeningen**

### Oefening 1: Verbeter `playTurn()` methode
**Doel:** Implementeer multi-source strategie
**Tijd:** 2-3 uur
**Deliverable:** AI die gemiddeld 5+ moves per beurt maakt

### Oefening 2: Parse TABLE berichten correct
**Doel:** Track discard pile tops
**Tijd:** 1 uur
**Deliverable:** AI kent zijn 4 discard pile tops

### Oefening 3: Skip-Bo kaart strategie
**Doel:** Bewaar Skip-Bo kaarten, gebruik ze slim
**Tijd:** 1-2 uur
**Deliverable:** Skip-Bo wordt NOOIT random weggegooid

### Oefening 4: Unit tests
**Doel:** Test alle move decision logic
**Tijd:** 2-3 uur
**Deliverable:** >= 80% code coverage voor AI logic

---

## 🎯 **Tournament Winning Tips**

1. **Snelheid = Voordeel**
   - Gebruik zo min mogelijk `Thread.sleep()`
   - Optimaliseer move calculations
   - Max 30 sec per turn, maar sneller = meer moves tegen anderen

2. **Stock Pile Prioriteit**
   - ALTIJD eerst stock pile proberen
   - Dit is je win condition!

3. **Hand Refill Strategie**
   - Als je alle 5 hand kaarten speelt, krijg je 5 nieuwe
   - Probeer dit te forceren voor meer opties

4. **Building Pile Awareness**
   - Track welke piles bijna vol zijn (10, 11, 12)
   - Speel slim naar volledig maken (geeft nieuwe pile)

5. **Defensive Play**
   - Als tegenstander bijna wint, blokkeer building piles
   - Maak piles vol als je geen stock moves hebt

---

## 📚 **Relevante Java Concepten**

Voor deze verbeteringen moet je goed begrijpen:
- **Design Patterns:** Strategy, State, Observer
- **Collections:** List, Map, Set en hun gebruik
- **Multithreading:** Thread safety, synchronized, volatile
- **Exception Handling:** Try-catch hierarchie, custom exceptions
- **Testing:** JUnit 5, Test-Driven Development
- **OOP:** Encapsulation, Inheritance, Polymorphism
- **SOLID Principles:** Vooral Single Responsibility

---

## 🚀 **Implementatie Volgorde**

**Week 1:**
1. Fix TABLE parsing voor discard piles
2. Implementeer hand card playing
3. Implementeer discard pile playing
4. Unit tests voor move validation

**Week 2:**
5. Skip-Bo strategie verbetering
6. Move evaluation functie
7. Timing optimalisatie
8. Meer unit tests

**Week 3 (optioneel):**
9. Strategy pattern refactoring
10. Opponent modeling
11. Performance metrics
12. Tournament fine-tuning

---

## ✅ **Verwachte Resultaten**

Na deze verbeteringen zou je AI moeten:
- **Minstens 5-10 kaarten** per beurt spelen (vs. huidige 1-2)
- **Skip-Bo kaarten strategisch** gebruiken
- **Sneller** zijn (< 5 seconden per beurt gemiddeld)
- **Hogere win rate** hebben tegen andere basic AIs
- **Geen protocol errors** genereren
- **Robuust** zijn tegen server disconnects

---

## 📖 **Aanvullende Resources**

### Skip-Bo Regels
- Official rules PDF (in je uploads)
- Let op: Building piles 1-12, dan clear en herstart

### Java Best Practices
- Effective Java (Joshua Bloch) - Hoofdstuk 2, 4, 8
- Clean Code (Robert Martin) - Hoofdstuk 2, 3, 6

### Testing
- JUnit 5 User Guide
- Test-Driven Development by Example (Kent Beck)

---

**Succes met de verbeteringen! Focus eerst op de Must Haves, dan Should Haves. De tournament is jouw kans om je AI te laten zien! 🏆**
