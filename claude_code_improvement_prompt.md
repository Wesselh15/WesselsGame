# Skip-Bo Code Quality Improvement Task - For Junior Developer

## Context
You are helping a junior programming student improve their Skip-Bo multiplayer game code. They have completed a working implementation that passes all functional requirements. Now they want to improve code quality by:

1. **Reducing code duplication**
2. **Using modern Java patterns** (enhanced for-loops, toArray())
3. **Extracting constants** for magic numbers

**CRITICAL:** This is junior developer code. Keep improvements:
- ✅ Simple and clear
- ✅ Maintaining existing architecture  
- ✅ Focused on readability
- ❌ NO complex refactoring
- ❌ NO design pattern overhauls
- ❌ NO architectural changes

---

## 🎯 Task Overview

The code currently has these quality issues:

### 🔴 **PRIORITY 1: Error Handling Duplication** (MUST FIX)
**Problem:** Same error handling pattern repeated 5 times in `GameManager.handleMove()`

**Current Code:**
```java
ClientHandler client = getClientByName(playerName);
if (client != null) {
    String errorMsg = new protocol.server.Error(ErrorCode.INVALID_MOVE)
                         .transformToProtocolString();
    client.sendMessage(errorMsg);
}
```

**Task:** Extract this into a helper method `sendErrorToPlayer()`

**Why:** This is genuine duplication that reduces code readability

---

### 🟡 **PRIORITY 2: Use Enhanced For-Loops** (RECOMMENDED)
**Problem:** 27 traditional for-loops with `.get(i)` pattern

**Current Pattern:**
```java
for (int i = 0; i < players.size(); i++) {
    Player player = players.get(i);
    sendHandToPlayer(player.getName());
}
```

**Task:** Replace with enhanced for-loops where index is not needed

**Why:** More readable, less error-prone, modern Java

---

### 🟢 **PRIORITY 3: Extract Magic Numbers** (NICE TO HAVE)
**Problem:** Hardcoded numbers like 4, 5, 2, 6 throughout code

**Current Pattern:**
```java
for (int i = 0; i < 4; i++)  // 4 is magic number
if (count < 2 || count > 6)  // 2 and 6 are magic numbers
```

**Task:** Create `GameConstants` class with named constants

**Why:** Self-documenting code, easier to maintain

---


## 📁 Files to Modify

### Primary Focus:
- `src/controller/GameManager.java` (573 lines)
  - Error handling duplication
  - Enhanced for-loops
  - Magic numbers
  - toArray() conversions

### Secondary:
- `src/model/Game.java` (192 lines)
  - Enhanced for-loops
  - Magic numbers

### Optional:
- Create new `src/model/GameConstants.java` (for constants)

---

## 🔍 Detailed Instructions

### **TASK 1: Fix Error Handling Duplication**

**File:** `controller/GameManager.java`

**Step 1:** Add this helper method at the end of the class (around line 500):

```java
/**
 * Sends an error message to a specific player
 * 
 * @param playerName Name of the player to send error to
 * @param errorCode The error code to send
 */
private void sendErrorToPlayer(String playerName, ErrorCode errorCode) {
    ClientHandler client = getClientByName(playerName);
    if (client != null) {
        String errorMsg = new protocol.server.Error(errorCode)
                             .transformToProtocolString();
        client.sendMessage(errorMsg);
    }
}
```

**Step 2:** Find and replace these patterns in `handleMove()` method:

**Replace Pattern 1 (line ~170):**
```java
// OLD:
ClientHandler client = getClientByName(playerName);
if (client != null) {
    String errorMsg = new protocol.server.Error(ErrorCode.COMMAND_NOT_ALLOWED)
                         .transformToProtocolString();
    client.sendMessage(errorMsg);
}

// NEW:
sendErrorToPlayer(playerName, ErrorCode.COMMAND_NOT_ALLOWED);
```

**Replace Pattern 2 (line ~214):**
```java
// OLD:
ClientHandler client = getClientByName(playerName);
if (client != null) {
    String errorMsg = new protocol.server.Error(ErrorCode.INVALID_MOVE)
                         .transformToProtocolString();
    client.sendMessage(errorMsg);
}

// NEW:
sendErrorToPlayer(playerName, ErrorCode.INVALID_MOVE);
```

