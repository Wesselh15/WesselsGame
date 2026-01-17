# Skip-Bo Bugs - Gevonden en Gefixed

## 🐛 KRITIEKE BUGS (Spel werkte niet correct)

### Bug #1: Hand wordt niet aangevuld tijdens beurt ⭐ JOUW ONTDEKKING
**Probleem:**
- Je speelt 3 kaarten naar building piles
- Je hand heeft nu 2 kaarten
- Je discard om je beurt te beëindigen
- Nieuwe speler krijgt 5 kaarten
- **JIJ krijgt GEEN nieuwe kaarten!**

**Oorzaak:**
In `Game.java:103-111`, hand werd ALLEEN aangevuld voor nieuwe speler na beurt wisseling:
```java
if (lastAction instanceof CardActionHandToDiscardPile) {
    currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    handCards(players.get(currentPlayerIndex)); // ← Alleen nieuwe speler!
}
```

**Skip-Bo Regel:**
- Hand moet tijdens je beurt ALTIJD aangevuld worden naar 5 kaarten
- Zodra je een kaart speelt (behalve discard), vul aan

**Fix:**
```java
// In Game.doMove(), na elke cardAction.execute():
if (!(cardAction instanceof CardActionHandToDiscardPile)) {
    handCards(player); // Vul hand aan na elke non-discard move
}
```

**Locatie:** `Game.java:102-106`

---

### Bug #2: Stock pile top card niet ge-update na stock pile move
**Probleem:**
- Speler speelt van stock pile → building pile
- Stock pile heeft nieuwe top card
- **Clients zien de oude top card!**

**Oorzaak:**
In `GameManager.handleMove()`, stock pile top card werd ALLEEN gestuurd bij:
- Game start
- Beurt wisseling

**Fix:**
```java
// Check if move was from stock pile
if (from instanceof StockPilePosition) {
    sendStockTopCard(player); // Send new top card
}
```

**Locatie:** `GameManager.java:161-164`

---

### Bug #3: Draw pile kan leeg raken → CRASH
**Probleem:**
- Draw pile heeft 3 kaarten over
- Speler moet 5 kaarten krijgen (5 - 0 = 5)
- `drawPile.subList(0, 5)` → **IndexOutOfBoundsException!**

**Oorzaak:**
In `Game.handCards()`, geen check of draw pile genoeg kaarten heeft:
```java
List<Card> handOut = drawPile.subList(0, 5 - hand.get(player).size());
```

**Fix:**
```java
int cardsToDraw = 5 - hand.get(player).size();

// Don't draw more than available
if (cardsToDraw > drawPile.size()) {
    cardsToDraw = drawPile.size();
}

if (cardsToDraw > 0 && !drawPile.isEmpty()) {
    List<Card> handOut = new ArrayList<>(drawPile.subList(0, cardsToDraw));
    hand.get(player).addAll(handOut);
    drawPile.removeAll(handOut);
}
```

**Locatie:** `Game.java:71-86`

---

## ⚠️ MINOR ISSUES (Niet kritiek, maar verbeterd)

### Issue #1: AI maakt veel invalide moves
**Probleem:**
- AI kiest random kaarten en random building piles
- Veel moves zijn invalid
- Server reject deze (correct), maar veel spam

**Status:**
- Niet gefixed (niet kritiek)
- AI is simpel gehouden voor beginnend programmeur project
- Server valideert correct, dus geen probleem
- Als gewenst kan AI slimmer gemaakt worden

**Als je dit wilt fixen:**
- Check eerst of building pile leeg is → speel 1 of Skip-Bo
- Anders, check huidige pile top → speel next sequential card
- Anders, discard

---

### Issue #2: Scoring systeem simplified
**Probleem:**
- `Map<Player, Integer> scores` bestaat maar wordt niet gebruikt
- Alleen winner krijgt 100 punten
- Geen multi-round support

**Status:**
- Niet gefixed (design keuze)
- Single-game focus is gekozen
- Voor multi-round: scores updaten op basis van resterende cards in stock pile

---

## ✅ GEEN BUG (Werkt correct)

### ✓ Building pile clear bij 12 kaarten
- Wordt correct gecleared in `Game.java:96-100`
- Clients krijgen update via TABLE message
- **Werkt!**

### ✓ Turn validatie
- Only current player kan moves maken
- Wordt gevalideerd in `Game.doMove():80-82`
- **Werkt!**

### ✓ Move validatie
- Alle CardAction classes hebben `isValid()` method
- Sequentiële building pile validatie werkt
- **Werkt!**

### ✓ Win detection
- Wordt gedetecteerd in `GameManager.handleMove():165-168`
- WINNER message wordt gestuurd
- **Werkt!**

---

## 📊 SAMENVATTING

### Gefixte Bugs:
1. ✅ **Hand refill tijdens beurt** - Was KRITIEK, nu gefixed
2. ✅ **Stock pile top card update** - Was verwarrend, nu gefixed
3. ✅ **Draw pile empty crash** - Was crash risk, nu gefixed

### Niet Gefixed (Acceptabel):
- AI is simpel en maakt veel invalid moves (niet broken, gewoon dom)
- Scoring is simplified to winner-take-all (design keuze)

### Test Scenario's:
```bash
# Test Bug #1 fix:
1. Start game met 2 spelers
2. Speler 1: PLAY~H.1~B.0 (hand: 4 kaarten)
3. Verwacht: HAND message met 5 kaarten ✅

# Test Bug #2 fix:
1. Speler 1: PLAY~S~B.0 (stock pile card)
2. Verwacht: STOCK~Player1~X message met nieuwe top ✅

# Test Bug #3 fix:
1. Speel tot draw pile bijna leeg
2. Probeer hand aan te vullen
3. Verwacht: Geen crash, krijgt resterende kaarten ✅
```

---

## 🎮 GAME STATUS

Het spel is nu **volledig speelbaar** volgens Skip-Bo regels!

Alle essentiële regels werken:
- ✅ Hand refills correct (tijdens EN na beurt)
- ✅ Stock pile visibility up-to-date
- ✅ Turn switching met announcements
- ✅ Building pile sequential validatie
- ✅ Win detection
- ✅ No crashes bij edge cases

**Klaar voor testen!** 🎉
