package com.duzman46.gridbound.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * The home screen's icon set: the plain, universal marks a player already knows.
 *
 * The rest of the app draws its icons out of board parts — tiles, wall bars, pawns — and that
 * set is right where it lives, on controls that act on a board. It is wrong here. A menu is not
 * a board, and a row of abstract bars and squares gives a first-time player nothing to
 * recognise: three of them read as the same mark at 26 dp, which is exactly what the home
 * screen looked like before this file existed.
 *
 * So these are the conventional shapes, drawn to one specification rather than imported from a
 * vendor set: a single stroke weight, one corner treatment, one optical size. Imported Material
 * glyphs would be a third design language on a screen that already has two, and they carry
 * their own metrics — that is what makes an interface look assembled rather than designed.
 *
 * Every one of them is a single flat colour. Colour is decided by the caller, and on this
 * screen the ration is strict: two of seven are gold, the rest are grey.
 */
enum class PremiumIcon {
    /** Standings. A cup on a stem. */
    TROPHY,

    /** Other people. Two heads and shoulders. */
    PEOPLE,

    /** Learning the rules. A mortarboard. */
    MORTARBOARD,

    /** Preferences. Two sliders on their tracks. */
    SLIDERS,

    /** The day's objectives. A shield with a star. */
    SHIELD_STAR,

    /** Where a session starts. A house. */
    HOUSE,

    /** Matches. A gamepad. */
    GAMEPAD,

    /** The player themselves. A head and shoulders. */
    PERSON,

    /** Career numbers. Three bars of a chart. */
    BARS,

    /** Settings, at the top of the screen. A cogwheel. */
    COG,

    /** Playing other people. A globe with a figure beside it. */
    GLOBE,

    /** Playing the machine. A robot's head. */
    ROBOT,

    /** The app's language. A globe with meridians only — no figure, because it is not a mode. */
    LANGUAGE,

    /** Making something that did not exist. A plus. */
    PLUS,

    /** A stretch of time. A calendar with its two rings. */
    CALENDAR,

    /** Speed. A lightning bolt, for the badge earned by finishing a match quickly. */
    BOLT,

    /** Everything else the app can do. Three dots in a row. */
    ELLIPSIS,

    /** Something written down and agreed to. A page with lines on it. */
    DOCUMENT,

    /** A fact rather than an action. An i in a ring. */
    INFO,

    /** A badge earned. Five points, filled. */
    STAR,

    /** Writing to the people who made this. An envelope. */
    ENVELOPE,

    /** Where the game is talked about. A speech bubble. */
    CHAT,

    /** Light or dark. A circle half filled, which is what the choice actually is. */
    CONTRAST,

    /** Sound. A speaker with two waves coming off it. */
    SPEAKER,

    /** Vibration. A handset with a stroke either side of it. */
    VIBRATE,

    /** Advertising, refused. A circle with a bar through it. */
    NO_ADS,

    /** Something the app wants to tell you when you are not looking. A bell. */
    BELL,
}

/** One stroke weight across the whole set, as a fraction of the icon's box. */
private const val STROKE = 0.085f

@Composable
fun PremiumGlyph(icon: PremiumIcon, modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) { drawPremiumIcon(icon, tint) }
}

/**
 * The same set, for the places that take a drawing rather than a composable.
 *
 * The forms in this app pass their marks as `DrawScope.() -> Unit` lambdas — a field's leading
 * mark, a dialog's crest, the tick inside a choice — so a `Canvas` composable cannot be handed to
 * them. Rather than draw a second set of icons for those, the whole `when` lives here and the
 * composable is three lines around it.
 */