**Repeat for all occurrences** in the method (should be ~5 total)

**Expected Result:**
- Helper method created
- 5 duplicated blocks replaced with single method calls
- ~20 lines of code removed
- Same functionality, cleaner code

---

### **TASK 2: Replace Traditional For-Loops**

**Guideline:** Only replace loops where:
- ✅ You iterate over ALL elements
- ✅ You don't need the index `i`
- ✅ You're not writing to arrays by index
- ❌ Keep traditional loops when you need index

**Examples to Replace:**

**Example 1 - GameManager.java ~line 319:**
```java
// OLD:
List<Player> players = game.getPlayers();
for (int i = 0; i < players.size(); i++) {
    Player player = players.get(i);
    String name = player.getName();
    ClientHandler client = getClientByName(name);
    // ... use player
}

// NEW:
List<Player> players = game.getPlayers();
for (Player player : players) {
    String name = player.getName();
    ClientHandler client = getClientByName(name);
    // ... use player
}
```

**Example 2 - GameManager.java ~line 157:**
```java
// OLD:
for (int i = 0; i < players.size(); i++) {
    sendStockTopCard(players.get(i));
}

// NEW:
for (Player player : players) {
    sendStockTopCard(player);
}
```

**Example 3 - GameManager.java ~line 531 (positionToAction method):**
```java
// OLD:
List<Card> hand = game.getHand(player);
actualCard = null;
for (int i = 0; i < hand.size(); i++) {
    if (hand.get(i).isSkipBo()) {
        actualCard = hand.get(i);
        break;
    }
}

// NEW:
List<Card> hand = game.getHand(player);
actualCard = null;
for (Card card : hand) {
    if (card.isSkipBo()) {
        actualCard = card;
        break;
    }
}
```

**DO NOT REPLACE these loops (need index):**

```java
// KEEP this - need index for array assignment:
for (int i = 0; i < playerTables.size(); i++) {
    ptArray[i] = playerTables.get(i);
}

// KEEP this - need index for building piles:
for (int i = 0; i < 4; i++) {
    BuildingPile pile = game.getBuildingPile(i);
}
```

**Files to check:**
- `GameManager.java` - Multiple loops
- `Game.java` - Check if any can be improved
- `AIClient.java` - Check handleMessage method

---

### **TASK 3: Extract Magic Numbers to Constants**

**Step 1:** Create new file `src/model/GameConstants.java`:

```java
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
```

**Step 2:** Replace magic numbers in code:

**Game.java changes:**
```java
// Line 42 - OLD:
for (int i = 0; i < 4; i++) {
// NEW:
for (int i = 0; i < GameConstants.NUM_BUILDING_PILES; i++) {

// Line 47 - OLD:
int cardsToHandout = players.size() <= 4 ? 30 : 20;
// NEW:
int cardsToHandout = players.size() <= GameConstants.MAX_PLAYERS / 2 
    ? GameConstants.STOCK_SIZE_SMALL_GAME 
    : GameConstants.STOCK_SIZE_LARGE_GAME;

// Line 56 - OLD:
for (int i = 0; i < 4; i++) {
// NEW:
for (int i = 0; i < GameConstants.NUM_DISCARD_PILES; i++) {

// Line 73 - OLD:
int cardsToDraw = 5 - hand.get(player).size();
// NEW:
int cardsToDraw = GameConstants.HAND_SIZE - hand.get(player).size();
```

**GameManager.java changes:**
```java
// Line 106 - OLD:
if (count < 2 || count > 6) {
// NEW:
if (count < GameConstants.MIN_PLAYERS || count > GameConstants.MAX_PLAYERS) {

// Line 348 - OLD:
for (int i = 0; i < 4; i++) {
// NEW:
for (int i = 0; i < GameConstants.NUM_BUILDING_PILES; i++) {

// Line 372 - OLD:
String[] discardPileValues = new String[4];
for (int j = 0; j < 4; j++) {
// NEW:
String[] discardPileValues = new String[GameConstants.NUM_DISCARD_PILES];
for (int j = 0; j < GameConstants.NUM_DISCARD_PILES; j++) {
```

