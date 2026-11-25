package com.emotionalchess.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.emotionalchess.R
import com.emotionalchess.engine.Action
import com.emotionalchess.engine.GameState
import com.emotionalchess.engine.Hint
import com.emotionalchess.engine.Kind
import com.emotionalchess.engine.Mode
import com.emotionalchess.engine.Mood
import com.emotionalchess.engine.Move
import com.emotionalchess.engine.Piece
import com.emotionalchess.engine.Rules
import com.emotionalchess.engine.Side

@DrawableRes
fun pieceRes(kind: Kind, side: Side): Int = if (side == Side.WHITE) {
    when (kind) {
        Kind.KING -> R.drawable.piece_wk
        Kind.QUEEN -> R.drawable.piece_wq
        Kind.ROOK -> R.drawable.piece_wr
        Kind.BISHOP -> R.drawable.piece_wb
        Kind.KNIGHT -> R.drawable.piece_wn
        Kind.PAWN -> R.drawable.piece_wp
    }
} else {
    when (kind) {
        Kind.KING -> R.drawable.piece_bk
        Kind.QUEEN -> R.drawable.piece_bq
        Kind.ROOK -> R.drawable.piece_br
        Kind.BISHOP -> R.drawable.piece_bb
        Kind.KNIGHT -> R.drawable.piece_bn
        Kind.PAWN -> R.drawable.piece_bp
    }
}

@Composable
fun PieceImage(kind: Kind, side: Side, modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painterResource(pieceRes(kind, side)),
        contentDescription = null,
        modifier = modifier,
        colorFilter = tint?.let { ColorFilter.tint(it, androidx.compose.ui.graphics.BlendMode.Modulate) },
    )
}

private const val SLIDE_MS = 260

/**
 * Three layers: a canvas for squares and highlights, the pieces (each one slides when its square
 * changes, and fades out when it leaves the board), and an invisible grid that takes the taps.
 */