fun DrawScope.drawPremiumIcon(icon: PremiumIcon, tint: Color) {
    val s = size.minDimension
    val line = s * STROKE
    when (icon) {
            PremiumIcon.TROPHY -> trophy(s, tint, line)
            PremiumIcon.PEOPLE -> people(s, tint)
            PremiumIcon.MORTARBOARD -> mortarboard(s, tint, line)
            PremiumIcon.SLIDERS -> sliders(s, tint, line)
            PremiumIcon.SHIELD_STAR -> shieldStar(s, tint)
            PremiumIcon.HOUSE -> house(s, tint)
            PremiumIcon.GAMEPAD -> gamepad(s, tint)
            PremiumIcon.PERSON -> person(s, tint)
            PremiumIcon.BARS -> bars(s, tint)
            PremiumIcon.COG -> cog(s, tint, line)
            PremiumIcon.GLOBE -> globe(s, tint, line, withFigure = true)
            PremiumIcon.LANGUAGE -> globe(s, tint, line, withFigure = false)
            PremiumIcon.ROBOT -> robot(s, tint, line)
            PremiumIcon.PLUS -> plus(s, tint, line)
            PremiumIcon.CALENDAR -> calendar(s, tint, line)
            PremiumIcon.BOLT -> bolt(s, tint)
            PremiumIcon.ELLIPSIS -> ellipsis(s, tint)
            PremiumIcon.DOCUMENT -> document(s, tint, line)
            PremiumIcon.INFO -> info(s, tint, line)
            PremiumIcon.STAR -> star(s, tint)
            PremiumIcon.ENVELOPE -> envelope(s, tint, line)
            PremiumIcon.CHAT -> chat(s, tint, line)
            PremiumIcon.CONTRAST -> contrast(s, tint, line)
            PremiumIcon.SPEAKER -> speaker(s, tint, line)
            PremiumIcon.VIBRATE -> vibrate(s, tint, line)
        PremiumIcon.NO_ADS -> noAds(s, tint, line)
        PremiumIcon.BELL -> bell(s, tint, line)
    }
}

/** A bell: a dome on a rim, with the clapper below it. */
private fun DrawScope.bell(s: Float, tint: Color, line: Float) {
    drawPath(
        Path().apply {
            moveTo(s * 0.22f, s * 0.68f)
            cubicTo(s * 0.30f, s * 0.62f, s * 0.28f, s * 0.52f, s * 0.28f, s * 0.44f)
            cubicTo(s * 0.28f, s * 0.24f, s * 0.38f, s * 0.14f, s * 0.50f, s * 0.14f)
            cubicTo(s * 0.62f, s * 0.14f, s * 0.72f, s * 0.24f, s * 0.72f, s * 0.44f)
            cubicTo(s * 0.72f, s * 0.52f, s * 0.70f, s * 0.62f, s * 0.78f, s * 0.68f)
            close()
        },
        tint,
        style = Stroke(width = line, join = StrokeJoin.Round),
    )
    // The handle on top and the clapper under: without them a bell reads as a hill.
    drawLine(tint, Offset(s * 0.44f, s * 0.13f), Offset(s * 0.56f, s * 0.13f), line, StrokeCap.Round)
    drawArc(
        color = tint,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(s * 0.40f, s * 0.70f),
        size = Size(s * 0.20f, s * 0.20f),
        style = Stroke(width = line, cap = StrokeCap.Round),
    )
}

/** A circle with one half filled: the light-or-dark choice, drawn as the thing itself. */
private fun DrawScope.contrast(s: Float, tint: Color, line: Float) {
    val radius = s * 0.40f
    drawCircle(tint, radius, Offset(s * 0.5f, s * 0.5f), style = Stroke(line))
    drawArc(
        color = tint,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.5f - radius, s * 0.5f - radius),
        size = Size(radius * 2f, radius * 2f),
    )
}

/**
 * A speaker: a square box at the back, a cone opening from it, and two arcs of sound.
 *
 * The first attempt was one six-sided path — a box and a cone drawn as a single silhouette —
 * and at 21 dp it read as an arrowhead with two loose brackets beside it. The shape a speaker
 * needs is the *notch* where the box meets the cone; the outline alone does not show it, so the
 * box is drawn as its own rectangle and the cone as its own triangle, with the arcs concentric
 * on the cone's mouth rather than on the middle of the icon.
 */
