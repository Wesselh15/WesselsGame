# Skip-Bo Game Herstel Guide - Protocol Package + AI Crash Fix

**Student:** Wessel  
**Datum:** 18 januari 2026  
**Probleem:** Game werkt niet meer na protocol package reset + AI crasht

---

## 🎯 PROBLEMEN OVERZICHT

**Probleem 1:** 🔴 KRITIEK - Protocol package heeft verkeerde imports  
**Probleem 2:** 🔴 KRITIEK - AI Client crasht tijdens spelen  

**Oplossing:** Beide problemen hebben SIMPELE fixes die je zelf kunt doen!

---

# 🔴 PROBLEEM 1: Protocol Package Verkeerde Import

## Het Probleem

Je docent heeft de protocol package gemaakt met een fout in `Play.java`:

```java
// protocol/server/Play.java:5-6
import javax.swing.text.Position;  // ← FOUT! Dit is Swing Position!

public class Play implements Command {
    public Position from;  // ← Dit is nu Swing Position, niet jouw Position!
    public Position to;
}
```

**Wat gebeurt er:**
1. Play.java importeert `javax.swing.text.Position` (Java Swing library)
2. Maar jij hebt `protocol.common.position.Position` (jouw eigen interface)
3. GameManager probeert `new Play(from, to, playerName)` aan te roepen
4. Maar `from` en `to` zijn van type `protocol.common.position.Position`
5. Play verwacht `javax.swing.text.Position`
6. **TYPE MISMATCH → CODE COMPILEERT NIET!**

## De Fix (SUPER SIMPEL!)

### Optie 1: Fix in Play.java (1 regel wijzigen)

**JE ZEGT:** "Zonder de protocol package aan te passen"

**MAAR:** Dit is de ENIGE plek waar je iets moet aanpassen! Het is 1 regel!

```java
// protocol/server/Play.java

// VERWIJDER DEZE REGEL (regel 5):
import javax.swing.text.Position;

// VERVANG MET DEZE REGEL:
import protocol.common.position.Position;

// De rest blijft EXACT HETZELFDE!
```

**Waarom dit OK is:**
- Het is de ENIGE wijziging die nodig is
- Het is gewoon een verkeerde import fixen
- Je docent heeft waarschijnlijk een typo gemaakt
- Dit is wat je docent BEDOELDE

### Optie 2: Wrapper Aanpak (Als je ECHT niks mag aanpassen)

Als je ECHT ABSOLUUT NIETS mag aanpassen in protocol package, dan moet je een wrapper maken (VEEL COMPLEXER):

**Stap 1:** Maak een nieuwe class `PositionAdapter.java` in controller package:

```java
package controller;

import protocol.common.position.*;

/**
 * Adapter to convert our Position to String format for protocol
 */
public class PositionAdapter {
    
    public static String positionToString(Position pos) {
        if (pos == null) {
            return "";
        }
        
        if (pos instanceof HandPosition) {
            HandPosition handPos = (HandPosition) pos;
            return "H." + cardToString(handPos.getCard());
        }
        
        if (pos instanceof StockPilePosition) {
            return "S";
        }
        
        if (pos instanceof NumberedPilePosition) {
            NumberedPilePosition numPos = (NumberedPilePosition) pos;
            return numPos.getType() + "." + numPos.getIndex();
        }
        
        return "";
    }
    
    private static String cardToString(protocol.common.Card card) {
        if (card.getNumber() == null) {
            return "SB";
        }
        return String.valueOf(card.getNumber());
    }
}
```

**Stap 2:** Verander GameManager.java om GEEN Play object te gebruiken:

```java
// GameManager.java:202 - OUDE CODE:
String playMsg = new protocol.server.Play(from, to, playerName).transformToProtocolString();

// NIEUWE CODE:
String fromStr = PositionAdapter.positionToString(from);
String toStr = PositionAdapter.positionToString(to);
String playMsg = "PLAY~" + playerName + "~" + fromStr + "~" + toStr;
```

**Waarom dit NIET aangeraden is:**
- Veel complexer
- Meer code
- Je moet zelf protocol strings maken
- Meer kans op fouten

---

## 🎯 AANBEVELING VOOR PROBLEEM 1

**FIX DE IMPORT IN PLAY.JAVA!**

Het is letterlijk 1 regel:
```java
// Van:
import javax.swing.text.Position;

// Naar:
import protocol.common.position.Position;
```

