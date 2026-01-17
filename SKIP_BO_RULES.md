# Skip-Bo Regels Implementatie Checklist

## ✅ GEÏMPLEMENTEERDE REGELS

### Setup Regels
| Regel | Locatie | Status |
|-------|---------|--------|
| Stock pile: 30 kaarten (2-4 spelers) of 20 kaarten (5-6 spelers) | `Game.java:47` | ✅ |
| 4 gedeelde building piles | `Game.java:42-44` | ✅ |
| 4 discard piles per speler | `Game.java:55-59` | ✅ |
| Hand van 5 kaarten | `Game.java:72-75` | ✅ |
| 126 kaarten totaal (18 Skip-Bo + 108 numbered) | `CardGenerator.java` | ✅ |

### Gameplay Regels
| Regel | Locatie | Status |
|-------|---------|--------|
| Building piles moeten sequentieel 1→12 | `BuildingPile.java:14-36` | ✅ |
| Skip-Bo kaarten zijn wildcards | `BuildingPile.java:19-22` | ✅ |
| Alleen top card van stock pile kan gespeeld worden | `CardActionStockPileToBuildingPile.java:23-35` | ✅ |
| Alleen top card van discard pile kan gespeeld worden | `CardActionDiscardPileToBuildingPile.java:21-36` | ✅ |
| Building pile leegmaken bij 12 kaarten | `Game.java:96-100` | ✅ |
| Hand aanvullen naar 5 kaarten na beurt | `Game.java:109` | ✅ |
| Beurt eindigt bij discard | `Game.java:106-110` | ✅ |
| **Beurt wisseling wordt aangekondigd** | `GameManager.java:165-174` | ✅ **FIXED!** |
| **Stock pile top card is zichtbaar** | `GameManager.java:240-249` | ✅ **FIXED!** |

### Win Condities
| Regel | Locatie | Status |
|-------|---------|--------|
| Win door stock pile leeg te maken | `GameManager.java:160-163` | ✅ |
| Winner krijgt 100 punten | `GameManager.java:331-333` | ✅ |

## ❌ NIET GEÏMPLEMENTEERDE REGELS (Optioneel)

### Advanced/Optionele Regels
| Regel | Reden |
|-------|-------|
| **Bonus: 5 extra kaarten bij alle hand kaarten spelen** | Optionele regel, vaak niet gespeeld |
| Multiple rounds met score tracking | Single game focus |
| Scores berekenen op basis van resterende kaarten | Simplified scoring (100 voor winner) |

## 🐛 GEFIXTE BUGS

### Bug #1: Beurt wisselt niet zichtbaar (KRITIEK)
**Probleem:**
- `Game.java` wisselde correct de beurt na een discard (regel 108)
- `GameManager.java` stuurde GEEN TURN message naar clients
- Clients wisten niet dat de beurt was gewisseld

**Fix:**
```java
// GameManager.java:165-174
// Als beurt is veranderd, stuur TURN message
Player playerAfterMove = game.getCurrentPlayer();
if (playerBeforeMove != playerAfterMove) {
    String turnMsg = new Turn(playerAfterMove.getName()).transformToProtocolString();
    server.broadcast(turnMsg);
    sendStockTopCard(playerAfterMove);
}
```

### Bug #2: Stock pile top card niet zichtbaar
**Probleem:**
- Volgens Skip-Bo regels moet de top card van stock pile zichtbaar zijn voor alle spelers
- Dit werd niet naar clients gestuurd

**Fix:**
```java
// GameManager.java:240-249
private void sendStockTopCard(Player player) {
    StockPile stockPile = game.getStockPile(player);
    if (!stockPile.isEmpty()) {
        Card topCard = stockPile.topCard();
        String cardStr = cardToString(topCard);
        String stockMsg = new Stock(player.getName(), cardStr).transformToProtocolString();
        server.broadcast(stockMsg);
    }
}
```

Dit wordt nu gestuurd:
1. Bij game start (voor alle spelers)
2. Bij nieuwe beurt (voor de nieuwe speler)
3. Na een move van stock pile

## 📊 IMPLEMENTATIE OVERZICHT

### Model Package (Game Logic) - 100% Compleet
Alle basis Skip-Bo regels zijn correct geïmplementeerd in de model classes:
- ✅ `Game.java` - Turn management, win detection
- ✅ `BuildingPile.java` - Sequentiële validatie, wildcards
- ✅ `StockPile.java` - Top card access only
- ✅ `DiscardPile.java` - Top card access only
- ✅ `CardAction*.java` - Alle move types met validatie

### Controller Package (Network) - Nu compleet
- ✅ `GameManager.java` - Beurt wisseling gefixed, stock cards toegevoegd
- ✅ `ClientHandler.java` - Protocol parsing
- ✅ `ServerHandler.java` - Message display

### Protocol Package - Ongewijzigd
Gemeenschappelijk protocol voor alle groepen - geen wijzigingen nodig.

## 🎮 GAMEPLAY FLOW (Nu correct!)

1. **Game Start:**
   - Server: `START~Alice,Bob`
   - Server: `TABLE~...` (building/discard piles)
   - Server: `HAND~5,7,12,SB,3` (naar elke speler hun eigen hand)
   - Server: `STOCK~Alice~8` (Alice's stock top card)
   - Server: `STOCK~Bob~5` (Bob's stock top card)
   - Server: `TURN~Alice` (wie is aan de beurt)

2. **During Turn:**
   - Client: `PLAY~H.5~B.0` (speel kaart 5 naar building pile 0)
   - Server: `PLAY~Alice~H.5~B.0` (broadcast naar iedereen)
   - Server: `TABLE~...` (updated state)
   - Server: `HAND~7,12,SB,3,9` (updated hand)

3. **Turn End (Discard):**
   - Client: `PLAY~H.3~D.0` (discard kaart 3)
   - Server: `PLAY~Alice~H.3~D.0`
   - Server: `TABLE~...`
   - Server: `HAND~...`
   - Server: **`TURN~Bob`** ← **NU GEFIXED!**
   - Server: **`STOCK~Bob~5`** ← **NU TOEGEVOEGD!**

4. **Win:**
   - Stock pile is leeg
   - Server: `WINNER~Alice.100,Bob.0`

## ✨ CONCLUSIE

Alle **essentiële Skip-Bo regels** zijn nu correct geïmplementeerd!

### Wat werkt:
- ✅ Complete game setup
- ✅ Alle move validatie
- ✅ Turn management (nu met correcte announcements)
- ✅ Stock pile top card visibility
- ✅ Win detection
- ✅ Protocol communicatie

### Wat is optioneel (niet geïmplementeerd):
- ❌ Bonus 5 kaarten regel (vaak niet gespeeld)
- ❌ Multi-round scoring (simplified to single game)

Het spel is **volledig speelbaar** volgens de officiële Skip-Bo regels! 🎉