private fun DrawScope.speaker(s: Float, tint: Color, line: Float) {
    // The box: short, centred on the axis, with the corners eased.
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.06f, s * 0.36f),
        size = Size(s * 0.20f, s * 0.28f),
        cornerRadius = CornerRadius(s * 0.04f),
    )
    // The cone, opening away from it.
    drawPath(
        Path().apply {
            moveTo(s * 0.24f, s * 0.42f)
            lineTo(s * 0.50f, s * 0.12f)
            lineTo(s * 0.50f, s * 0.88f)
            lineTo(s * 0.24f, s * 0.58f)
            close()
        },
        tint,
    )
    // Two arcs sharing the cone's mouth as their centre, so they read as one sound spreading.
    listOf(0.19f to 0.32f, 0.34f to 0.46f).forEach { (radius, _) ->
        drawArc(
            color = tint,
            startAngle = -52f,
            sweepAngle = 104f,
            useCenter = false,
            topLeft = Offset(s * (0.56f - radius), s * (0.5f - radius)),
            size = Size(s * radius * 2f, s * radius * 2f),
            style = Stroke(width = line * 0.95f, cap = StrokeCap.Round),
        )
    }
}

/** A handset, with a short stroke either side of it saying that it is moving. */
private fun DrawScope.vibrate(s: Float, tint: Color, line: Float) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.32f, s * 0.14f),
        size = Size(s * 0.36f, s * 0.72f),
        cornerRadius = CornerRadius(s * 0.09f),
        style = Stroke(line),
    )
    drawLine(
        tint,
        Offset(s * 0.44f, s * 0.24f),
        Offset(s * 0.56f, s * 0.24f),
        strokeWidth = line * 0.8f,
        cap = StrokeCap.Round,
    )
    listOf(0.14f, 0.86f).forEach { x ->
        drawLine(
            tint,
            Offset(s * x, s * 0.36f),
            Offset(s * x, s * 0.64f),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
    }
}

/** A circle with a bar through it: advertising, refused. */
private fun DrawScope.noAds(s: Float, tint: Color, line: Float) {
    drawCircle(tint, s * 0.40f, Offset(s * 0.5f, s * 0.5f), style = Stroke(line))
    drawLine(
        tint,
        Offset(s * 0.22f, s * 0.78f),
        Offset(s * 0.78f, s * 0.22f),
        strokeWidth = line,
        cap = StrokeCap.Round,
    )
}

/** An envelope: the box, and the flap folded down into it. */
private fun DrawScope.envelope(s: Float, tint: Color, line: Float) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.08f, s * 0.22f),
        size = Size(s * 0.84f, s * 0.56f),
        cornerRadius = CornerRadius(s * 0.10f),
        style = Stroke(line),
    )
    drawPath(
        Path().apply {
            moveTo(s * 0.12f, s * 0.28f)
            lineTo(s * 0.50f, s * 0.56f)
            lineTo(s * 0.88f, s * 0.28f)
        },
        tint,
        style = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** A speech bubble with three dots in it, and a tail on the leading side. */
private fun DrawScope.chat(s: Float, tint: Color, line: Float) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.08f, s * 0.16f),
        size = Size(s * 0.84f, s * 0.58f),
        cornerRadius = CornerRadius(s * 0.18f),
        style = Stroke(line),
    )
    drawPath(
        Path().apply {
            moveTo(s * 0.26f, s * 0.72f)
            lineTo(s * 0.24f, s * 0.94f)
            lineTo(s * 0.46f, s * 0.74f)
            close()
        },
        tint,
    )
    listOf(0.32f, 0.50f, 0.68f).forEach { x ->
        drawCircle(tint, s * 0.055f, Offset(s * x, s * 0.45f))
    }
}

/** A five-pointed star, filled. The mark for a badge earned. */
private fun DrawScope.star(s: Float, tint: Color) {
    val path = Path()
    for (index in 0 until 10) {
        val radius = if (index % 2 == 0) s * 0.48f else s * 0.20f
        val angle = Math.toRadians((-90 + index * 36).toDouble())
        val x = s * 0.5f + (radius * Math.cos(angle)).toFloat()
        val y = s * 0.5f + (radius * Math.sin(angle)).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, tint)
}