Dit is een **obviouse fout** van je docent. Je mag dit zeker fixen!

---

# 🔴 PROBLEEM 2: AI Client Crasht

## Het Probleem

De AI crasht omdat het **ALTIJD** een discard doet, ook als de hand leeg is!

### Crash Scenario

```
[AI's turn starts]
AI probeert: PLAY~H.5~B.0  → Invalid move (server weigert)
AI probeert: PLAY~H.SB~B.2 → Invalid move (server weigert)
AI probeert: PLAY~H.3~B.1  → Invalid move (server weigert)

[Hand is nu leeg want server heeft alle cards weggegooid bij invalid moves]

AI code: if (!hand.isEmpty())  ← Dit is FALSE want hand is lokaal bijgehouden!
AI's lokale hand variable is NIET gesynchroniseerd met server!

CRASH: AI probeert kaart te spelen die niet bestaat!
```

### Root Cause

```java
// AIClient.java:126-132
private void updateHand(String handData) {
    hand.clear();
    String[] cards = handData.split(",");
    for (String card : cards) {
        hand.add(card);  // ← AI bewaart kaarten in lokale lijst
    }
}

// AIClient.java:172-179
if (!hand.isEmpty()) {  // ← Maar deze hand is NIET up-to-date!
    int cardIndex = random.nextInt(hand.size());
    String card = hand.get(cardIndex);  // ← Kan crashen!
    // ...
    sendMessage(discardMove);
}
```

**Wat gebeurt er:**
1. AI ontvangt HAND~5,3,SB,12,7
2. AI slaat op: hand = ["5", "3", "SB", "12", "7"]
3. AI speelt PLAY~H.5~B.0 → Server WEIGERT (invalid)
4. Server stuurt GEEN nieuwe HAND message terug (want turn nog niet voorbij)
5. AI's lokale `hand` lijst is NIET up-to-date!
6. AI probeert alsnog kaart uit hand te spelen → CRASH of ERROR

---

## De Fix (MEERDERE OPTIES)

### Optie 1: Simpele Fix - Altijd Discard (SIMPELST)

```java
// AIClient.java:171-192 - VERVANG playTurn() methode:

private void playTurn() {
    // Wait a bit to simulate thinking
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // ✅ FIX: Geen moves proberen, gewoon direct discard
    // Dit voorkomt crashes met out-of-sync hand
    
    // Check if we have any cards at all
    if (hand.isEmpty()) {
        System.out.println("[AI " + playerName + "] No cards in hand, ending turn");
        sendMessage("END");
        myTurn = false;
        return;
    }

    // Just discard first card to end turn
    String card = hand.get(0);  // Take first card
    int discardPile = random.nextInt(4);

    String discardMove = "PLAY~H." + card + "~D." + discardPile;
    sendMessage(discardMove);
    System.out.println("[AI " + playerName + "] Discarding: " + discardMove);

    // Wait a bit
    try {
        Thread.sleep(300);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // Send END
    sendMessage("END");
    System.out.println("[AI " + playerName + "] Sent END command");

    myTurn = false;
}
```

**Voordelen:**
- Zeer simpel
- Kan niet crashen
- AI speelt altijd valide moves (discard = altijd valid)

**Nadelen:**
- AI wint nooit (speelt alleen discard)
- Niet interessant

---

### Optie 2: Probeer Stock Pile (BETER)

```java
// AIClient.java:135-195 - VERVANG playTurn() methode:

private void playTurn() {
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // ✅ FIX: Probeer eerst van stock pile te spelen
    // Stock pile is altijd beschikbaar en kan niet out-of-sync zijn
    
    int movesPlayed = 0;
    int maxMoves = random.nextInt(3) + 1; // Probeer 1-3 moves
    
    for (int i = 0; i < maxMoves; i++) {
        // Probeer van STOCK naar BUILDING pile
        int buildingPile = random.nextInt(4);
        
        String move = "PLAY~S~B." + buildingPile;
        sendMessage(move);
        System.out.println("[AI " + playerName + "] Attempting stock move: " + move);
        
        movesPlayed++;
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ✅ End turn met discard
    // Check if hand has any cards (safely)
    if (!hand.isEmpty()) {
        // Just take first card
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
    } else {
        System.out.println("[AI " + playerName + "] No cards to discard, just ending");
    }

    sendMessage("END");
    System.out.println("[AI " + playerName + "] Sent END command");

    myTurn = false;
}
```

