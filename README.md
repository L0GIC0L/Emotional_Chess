# Emotional Chess

An Android companion app for playing **Remorse Chess** (a.k.a. emotional chess) on a real board with a friend.
Mirror each move on the phone; the app tracks every piece's feelings, die rolls, countdowns and absolutions,
and enforces what each piece may still do.

Built with Jetpack Compose and Material 3 (dynamic colour). The layout adapts to the Pixel Fold: board and
panel stacked on the outer screen, side by side on the unfolded inner screen.

## Variants

**Classic Remorse** – a piece that captures becomes remorseful: it can move and block but cannot capture
(and so gives no check) until a friendly bishop moves onto one of the eight squares around it.

**Emotional (dice)** – a piece's first capture rolls one die:

| Roll | Mood | Effect |
|---|---|---|
| 1 | Devastated | Frozen, gives no check, leaves the board after its owner's 3rd following turn unless absolved. A departing king loses. |
| 2–3 | Guilt-ridden | Moves normally, cannot capture or give check until absolved. |
| 4–5 | Unaffected | Nothing happens. |
| 6 | Psychopath | Every later capture earns one mandatory extra move; that extra move may capture the king. |

Absolution takes a whole turn: a friendly, non-devastated bishop next to the afflicted piece absolves it
(never itself, never while in check). You can enter a physical die roll or let the app roll.

Rules follow the published descriptions at nabous.dev (classic) and remorsechess.com (dice).

## Features

- Legal-move highlighting, check/checkmate/stalemate, castling, en passant, promotion
- Mood rings, devastation countdown badges, bonus-move and absolution prompts
- Move log, undo, board flip, resign/draw, game saved automatically
- Screen stays on during a game

## Building

GitHub Actions (`.github/workflows/android.yml`) runs the unit tests and builds a debug APK on every push.
Download it from the run's **Artifacts** section (`emotional-chess-debug-apk`) and install it on the phone.

Locally: `./gradlew testDebugUnitTest assembleDebug` (JDK 17+, Android SDK 35).