/** A page with a folded corner and three lines of text. */
private fun DrawScope.document(s: Float, tint: Color, line: Float) {
    drawPath(
        Path().apply {
            moveTo(s * 0.20f, s * 0.08f)
            lineTo(s * 0.62f, s * 0.08f)
            lineTo(s * 0.82f, s * 0.30f)
            lineTo(s * 0.82f, s * 0.92f)
            lineTo(s * 0.20f, s * 0.92f)
            close()
        },
        tint,
        style = Stroke(width = line, join = StrokeJoin.Round),
    )
    listOf(0.50f, 0.64f, 0.78f).forEach { y ->
        drawLine(
            tint,
            Offset(s * 0.32f, s * y),
            Offset(s * 0.70f, s * y),
            strokeWidth = line * 0.8f,
            cap = StrokeCap.Round,
        )
    }
}

/** An i in a ring, for a line that states something rather than doing something. */
private fun DrawScope.info(s: Float, tint: Color, line: Float) {
    drawCircle(tint, s * 0.42f, Offset(s * 0.5f, s * 0.5f), style = Stroke(line))
    drawCircle(tint, s * 0.055f, Offset(s * 0.5f, s * 0.30f))
    drawLine(
        tint,
        Offset(s * 0.5f, s * 0.44f),
        Offset(s * 0.5f, s * 0.72f),
        strokeWidth = line,
        cap = StrokeCap.Round,
    )
}

/** A lightning bolt: one closed shape, no outline, so it holds at 20 dp. */
private fun DrawScope.bolt(s: Float, tint: Color) {
    drawPath(
        Path().apply {
            moveTo(s * 0.58f, s * 0.04f)
            lineTo(s * 0.20f, s * 0.56f)
            lineTo(s * 0.45f, s * 0.56f)
            lineTo(s * 0.40f, s * 0.96f)
            lineTo(s * 0.80f, s * 0.42f)
            lineTo(s * 0.54f, s * 0.42f)
            close()
        },
        tint,
    )
}

/** Three dots in a row, for the place everything else lives. */
private fun DrawScope.ellipsis(s: Float, tint: Color) {
    listOf(0.20f, 0.50f, 0.80f).forEach { x ->
        drawCircle(tint, s * 0.10f, Offset(s * x, s * 0.5f))
    }
}