**BuildingPile.java changes:**
```java
// Line 59 - OLD:
return cards.size() >= 12;
// NEW:
return cards.size() >= GameConstants.BUILDING_PILE_FULL_SIZE;
```

**Add import to all modified files:**
```java
import model.GameConstants;
```

---

### **TASK 4: Use toArray() Method**

**Replace these 3 patterns:**

**Location 1 - GameManager.java ~line 147:**
```java
// OLD:
String[] names = new String[playerNames.size()];
for (int i = 0; i < playerNames.size(); i++) {
    names[i] = playerNames.get(i);
}

// NEW:
String[] names = playerNames.toArray(new String[0]);
```

**Location 2 - GameManager.java ~line 394:**
```java
// OLD:
protocol.server.Table.PlayerTable[] ptArray = 
    new protocol.server.Table.PlayerTable[playerTables.size()];
for (int i = 0; i < playerTables.size(); i++) {
    ptArray[i] = playerTables.get(i);
}

// NEW:
protocol.server.Table.PlayerTable[] ptArray = 
    playerTables.toArray(new protocol.server.Table.PlayerTable[0]);
```

**Location 3 - GameManager.java ~line 420:**
```java
// OLD:
Winner.Score[] scores = new Winner.Score[scoreList.size()];
for (int i = 0; i < scoreList.size(); i++) {
    scores[i] = scoreList.get(i);
}

// NEW:
Winner.Score[] scores = scoreList.toArray(new Winner.Score[0]);
```

---

## ✅ Testing After Changes

After making changes, verify:

1. **Code compiles without errors**
   ```bash
   javac src/**/*.java
   ```

2. **Run Server and Client**
   - Server starts successfully
   - Client connects successfully
   - Game can be played
   - No crashes or errors

3. **Run AI Client**
   - AI connects and plays
   - No exceptions

4. **Check specific functionality:**
   - Error messages still work (test invalid moves)
   - Game still follows rules
   - Winner still announced
   - All loops still work correctly

---

## 📊 Expected Results

### Before Changes:
- Lines of code: ~1200
- Code duplication: Multiple instances
- Magic numbers: Throughout code
- Traditional loops: 27 instances

### After Changes:
- Lines of code: ~1150 (50 lines saved)
- Code duplication: Minimal
- Magic numbers: Centralized in constants
- Enhanced loops: ~15 instances improved

### Code Quality:
- Before: 7.0/10
- After: 8.0-8.5/10

---

## ⚠️ Important Reminders

**DO:**
- ✅ Test after each change
- ✅ Keep changes simple
- ✅ Maintain existing logic
- ✅ Add comments where helpful
- ✅ Follow existing code style

**DON'T:**
- ❌ Change method signatures
- ❌ Modify protocol classes
- ❌ Restructure packages
- ❌ Add complex patterns
- ❌ Change game logic
- ❌ Break existing functionality

---

## 🎯 Priorities

If time is limited, do in this order:

**1. MUST DO (15 min):**
- Extract error handling method
- Fix 5 duplications in handleMove()

**2. SHOULD DO (45 min):**
- Replace ~10 most obvious for-loops
- Add GameConstants class
- Replace obvious magic numbers

**3. NICE TO HAVE (20 min):**
- Replace all suitable for-loops
- Use toArray() in 3 places
- Add JavaDoc to new methods

---

## 💡 Hints for Claude Code

1. **Start with Task 1** - It has the biggest impact
2. **Be careful with Task 2** - Only change loops where safe
3. **Task 3 requires new file** - Make sure imports work
4. **Test incrementally** - Don't change everything at once
5. **Keep it simple** - This is junior code, maintain that level

---

## 🎓 Learning Objectives

This exercise teaches:
- **DRY principle** (Don't Repeat Yourself)
- **Code readability** (enhanced for-loops)
- **Maintainability** (constants)
- **Modern Java** (toArray, enhanced for)
- **Refactoring safely** (small steps, testing)

Good luck! 🚀
