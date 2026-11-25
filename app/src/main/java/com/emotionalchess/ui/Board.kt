package com.emotionalchess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Kind
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Piece
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side

// Solid glyphs for both colours (tinted), with a text-presentation selector so the pawn
// never renders as an emoji.
private fun glyph(kind: Kind): String = when (kind) {
    Kind.KING -> "♚"
    Kind.QUEEN -> "♛"
    Kind.ROOK -> "♜"
    Kind.BISHOP -> "♝"
    Kind.KNIGHT -> "♞"
    Kind.PAWN -> "♟"
} + "︎"

@Composable
fun ChessBoard(
    game: GameState,
    selected: Int?,
    targets: List<Move>,
    flipped: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetSquares = targets.map { it.to }.toSet()
    val checkSquare = if (Rules.inCheck(game)) Rules.kingSquare(game.board, game.toMove) else null
    val absolvable = Rules.legalAbsolutions(game).toSet()
    val last = game.lastMove

    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
    ) {
        val cell = maxWidth / 8
        Column(Modifier.fillMaxSize()) {
            for (row in 0..7) {
                Row(Modifier.weight(1f)) {
                    for (col in 0..7) {
                        val rank = if (flipped) row else 7 - row
                        val file = if (flipped) 7 - col else col
                        val sq = Rules.sq(file, rank)
                        Square(
                            sq = sq,
                            piece = game.board[sq],
                            cell = cell,
                            isLight = (file + rank) % 2 == 1,
                            selected = sq == selected,
                            target = sq in targetSquares,
                            lastMove = last != null && (sq == last.from || sq == last.to),
                            inCheck = sq == checkSquare,
                            absolvable = sq in absolvable,
                            bonus = sq == game.bonusSquare,
                            fileLabel = if (row == 7) ('a' + file).toString() else null,
                            rankLabel = if (col == 0) (rank + 1).toString() else null,
                            onTap = onTap,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Square(
    sq: Int,
    piece: Piece?,
    cell: Dp,
    isLight: Boolean,
    selected: Boolean,
    target: Boolean,
    lastMove: Boolean,
    inCheck: Boolean,
    absolvable: Boolean,
    bonus: Boolean,
    fileLabel: String?,
    rankLabel: String?,
    onTap: (Int) -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val base = if (isLight) LightSquare else DarkSquare
    val labelColor = if (isLight) DarkSquare else LightSquare
    val description = buildString {
        append(Rules.squareName(sq))
        if (piece != null) append(", ${piece.name}, ${piece.mood.name.lowercase()}")
    }
    Box(
        modifier
            .fillMaxSize()
            .background(base)
            .then(if (lastMove) Modifier.background(Color(0x66F6F669)) else Modifier)
            .then(if (inCheck) Modifier.background(scheme.error.copy(alpha = 0.55f)) else Modifier)
            .then(if (selected) Modifier.background(scheme.primary.copy(alpha = 0.45f)) else Modifier)
            .clickable { onTap(sq) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val fontSize = with(LocalDensity.current) { (cell * 0.72f).toSp() }
        val small = with(LocalDensity.current) { (cell * 0.2f).toSp() }

        if (rankLabel != null) Text(
            rankLabel, color = labelColor, fontSize = small, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp),
        )
        if (fileLabel != null) Text(
            fileLabel, color = labelColor, fontSize = small, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp),
        )

        if (piece != null) {
            val ring = moodColor(piece.mood)
            if (ring != null && piece.mood != Mood.UNAFFECTED) {
                Box(
                    Modifier
                        .size(cell * 0.9f)
                        .border(cell * 0.07f, ring, CircleShape)
                )
            }
            if (absolvable || bonus) {
                Box(
                    Modifier
                        .size(cell * 0.98f)
                        .border(2.dp, if (bonus) moodColor(Mood.PSYCHOPATH)!! else scheme.tertiary, RoundedCornerShape(4.dp))
                )
            }
            val fill = if (piece.side == Side.WHITE) Color.White else Color(0xFF1B1B1B)
            val outline = if (piece.side == Side.WHITE) Color(0xFF1B1B1B) else Color(0xFFEEEEEE)
            Box(contentAlignment = Alignment.Center) {
                Text(glyph(piece.kind), color = fill, fontSize = fontSize)
                Text(
                    glyph(piece.kind),
                    color = outline,
                    fontSize = fontSize,
                    style = TextStyle(drawStyle = Stroke(width = with(LocalDensity.current) { (cell * 0.025f).toPx() })),
                )
            }
            if (piece.mood == Mood.UNAFFECTED) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-3).dp, y = 3.dp)
                        .size(cell * 0.14f)
                        .background(ring!!, CircleShape)
                )
            }
            if (piece.mood == Mood.DEVASTATED) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(cell * 0.3f)
                        .background(ring!!, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(piece.countdown.toString(), color = Color.White, fontSize = small, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (target) {
            if (piece == null) {
                Box(Modifier.size(cell * 0.28f).background(scheme.primary.copy(alpha = 0.6f), CircleShape))
            } else {
                Box(Modifier.fillMaxSize().border(cell * 0.07f, scheme.primary.copy(alpha = 0.7f), CircleShape))
            }
        }
    }
}

@Composable
fun PieceGlyph(kind: Kind, side: Side, size: Dp) {
    val fs = with(LocalDensity.current) { (size * 0.8f).toSp() }
    Box(contentAlignment = Alignment.Center) {
        Text(glyph(kind), color = if (side == Side.WHITE) Color.White else Color(0xFF1B1B1B), fontSize = fs)
        Text(
            glyph(kind),
            color = if (side == Side.WHITE) Color(0xFF1B1B1B) else Color(0xFFEEEEEE),
            fontSize = fs,
            style = TextStyle(drawStyle = Stroke(width = with(LocalDensity.current) { 1.dp.toPx() })),
        )
    }
}