/** A calendar: a box, a rule under its head, and the two rings it hangs from. */
private fun DrawScope.calendar(s: Float, tint: Color, line: Float) {
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.10f, s * 0.20f),
        size = Size(s * 0.80f, s * 0.70f),
        cornerRadius = CornerRadius(s * 0.12f),
        style = Stroke(line),
    )
    drawLine(
        tint,
        Offset(s * 0.10f, s * 0.42f),
        Offset(s * 0.90f, s * 0.42f),
        strokeWidth = line * 0.9f,
    )
    listOf(0.32f, 0.68f).forEach { x ->
        drawLine(
            tint,
            Offset(s * x, s * 0.08f),
            Offset(s * x, s * 0.28f),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * A plus, drawn to the set's own weight rather than as two thin hairlines.
 *
 * It sits beside the join mark on the lobby's pair of cards, and the two have to read as
 * equals at 20 dp: a stroke lighter than the rest of the set would make "create" look like
 * the lesser of the two before anyone read a word.
 */
private fun DrawScope.plus(s: Float, tint: Color, line: Float) {
    val bar = line * 1.35f
    drawLine(
        tint,
        Offset(s * 0.5f, s * 0.14f),
        Offset(s * 0.5f, s * 0.86f),
        strokeWidth = bar,
        cap = StrokeCap.Round,
    )
    drawLine(
        tint,
        Offset(s * 0.14f, s * 0.5f),
        Offset(s * 0.86f, s * 0.5f),
        strokeWidth = bar,
        cap = StrokeCap.Round,
    )
}

/**
 * A globe, drawn in outline.
 *
 * The mode icons on the play screen are larger than the marks on a menu row and they are the
 * only thing distinguishing three otherwise identical cards, so these are stroked rather than
 * filled — an outline holds its detail at 40 dp where a solid shape becomes a blob.
 *
 * [withFigure] is what separates the two things a globe can mean here. With a person beside it
 * the globe is *other players*, which is the online mode; without one it is simply the world,
 * which is the language control. Same drawing, one addition, two meanings that never collide
 * because they never appear on the same screen.
 */
private fun DrawScope.globe(s: Float, tint: Color, line: Float, withFigure: Boolean) {
    val radius = if (withFigure) s * 0.33f else s * 0.40f
    val centre = if (withFigure) Offset(s * 0.42f, s * 0.42f) else Offset(s * 0.5f, s * 0.5f)
    drawCircle(tint, radius, centre, style = Stroke(line))
    // The equator, and two meridians drawn as ellipses of decreasing width.
    drawLine(
        tint,
        Offset(centre.x - radius, centre.y),
        Offset(centre.x + radius, centre.y),
        strokeWidth = line,
    )
    listOf(0.42f, 0.86f).forEach { squeeze ->
        drawOval(
            color = tint,
            topLeft = Offset(centre.x - radius * squeeze, centre.y - radius),
            size = Size(radius * 2f * squeeze, radius * 2f),
            style = Stroke(line * 0.85f),
        )
    }
    if (withFigure) {
        // The other player, standing in front of the world.
        val fx = s * 0.74f
        val fy = s * 0.66f
        drawCircle(tint, s * 0.115f, Offset(fx, fy - s * 0.10f))
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(fx - s * 0.185f, fy + s * 0.02f),
            size = Size(s * 0.37f, s * 0.30f),
        )
    }
}

/**
 * A robot's head: a rounded box, two eyes, an aerial.
 *
 * Deliberately friendly rather than menacing. The bot is the mode a beginner picks first, and an
 * icon that looks like a threat is an icon that says "not for you".
 */
private fun DrawScope.robot(s: Float, tint: Color, line: Float) {
    // Aerial
    drawLine(tint, Offset(s * 0.5f, s * 0.06f), Offset(s * 0.5f, s * 0.20f), strokeWidth = line)
    drawCircle(tint, s * 0.06f, Offset(s * 0.5f, s * 0.06f))
    // Head
    drawRoundRect(
        color = tint,
        topLeft = Offset(s * 0.13f, s * 0.22f),
        size = Size(s * 0.74f, s * 0.56f),
        cornerRadius = CornerRadius(s * 0.18f),
        style = Stroke(line),
    )
    // Eyes
    drawCircle(tint, s * 0.075f, Offset(s * 0.35f, s * 0.48f))
    drawCircle(tint, s * 0.075f, Offset(s * 0.65f, s * 0.48f))
    // Mouth
    drawLine(
        tint,
        Offset(s * 0.36f, s * 0.63f),
        Offset(s * 0.64f, s * 0.63f),
        strokeWidth = line * 0.9f,
        cap = StrokeCap.Round,
    )
    // Ears
    listOf(0.05f, 0.87f).forEach { x ->
        drawRoundRect(
            color = tint,
            topLeft = Offset(s * x, s * 0.40f),
            size = Size(s * 0.08f, s * 0.20f),
            cornerRadius = CornerRadius(s * 0.04f),
        )
    }
}

private fun DrawScope.trophy(s: Float, tint: Color, line: Float) {
    // Cup
    val cup = Path().apply {
        moveTo(s * 0.28f, s * 0.14f)
        lineTo(s * 0.72f, s * 0.14f)
        lineTo(s * 0.72f, s * 0.40f)
        cubicTo(s * 0.72f, s * 0.58f, s * 0.62f, s * 0.66f, s * 0.50f, s * 0.66f)
        cubicTo(s * 0.38f, s * 0.66f, s * 0.28f, s * 0.58f, s * 0.28f, s * 0.40f)
        close()
    }
    drawPath(cup, tint)
    // Handles
    drawArc(
        color = tint,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(s * 0.10f, s * 0.18f),
        size = Size(s * 0.22f, s * 0.26f),
        style = Stroke(width = line, cap = StrokeCap.Round),
    )
    drawArc(
        color = tint,
        startAngle = 270f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(s * 0.68f, s * 0.18f),
        size = Size(s * 0.22f, s * 0.26f),
        style = Stroke(width = line, cap = StrokeCap.Round),
    )
    // Stem and foot
    drawRect(tint, Offset(s * 0.455f, s * 0.66f), Size(s * 0.09f, s * 0.14f))
    drawRoundRect(
        tint,
        Offset(s * 0.30f, s * 0.80f),
        Size(s * 0.40f, s * 0.09f),
        CornerRadius(s * 0.03f),
    )
}

private fun DrawScope.people(s: Float, tint: Color) {
    // The one behind, quieter.
    drawCircle(tint.copy(alpha = 0.55f), s * 0.13f, Offset(s * 0.68f, s * 0.34f))
    drawArc(
        color = tint.copy(alpha = 0.55f),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.50f, s * 0.52f),
        size = Size(s * 0.42f, s * 0.36f),
    )
    // The one in front.
    drawCircle(tint, s * 0.155f, Offset(s * 0.40f, s * 0.32f))
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.16f, s * 0.50f),
        size = Size(s * 0.48f, s * 0.40f),
    )
}