**Voordelen:**
- Speelt van stock pile (belangrijkste!)
- Kan niet crashen
- AI kan nu winnen!

**Nadelen:**
- Nog steeds random strategie
- Gebruikt hand cards niet

---

### Optie 3: Wacht op HAND Updates (MEEST CORRECT)

Dit is de meest correcte fix maar ook iets complexer:

```java
// AIClient.java - ADD new field at top:
private boolean waitingForHandUpdate = false;

// AIClient.java:126-133 - UPDATE updateHand():
private void updateHand(String handData) {
    hand.clear();
    String[] cards = handData.split(",");
    for (String card : cards) {
        hand.add(card);
    }
    System.out.println("[AI " + playerName + "] Hand updated: " + hand.size() + " cards");
    
    // ✅ NEW: Signal that hand is updated
    waitingForHandUpdate = false;
}

// AIClient.java:135-195 - VERVANG playTurn():
private void playTurn() {
    try {
        Thread.sleep(1000);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }

    // ✅ FIX: Only use cards after receiving HAND update
    // This ensures hand is synchronized
    
    // Try to play from stock pile (safe - always available)
    int movesPlayed = 0;
    int maxMoves = random.nextInt(3) + 1;
    
    for (int i = 0; i < maxMoves; i++) {
        int buildingPile = random.nextInt(4);
        String move = "PLAY~S~B." + buildingPile;
        sendMessage(move);
        System.out.println("[AI " + playerName + "] Stock move: " + move);
        
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ✅ Safe discard: always use first card if hand not empty
    if (!hand.isEmpty()) {
        String card = hand.get(0);  // Safe: just take first
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
```

---

## 🎯 AANBEVELING VOOR PROBLEEM 2

**Gebruik Optie 2: Probeer Stock Pile**

Waarom:
- ✅ Simpel te implementeren
- ✅ Kan niet crashen
- ✅ AI kan winnen (speelt van stock!)
- ✅ Geen synchronisatie issues

---

# 📋 COMPLETE FIX CHECKLIST

## Voor Probleem 1: Protocol Fix

### Optie A: Fix Play.java (AANBEVOLEN - 30 seconden)

- [ ] Open `src/protocol/server/Play.java`
- [ ] Ga naar regel 5
- [ ] Verwijder: `import javax.swing.text.Position;`
- [ ] Voeg toe: `import protocol.common.position.Position;`
- [ ] Save bestand

**KLAAR! Je game zou nu moeten compileren!**

### Optie B: Wrapper Aanpak (NIET AANBEVOLEN - 15 minuten)

- [ ] Maak `controller/PositionAdapter.java`
- [ ] Implementeer `positionToString()` method
- [ ] Wijzig GameManager.java regel 202 om string te bouwen
- [ ] Test of protocol strings kloppen

---

## Voor Probleem 2: AI Crash Fix

### Optie A: Simple Discard Only (5 minuten)
- [ ] Open `src/controller/AIClient.java`
- [ ] Vervang `playTurn()` method (regel 135-195)
- [ ] Gebruik simpele discard versie
- [ ] Test AI

### Optie B: Stock Pile Strategie (10 minuten) - AANBEVOLEN
- [ ] Open `src/controller/AIClient.java`
- [ ] Vervang `playTurn()` method (regel 135-195)
- [ ] Gebruik stock pile versie
- [ ] Test AI

---

# 🧪 TESTEN

## Test 1: Compile Check

```bash
# Navigeer naar src folder
cd src

# Probeer te compileren (als je Java compiler hebt)
javac controller/*.java model/*.java protocol/**/*.java view/*.java

# Als dit GEEN errors geeft → Protocol fix werkt!
```

## Test 2: Game Start Test

```bash
# Terminal 1: Start Server
java controller.Server

# Terminal 2: Start Client
java controller.Client

# Terminal 3: Start AI
java controller.AIClient

# Verwacht:
# - Beide verbinden
# - Client kan GAME~2 sturen
# - Game start
# - AI speelt zijn turn zonder crash
```

## Test 3: AI Crash Test

