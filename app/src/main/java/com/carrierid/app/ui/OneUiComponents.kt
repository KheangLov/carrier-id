package com.carrierid.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carrierid.app.carrier.Carrier
import com.carrierid.app.carrier.CarrierLogos
import com.carrierid.app.ui.theme.One
import com.carrierid.app.ui.theme.SystemFont

/** One spacing scale for the whole app, so every screen lines up. */
object Dimens {
    val PageH = 16.dp          // card edge ↔ screen edge
    val CardRadius = 24.dp
    val RowH = 18.dp           // text inset inside a card
    val RowV = 14.dp
    val SectionTop = 26.dp
    val SectionBottom = 8.dp
    val Gap = 14.dp            // icon/avatar ↔ text
    /** Where text starts inside a card, measured from the card's left edge. */
    fun textStart(leading: Dp) = RowH + leading + Gap
}

/**
 * Large-title layout: a big title in the top part of the screen that fades as
 * you scroll, then a compact title bar slides in over a soft fade.
 */
@Composable
fun LargeTitleScreen(
    title: String,
    subtitle: String?,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val c = One.colors
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val headerHeight = maxOf(200.dp, screenH * 0.27f)
    val collapseAtPx = with(LocalDensity.current) { (headerHeight * 0.55f).toPx() }
    val collapsed by remember(listState, collapseAtPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapseAtPx
        }
    }
    val headerFade by remember(listState, collapseAtPx) {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 0f
            else (1f - listState.firstVisibleItemScrollOffset / collapseAtPx).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Leave room so the last rows can scroll above the floating glass tab bar
            contentPadding = PaddingValues(bottom = 24.dp + LocalBottomBarSpace.current),
        ) {
            item(key = "__large_title") {
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding().height(headerHeight)
                        .padding(horizontal = 32.dp)
                        .graphicsLayer {
                            alpha = headerFade
                            val s = 0.94f + 0.06f * headerFade
                            scaleX = s; scaleY = s
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        title, color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Normal,
                        letterSpacing = (-0.4).sp, textAlign = TextAlign.Center,
                    )
                    if (subtitle != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(subtitle, color = c.textSecondary, fontSize = 14.sp,
                            lineHeight = 19.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            content()
        }

        AnimatedVisibility(
            visible = collapsed,
            enter = fadeIn() + slideInVertically { -it / 3 },
            exit = fadeOut() + slideOutVertically { -it / 3 },
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(
                        0f to c.background, 0.72f to c.background.copy(alpha = 0.92f),
                        1f to c.background.copy(alpha = 0f),
                    ))
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Small label above a group of cards, aligned with the text inside them. */
@Composable
fun GroupHeader(text: String) {
    Text(
        text, color = One.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
        modifier = Modifier.padding(
            start = Dimens.PageH + Dimens.RowH, end = Dimens.PageH + Dimens.RowH,
            top = Dimens.SectionTop, bottom = Dimens.SectionBottom,
        ),
    )
}

/** Small grey note under a group. */
@Composable
fun GroupFooter(text: String) {
    Text(
        text, color = One.colors.textSecondary, fontSize = 12.5.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(
            start = Dimens.PageH + Dimens.RowH, end = Dimens.PageH + Dimens.RowH, top = 10.dp,
        ),
    )
}

/** A standalone rounded card. */
@Composable
fun OneCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = Dimens.PageH).fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.CardRadius)).background(One.colors.card)
            .padding(horizontal = Dimens.RowH, vertical = 18.dp),
        content = content,
    )
}