private fun DrawScope.mortarboard(s: Float, tint: Color, line: Float) {
    // The board: a diamond seen from slightly above.
    drawPath(
        Path().apply {
            moveTo(s * 0.50f, s * 0.16f)
            lineTo(s * 0.94f, s * 0.36f)
            lineTo(s * 0.50f, s * 0.56f)
            lineTo(s * 0.06f, s * 0.36f)
            close()
        },
        tint,
    )
    // The cap under it.
    drawPath(
        Path().apply {
            moveTo(s * 0.24f, s * 0.43f)
            lineTo(s * 0.24f, s * 0.66f)
            cubicTo(s * 0.24f, s * 0.80f, s * 0.76f, s * 0.80f, s * 0.76f, s * 0.66f)
            lineTo(s * 0.76f, s * 0.43f)
            lineTo(s * 0.50f, s * 0.55f)
            close()
        },
        tint.copy(alpha = 0.72f),
    )
    // The tassel.
    drawLine(
        tint,
        Offset(s * 0.90f, s * 0.38f),
        Offset(s * 0.90f, s * 0.70f),
        strokeWidth = line * 0.8f,
        cap = StrokeCap.Round,
    )
    drawCircle(tint, s * 0.055f, Offset(s * 0.90f, s * 0.76f))
}

private fun DrawScope.sliders(s: Float, tint: Color, line: Float) {
    listOf(0.34f to 0.62f, 0.66f to 0.38f).forEach { (y, knob) ->
        drawLine(
            tint.copy(alpha = 0.6f),
            Offset(s * 0.08f, s * y),
            Offset(s * 0.92f, s * y),
            strokeWidth = line * 0.85f,
            cap = StrokeCap.Round,
        )
        drawCircle(tint, s * 0.115f, Offset(s * knob, s * y))
    }
}

private fun DrawScope.shieldStar(s: Float, tint: Color) {
    val shield = Path().apply {
        moveTo(s * 0.50f, s * 0.07f)
        lineTo(s * 0.88f, s * 0.22f)
        lineTo(s * 0.88f, s * 0.52f)
        cubicTo(s * 0.88f, s * 0.76f, s * 0.70f, s * 0.89f, s * 0.50f, s * 0.95f)
        cubicTo(s * 0.30f, s * 0.89f, s * 0.12f, s * 0.76f, s * 0.12f, s * 0.52f)
        lineTo(s * 0.12f, s * 0.22f)
        close()
    }
    drawPath(shield, tint)
    // A five-pointed star cut out of it, in the ground behind, so the shield reads as solid.
    val star = Path()
    val cx = s * 0.50f
    val cy = s * 0.47f
    val outer = s * 0.20f
    val inner = s * 0.086f
    for (index in 0 until 10) {
        val radius = if (index % 2 == 0) outer else inner
        val angle = Math.toRadians((-90 + index * 36).toDouble())
        val x = cx + (radius * Math.cos(angle)).toFloat()
        val y = cy + (radius * Math.sin(angle)).toFloat()
        if (index == 0) star.moveTo(x, y) else star.lineTo(x, y)
    }
    star.close()
    drawPath(star, Color(0xFF0E1216))
}