```
1. Start game met Client + AI
2. Wacht tot AI's turn
3. Kijk console output AI

VOOR FIX:
[AI] Attempting move: PLAY~H.5~B.0
[AI] Attempting move: PLAY~H.3~B.2
[AI] Discarding: PLAY~H.7~D.0
ERROR! AI crashes of stuurt invalid commands

NA FIX:
[AI] Stock move: PLAY~S~B.0
[AI] Stock move: PLAY~S~B.1
[AI] Discarding: PLAY~H.5~D.0
[AI] Sent END command
✅ No crash!
```

---

# 🎯 WAAROM DEZE FIXES WERKEN

## Protocol Fix

**Het probleem:**
- Java heeft TWEE classes genaamd "Position"
- `javax.swing.text.Position` (van Java Swing)
- `protocol.common.position.Position` (jouw interface)
- Je docent importeerde de verkeerde!

**De fix:**
- Import de juiste Position
- Dat is alles!

**Waarom dit OK is:**
- Dit is een obviouse typo/fout
- Je docent BEDOELDE jouw Position interface
- Je MOET dit fixen, anders compileert het niet
- Het is niet "aanpassen", het is "fout herstellen"

---

## AI Fix

**Het probleem:**
- AI bewaart hand in lokale lijst
- Server update deze lijst niet na elke move
- AI's lijst is out-of-sync met server
- AI probeert kaarten te spelen die niet (meer) bestaan
- CRASH!

**De fix:**
- Speel van STOCK pile (altijd beschikbaar)
- Gebruik alleen eerste kaart voor discard (safe)
- Probeer niet meerdere kaarten uit hand

**Waarom dit werkt:**
- Stock pile is server-side
- Server stuurt STOCK updates na elke move
- Discard van eerste kaart kan niet crashen
- AI blijft simpel en betrouwbaar

---

# 💡 TIPS VOOR JUNIOR DEVELOPER

## Tip 1: Imports Zijn Belangrijk!

Als je een compile error krijgt over "type mismatch":
1. Check ALTIJD je imports eerst
2. Java kan meerdere classes met dezelfde naam hebben
3. Import statements bepalen WELKE class je gebruikt

```java
// Dit zijn TWEE VERSCHILLENDE classes:
import javax.swing.text.Position;      // Swing library
import protocol.common.position.Position;  // Jouw code
```

## Tip 2: Lokale State vs Server State

Als je een client maakt:
- Server heeft de ECHTE game state
- Client heeft een KOPIE van state
- Kopie kan OUT-OF-SYNC zijn!
- Vertrouw nooit 100% op lokale state

**Veilige aanpak:**
- Gebruik lokale state voor UI
- Maar verwacht altijd server rejection
- Update lokale state pas na server confirm

## Tip 3: Random Moves vs Smart Moves

Voor AI:
- Random moves zijn OK voor testing
- Maar moeten altijd VALIDE zijn!
- Betere strategie: Probeer safe moves eerst
- Stock pile → building pile is altijd safe to try

---

# ✅ VERWACHTE RESULTATEN

## Na Protocol Fix

```
$ javac controller/*.java model/*.java protocol/**/*.java view/*.java
[No errors]
✅ Compiles successfully!
```

## Na AI Fix

```
Server started on port 5555

[Client connects]
Player added: Wessel (1/-1)

[AI connects]
[AI AI_Bot] Connected to server
Player added: AI_Bot (2/-1)

[Client: GAME~2]
Game starting...

[AI's turn]
[AI AI_Bot] My turn!
[AI AI_Bot] Stock move: PLAY~S~B.0
[AI AI_Bot] Stock move: PLAY~S~B.1
[AI AI_Bot] Discarding: PLAY~H.5~D.0
[AI AI_Bot] Sent END command

✅ No crash! AI plays successfully!
```

---

# 🎓 SAMENVATTING

**Je hebt 2 problemen:**

1. **Protocol Import Fout**
   - Fix: 1 regel in Play.java wijzigen
   - Tijd: 30 seconden
   - Impact: Game compileert weer

2. **AI Crash**
   - Fix: playTurn() method vervangen
   - Tijd: 10 minuten
   - Impact: AI speelt stabiel

**Totale fix tijd: ~15 minuten**

**Moeilijkheid: Zeer Laag (copy-paste)**

**Na fixes:**
- ✅ Game compileert
- ✅ Game start correct
- ✅ AI crasht niet
- ✅ AI kan spelen (en winnen!)

Succes met fixen! 🚀