@Composable
fun ChessBoard(
    game: GameState,
    selected: Int?,
    targets: List<Move>,
    flipped: Boolean,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: Hint? = null,
) {
    val scheme = MaterialTheme.colorScheme
    // A castling move can be entered on the king's destination or on the rook.
    val targetSquares = targets.flatMap { listOfNotNull(it.to, if (it.castle) it.rookFrom else null) }.toSet()
    val hintMove = (hint?.action as? Action.Play)?.move
    val hintHeal = (hint?.action as? Action.Heal)?.square
    val checkSquare = if (game.result == null && Rules.inCheck(game)) Rules.kingSquare(game.board, game.toMove) else null
    val absolvable = Rules.legalAbsolutions(game).toSet()
    val last = game.lastMove
    val measurer = rememberTextMeasurer()

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "pulse",
    )

    fun col(sq: Int) = if (flipped) 7 - Rules.file(sq) else Rules.file(sq)
    fun row(sq: Int) = if (flipped) Rules.rank(sq) else 7 - Rules.rank(sq)

    // Pieces that just left the board get a short fade-out "ghost".
    val previous = remember { BoardRef(game.board) }
    val ghosts = remember(game.board) {
        val present = game.board.mapNotNull { it?.id }.filter { it >= 0 }.toSet()
        previous.board.withIndex().mapNotNull { (sq, p) ->
            if (p != null && p.id >= 0 && p.id !in present) sq to p else null
        }
    }
    SideEffect { previous.board = game.board }

    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .shadow(18.dp, RoundedCornerShape(14.dp), ambientColor = scheme.primary, spotColor = Color.Black)
            .clip(RoundedCornerShape(14.dp))
    ) {
        val cell = maxWidth / 8

        Canvas(Modifier.fillMaxSize()) {
            val c = size.width / 8
            for (sq in 0 until 64) {
                val tl = Offset(col(sq) * c, row(sq) * c)
                val light = (Rules.file(sq) + Rules.rank(sq)) % 2 == 1
                val lastMove = last != null && (sq == last.from || sq == last.to)
                val colour = when {
                    lastMove && light -> LastMoveLight
                    lastMove -> LastMoveDark
                    light -> LightSquare
                    else -> DarkSquare
                }
                drawRect(colour, tl, Size(c, c))
                // The goal squares of King of the Hill and Racing Kings.
                if ((game.mode == Mode.KING_OF_THE_HILL && sq in Rules.HILL) || (game.mode == Mode.RACING_KINGS && Rules.rank(sq) == 7)) {
                    drawRect(Color(0x33FFC66E), tl, Size(c, c))
                    val w = c * 0.05f
                    drawRect(Color(0xCCFFB02E), tl + Offset(w / 2, w / 2), Size(c - w, c - w), style = Stroke(w))
                }
                if (sq == selected) drawRect(scheme.primary.copy(alpha = 0.5f), tl, Size(c, c))
                if (sq in absolvable) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(scheme.tertiary.copy(alpha = 0.75f * pulse), Color.Transparent),
                            center = tl + Offset(c / 2, c / 2), radius = c * 0.75f,
                        ),
                        tl, Size(c, c),
                    )
                }
                if (sq == checkSquare) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(Color(0xFFFF2D3A).copy(alpha = pulse), Color(0x00FF2D3A)),
                            center = tl + Offset(c / 2, c / 2), radius = c * 0.72f,
                        ),
                        tl, Size(c, c),
                    )
                }
                if (sq == game.bonusSquare) {
                    val w = c * 0.08f
                    drawRoundRect(
                        moodColor(Mood.PSYCHOPATH)!!.copy(alpha = pulse),
                        tl + Offset(w / 2, w / 2), Size(c - w, c - w), CornerRadius(c * 0.12f), style = Stroke(w),
                    )
                }
                if (sq in targetSquares) {
                    val centre = tl + Offset(c / 2, c / 2)
                    if (game.board[sq] == null) {
                        drawCircle(Color(0xFF2A1A35).copy(alpha = 0.35f), c * 0.16f, centre)
                    } else {
                        drawCircle(Color(0xFF2A1A35).copy(alpha = 0.4f), c * 0.46f, centre, style = Stroke(c * 0.08f))
                    }
                }
            }
            if (hintHeal != null) {
                val tl = Offset(col(hintHeal) * c, row(hintHeal) * c)
                drawCircle(scheme.tertiary.copy(alpha = 0.9f), c * 0.47f, tl + Offset(c / 2, c / 2), style = Stroke(c * 0.07f))
            }
            // Coordinates along the left and bottom edges.
            val labelStyle = TextStyle(fontSize = (c * 0.2f).toSp(), fontWeight = FontWeight.Bold)
            for (i in 0..7) {
                val rankSq = Rules.sq(if (flipped) 7 else 0, if (flipped) i else 7 - i)
                val rankLight = (Rules.file(rankSq) + Rules.rank(rankSq)) % 2 == 1
                drawText(
                    measurer, "${Rules.rank(rankSq) + 1}", Offset(c * 0.06f, i * c + c * 0.01f),
                    labelStyle.copy(color = if (rankLight) DarkSquare else LightSquare),
                )
                val fileSq = Rules.sq(if (flipped) 7 - i else i, if (flipped) 7 else 0)
                val fileLight = (Rules.file(fileSq) + Rules.rank(fileSq)) % 2 == 1
                val text = "${'a' + Rules.file(fileSq)}"
                val w = measurer.measure(text, labelStyle).size.width
                drawText(
                    measurer, text, Offset(i * c + c - w - c * 0.06f, 7 * c + c * 0.7f),
                    labelStyle.copy(color = if (fileLight) DarkSquare else LightSquare),
                )
            }
        }

        for ((sq, p) in ghosts) key("ghost", p.id) {
            Ghost(p, cell * col(sq), cell * row(sq), cell)
        }
        for (sq in 0 until 64) {
            val p = game.board[sq] ?: continue
            key(if (p.id >= 0) p.id else 1000 + sq) {
                BoardPiece(
                    piece = p,
                    mode = game.mode,
                    x = cell * col(sq),
                    y = cell * row(sq),
                    cell = cell,
                    onTop = last != null && last.to == sq,
                )
            }
        }

        game.explosion?.let { at -> key("boom", game.log.size) { Explosion(cell * (col(at) - 1), cell * (row(at) - 1), cell * 3) } }

        if (hintMove != null) {
            Canvas(Modifier.fillMaxSize().zIndex(2f)) {
                val c = size.width / 8
                val from = Offset(col(hintMove.from) * c + c / 2, row(hintMove.from) * c + c / 2)
                val to = Offset(col(hintMove.to) * c + c / 2, row(hintMove.to) * c + c / 2)
                drawArrow(from, to, c, scheme.tertiary.copy(alpha = 0.85f))
            }
        }

        // Tap targets, one per square, on top of everything; they draw nothing.
        Column(Modifier.fillMaxSize()) {
            for (r in 0..7) Row(Modifier.weight(1f)) {
                for (cIdx in 0..7) {
                    val sq = Rules.sq(if (flipped) 7 - cIdx else cIdx, if (flipped) r else 7 - r)
                    val p = game.board[sq]
                    val description = buildString {
                        append(Rules.squareName(sq))
                        if (p != null) append(", ${p.name}, ${Rules.moodLabel(p.mood, game.mode)}")
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clickable(remember { MutableInteractionSource() }, indication = null) { onTap(sq) }
                            .semantics { contentDescription = description }
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardPiece(piece: Piece, mode: Mode, x: Dp, y: Dp, cell: Dp, onTop: Boolean) {
    val ax = animateDpAsState(x, tween(SLIDE_MS, easing = FastOutSlowInEasing), label = "x")
    val ay = animateDpAsState(y, tween(SLIDE_MS, easing = FastOutSlowInEasing), label = "y")
    val glow by animateColorAsState(moodColor(piece.mood) ?: Color.Transparent, tween(500), label = "glow")

    Box(
        Modifier
            .zIndex(if (onTop) 1f else 0f)
            .offset { IntOffset(ax.value.roundToPx(), ay.value.roundToPx()) }
            .size(cell),
        contentAlignment = Alignment.Center,
    ) {
        // Animated values are read only inside draw/layer lambdas, so a pulsing piece redraws
        // every frame without recomposing.
        when (piece.mood) {
            Mood.GUILTY -> MoodGlow(glow) { 0.8f }
            Mood.DEVASTATED -> MoodGlow(glow) { 0.85f }
            Mood.INFECTED -> MoodGlow(glow) { 0.9f }
            Mood.PSYCHOPATH -> {
                val beat = rememberInfiniteTransition(label = "beat").animateFloat(
                    0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "beat",
                )
                MoodGlow(glow) { beat.value }
            }
            else -> Unit
        }

        val shiver: State<Float> = if (piece.mood == Mood.DEVASTATED) {
            rememberInfiniteTransition(label = "shiver").animateFloat(
                -3.5f, 3.5f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "shiver",
            )
        } else NoShiver

        PieceImage(
            piece.kind, piece.side,
            Modifier
                .fillMaxSize()
                .padding(cell * 0.05f)
                .graphicsLayer {
                    rotationZ = shiver.value
                    alpha = if (piece.mood == Mood.DEVASTATED) 0.78f else 1f
                },
        )

        if (piece.dying) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(cell * 0.03f)
                    .size(cell * 0.34f)
                    .background(moodColor(piece.mood)!!, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    piece.countdown.toString(), color = if (piece.mood == Mood.INFECTED) Color(0xFF1B2A05) else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = (cell.value * 0.22f).sp, lineHeight = (cell.value * 0.22f).sp,
                )
            }
        } else if (piece.mood != Mood.CALM) {
            Text(
                moodEmoji(piece.mood, mode),
                fontSize = (cell.value * 0.26f).sp,
                lineHeight = (cell.value * 0.26f).sp,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = cell * 0.02f, y = -cell * 0.03f),
            )
        }
        if (piece.perks.isNotEmpty()) {
            Text(
                piece.perks.joinToString("") { it.emoji },
                fontSize = (cell.value * 0.2f).sp,
                lineHeight = (cell.value * 0.2f).sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(Color(0xCC1A1022), RoundedCornerShape(cell * 0.1f))
                    .padding(horizontal = cell * 0.02f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(from: Offset, to: Offset, cell: Float, colour: Color) {
    val d = to - from
    val len = d.getDistance()
    if (len < 1f) return
    val dir = d / len
    val normal = Offset(-dir.y, dir.x)
    val head = cell * 0.42f
    val tip = to - dir * (cell * 0.12f)
    val base = tip - dir * head
    drawLine(colour, from + dir * (cell * 0.2f), base, strokeWidth = cell * 0.16f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(base.x + normal.x * head * 0.6f, base.y + normal.y * head * 0.6f)
        lineTo(base.x - normal.x * head * 0.6f, base.y - normal.y * head * 0.6f)
        close()
    }
    drawPath(path, colour)
}

/** Atomic: a burst over the 3×3 squares around a capture. */
@Composable
private fun Explosion(x: Dp, y: Dp, size: Dp) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(700)) }
    Canvas(Modifier.offset(x, y).size(size).zIndex(3f)) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val r = this.size.minDimension / 2 * (0.3f + 0.9f * p)
        drawCircle(
            Brush.radialGradient(
                0f to Color(0xFFFFF3B0).copy(alpha = 1f - p),
                0.4f to Color(0xFFFF8A1F).copy(alpha = 0.9f * (1f - p)),
                1f to Color(0x00FF3D00),
                center = center, radius = r,
            ),
            radius = r,
        )
    }
}

private val NoShiver: State<Float> = mutableStateOf(0f)

private class BoardRef(var board: List<Piece?>)

@Composable
private fun MoodGlow(color: Color, strength: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        val strength = strength()
        val radius = 0.42f + 0.08f * strength
        drawCircle(
            Brush.radialGradient(
                0f to color.copy(alpha = 0.95f * strength),
                0.55f to color.copy(alpha = 0.55f * strength),
                1f to color.copy(alpha = 0f),
                center = center, radius = size.minDimension * radius,
            ),
            radius = size.minDimension * radius,
        )
    }
}

@Composable
private fun Ghost(piece: Piece, x: Dp, y: Dp, cell: Dp) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(420, delayMillis = SLIDE_MS / 2)) }
    PieceImage(
        piece.kind, piece.side,
        Modifier
            .offset(x, y)
            .size(cell)
            .padding(cell * 0.05f)
            .graphicsLayer {
                alpha = 1f - t.value
                scaleX = 1f + 0.5f * t.value
                scaleY = 1f + 0.5f * t.value
                rotationZ = 25f * t.value
            },
    )
}