private fun DrawScope.house(s: Float, tint: Color) {
    drawPath(
        Path().apply {
            moveTo(s * 0.50f, s * 0.10f)
            lineTo(s * 0.95f, s * 0.48f)
            lineTo(s * 0.83f, s * 0.48f)
            lineTo(s * 0.83f, s * 0.90f)
            lineTo(s * 0.17f, s * 0.90f)
            lineTo(s * 0.17f, s * 0.48f)
            lineTo(s * 0.05f, s * 0.48f)
            close()
        },
        tint,
    )
    // A door punched through, so the mass reads as a house rather than an arrow.
    drawRoundRect(
        Color(0xFF0D1115),
        Offset(s * 0.40f, s * 0.58f),
        Size(s * 0.20f, s * 0.32f),
        CornerRadius(s * 0.04f),
    )
}

private fun DrawScope.gamepad(s: Float, tint: Color) {
    drawRoundRect(
        tint,
        Offset(s * 0.06f, s * 0.30f),
        Size(s * 0.88f, s * 0.40f),
        CornerRadius(s * 0.18f),
    )
    // Grips
    drawCircle(tint, s * 0.145f, Offset(s * 0.20f, s * 0.72f))
    drawCircle(tint, s * 0.145f, Offset(s * 0.80f, s * 0.72f))
    // The pad and two buttons, punched out.
    val hole = Color(0xFF0D1115)
    drawRoundRect(hole, Offset(s * 0.16f, s * 0.455f), Size(s * 0.20f, s * 0.07f), CornerRadius(s * 0.035f))
    drawRoundRect(hole, Offset(s * 0.225f, s * 0.39f), Size(s * 0.07f, s * 0.20f), CornerRadius(s * 0.035f))
    drawCircle(hole, s * 0.055f, Offset(s * 0.70f, s * 0.43f))
    drawCircle(hole, s * 0.055f, Offset(s * 0.80f, s * 0.55f))
}

private fun DrawScope.person(s: Float, tint: Color) {
    drawCircle(tint, s * 0.185f, Offset(s * 0.50f, s * 0.30f))
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(s * 0.16f, s * 0.54f),
        size = Size(s * 0.68f, s * 0.56f),
    )
}

private fun DrawScope.bars(s: Float, tint: Color) {
    val width = s * 0.18f
    listOf(0.52f, 0.80f, 0.36f).forEachIndexed { index, height ->
        val x = s * 0.14f + index * s * 0.27f
        drawRoundRect(
            if (index == 1) tint else tint.copy(alpha = 0.62f),
            Offset(x, s * (0.88f - height)),
            Size(width, s * height),
            CornerRadius(s * 0.045f),
        )
    }
}

private fun DrawScope.cog(s: Float, tint: Color, line: Float) {
    val teeth = 8
    val outer = s * 0.46f
    val inner = s * 0.34f
    val path = Path()
    for (index in 0 until teeth * 2) {
        val radius = if (index % 2 == 0) outer else inner
        val angle = Math.toRadians((index * 360.0 / (teeth * 2)) - 90.0)
        val x = s * 0.5f + (radius * Math.cos(angle)).toFloat()
        val y = s * 0.5f + (radius * Math.sin(angle)).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, tint)
    drawCircle(Color(0xFF11151A), s * 0.155f, Offset(s * 0.5f, s * 0.5f))
    drawCircle(tint, s * 0.155f, Offset(s * 0.5f, s * 0.5f), style = Stroke(line * 0.5f))
}