private fun groupShape(index: Int, size: Int): Shape {
    val top = if (index == 0) Dimens.CardRadius else 0.dp
    val bottom = if (index == size - 1) Dimens.CardRadius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * One row of a grouped list. Consecutive cells render as a single rounded card.
 * The divider starts at [dividerStart] so it lines up with the row's text.
 */
@Composable
fun GroupCell(
    index: Int,
    size: Int,
    onClick: (() -> Unit)? = null,
    dividerStart: Dp = Dimens.RowH,
    content: @Composable RowScope.() -> Unit,
) {
    val c = One.colors
    Column(
        Modifier.padding(horizontal = Dimens.PageH).fillMaxWidth()
            .clip(groupShape(index, size)).background(c.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .padding(horizontal = Dimens.RowH, vertical = Dimens.RowV),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (index < size - 1) {
            HorizontalDivider(
                Modifier.padding(start = dividerStart, end = Dimens.RowH),
                thickness = 0.6.dp, color = c.divider,
            )
        }
    }
}

/** Two-line text block used inside rows. */
@Composable
fun RowScope.RowTexts(title: String, subtitle: String?, subtitleColor: Color = One.colors.textSecondary) {
    val c = One.colors
    Column(Modifier.weight(1f)) {
        Text(title, color = c.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = subtitleColor, fontSize = 13.sp, lineHeight = 17.sp)
        }
    }
}

/** Rounded-square tile with a white icon, as used in Samsung/Apple settings lists. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.56f)) }
}

/** Settings row: icon tile, title, subtitle, switch. */
@Composable
fun RowScope.SettingContent(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    IconTile(icon, iconColor)
    Spacer(Modifier.width(Dimens.Gap))
    RowTexts(title, subtitle, if (checked) One.colors.accent else One.colors.textSecondary)
    Spacer(Modifier.width(10.dp))
    OneSwitch(checked, onToggle)
}

@Composable
fun Chevron() {
    Icon(AppIcons.Chevron, null, tint = One.colors.textSecondary.copy(alpha = 0.6f),
        modifier = Modifier.size(18.dp))
}

@Composable
fun OneSwitch(checked: Boolean, onToggle: () -> Unit) {
    val c = One.colors
    Switch(
        checked = checked,
        onCheckedChange = { onToggle() },
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = if (c.dark) Color(0xFF3A3A3C) else Color(0xFFD9D9DE),
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Pill-shaped text field (search bar / number input). */
@Composable
fun OneTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = One.colors.card,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = One.colors
    Row(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp))
            .background(background).padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = c.textSecondary, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 16.sp, fontFamily = SystemFont),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(c.textSecondary.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Close, "Clear", tint = c.card, modifier = Modifier.size(12.dp)) }
            }
        }
    }
}

/** Search bar placed at page width. */
@Composable
fun SearchBar(value: String, onValueChange: (String) -> Unit) {
    OneTextField(
        value, onValueChange, placeholder = "Search", icon = AppIcons.Search,
        modifier = Modifier.padding(horizontal = Dimens.PageH),
    )
}

/** Horizontally scrolling row of filter pills, aligned to the page edge. */
@Composable
fun PillRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.PageH, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Filter pill: selected = solid (inverse colours), unselected = card. */
@Composable
fun OnePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = One.colors
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (selected) c.text else c.card)
            .clickable(onClick = onClick)
            .padding(start = if (leading != null) 8.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (selected) c.background else c.text, fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
fun OneButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = One.colors
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
            .background(if (enabled) c.accent else c.field)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else c.textSecondary,
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Centred message for empty / permission states. */
@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val c = One.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(c.card),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(16.dp))
        Text(title, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = c.textSecondary, fontSize = 14.sp, lineHeight = 19.sp,
            textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Box(Modifier.widthIn(max = 220.dp)) { OneButton(action, onClick = onAction) }
        }
    }
}

/**
 * Carrier logo in a white circle. Uses the logo picked in Settings → Networks
 * (or a bundled one); falls back to a coloured letter circle when there is none.
 */
@Composable
fun CarrierBadge(carrier: Carrier?, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val version by CarrierLogos.version
    val logo = remember(carrier, version) {
        carrier?.let { CarrierLogos.bitmap(ctx, it)?.asImageBitmap() }
    }
    if (logo != null) {
        Box(
            modifier.size(size).clip(CircleShape).background(Color.White)
                .border(0.5.dp, Color(0x1F000000), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                logo, contentDescription = carrier?.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size * 0.12f),
            )
        }
    } else {
        Box(
            modifier.size(size).clip(CircleShape)
                .background(carrier?.let { Color(it.colorInt) } ?: Color(0xFFBDBDBD)),
            contentAlignment = Alignment.Center,
        ) {
            Text(carrier?.initial ?: "?", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.46f).sp)
        }
    }
}

private val AvatarColors = listOf(
    Color(0xFF7FA7F5), Color(0xFF9C8BEA), Color(0xFFF08DAA), Color(0xFFF5A66B),
    Color(0xFF6CC9A8), Color(0xFF5FBEDB), Color(0xFFE9C25F), Color(0xFFB4A08F),
)

val AvatarSize = 44.dp

/** Contact avatar with the carrier badge pinned to its bottom-right corner. */
@Composable
fun ContactAvatar(name: String, carrier: Carrier?) {
    val c = One.colors
    val first = name.trim().firstOrNull()
    val isLetter = first != null && first.isLetter()
    val color = if (!isLetter) (if (c.dark) Color(0xFF3A3F47) else Color(0xFFC5CCD6))
    else AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size]
    Box(Modifier.size(AvatarSize)) {
        Box(
            Modifier.size(AvatarSize).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            if (isLetter) {
                Text(first!!.uppercase(), color = Color.White,
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Icon(AppIcons.PersonFilled, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        if (carrier != null) {
            CarrierBadge(
                carrier, 18.dp,
                Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)
                    .border(2.dp, c.card, CircleShape),
            )
        }
    }
}
