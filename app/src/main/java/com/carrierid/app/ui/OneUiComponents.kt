package com.carrierid.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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

private val CardRadius = 26.dp

/**
 * One UI "large title" layout: the top ~30% of the screen is a big centred title
 * that scrolls away; a compact title bar fades in once it's gone.
 */
@Composable
fun LargeTitleScreen(
    title: String,
    subtitle: String?,
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val c = One.colors
    val headerHeight = (LocalConfiguration.current.screenHeightDp * 0.30f).dp
    val collapseAtPx = with(LocalDensity.current) { (headerHeight * 0.6f).toPx() }
    val collapsed by remember(listState, collapseAtPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > collapseAtPx
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item(key = "__large_title") {
                Column(
                    Modifier.fillMaxWidth().height(headerHeight).statusBarsPadding()
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(title, color = c.text, fontSize = 32.sp, textAlign = TextAlign.Center)
                    if (subtitle != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(subtitle, color = c.textSecondary, fontSize = 14.sp,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            content()
        }

        AnimatedVisibility(visible = collapsed, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.fillMaxWidth().background(c.background).statusBarsPadding()
                    .height(56.dp).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(title, color = c.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Small accent-coloured label above a group of cards. */
@Composable
fun GroupHeader(text: String) {
    Text(
        text, color = One.colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 30.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** A standalone rounded card. */
@Composable
fun OneCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius)).background(One.colors.card)
            .padding(18.dp),
        content = content,
    )
}

private fun groupShape(index: Int, size: Int): Shape {
    val top = if (index == 0) CardRadius else 0.dp
    val bottom = if (index == size - 1) CardRadius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * One row of a grouped list. Consecutive cells with the same group render as a
 * single rounded card with inset dividers (One UI settings / contacts style).
 */
@Composable
fun GroupCell(
    index: Int,
    size: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val c = One.colors
    Column(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth()
            .clip(groupShape(index, size)).background(c.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        if (index < size - 1) {
            HorizontalDivider(Modifier.padding(horizontal = 18.dp), thickness = 0.8.dp, color = c.divider)
        }
    }
}

/** Settings-style row: coloured circle icon, title, subtitle, switch. */
@Composable
fun RowScope.SettingContent(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val c = One.colors
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(iconColor),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
        Text(title, color = c.text, fontSize = 16.sp)
        Text(subtitle, color = if (checked) c.accent else c.textSecondary, fontSize = 13.sp)
    }
    Spacer(Modifier.width(8.dp))
    OneSwitch(checked, onToggle)
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
            uncheckedTrackColor = if (c.dark) Color(0xFF4A4A4A) else Color(0xFFCFCFCF),
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
        modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(25.dp))
            .background(background).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, color = c.textSecondary, fontSize = 16.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value, onValueChange = onValueChange, singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Filled.Close, "Clear", tint = c.textSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

/** Filter pill. */
@Composable
fun OnePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = One.colors
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) c.accent else c.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(6.dp)) }
        Text(text, color = if (selected) Color.White else c.text, fontSize = 14.sp,
            fontWeight = FontWeight.Medium)
    }
}

@Composable
fun OneButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = One.colors
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(if (enabled) c.accent else c.field)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else c.textSecondary,
            fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One UI bottom tab bar: icon + label, selected tab bold. */
@Composable
fun OneBottomBar(selected: Int, items: List<Pair<ImageVector, String>>, onSelect: (Int) -> Unit) {
    val c = One.colors
    Column(Modifier.fillMaxWidth().background(c.background)) {
        HorizontalDivider(thickness = 0.5.dp, color = c.divider)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp)) {
            items.forEachIndexed { i, (icon, label) ->
                val sel = i == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(i) },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, null, tint = if (sel) c.text else c.textSecondary,
                        modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(label, fontSize = 12.sp,
                        color = if (sel) c.text else c.textSecondary,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Uses your logo file if present (res/drawable/logo_xxx), otherwise a coloured letter circle. */
@Composable
fun CarrierBadge(carrier: Carrier?, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val logoId = remember(carrier) { carrier?.let { CarrierLogos.drawableId(ctx, it) } ?: 0 }
    if (logoId != 0) {
        Image(painterResource(logoId), contentDescription = carrier?.displayName,
            modifier = modifier.size(size).clip(CircleShape))
    } else {
        Box(
            modifier.size(size).clip(CircleShape)
                .background(carrier?.let { Color(it.colorInt) } ?: Color(0xFFBDBDBD)),
            contentAlignment = Alignment.Center,
        ) {
            Text(carrier?.initial ?: "?", color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.48f).sp)
        }
    }
}

private val AvatarColors = listOf(
    Color(0xFF7FA7F5), Color(0xFF9C8BEA), Color(0xFFF08DAA), Color(0xFFF5A66B),
    Color(0xFF6CC9A8), Color(0xFF5FBEDB), Color(0xFFE9C25F), Color(0xFFB4A08F),
)

/** Contact avatar with the carrier badge pinned to its bottom-right corner. */
@Composable
fun ContactAvatar(name: String, carrier: Carrier?) {
    val c = One.colors
    val color = AvatarColors[(name.hashCode() and 0x7fffffff) % AvatarColors.size]
    Box(Modifier.size(48.dp)) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.trim().firstOrNull()?.uppercase() ?: "#", color = Color.White,
                fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        if (carrier != null) {
            CarrierBadge(
                carrier, 20.dp,
                Modifier.align(Alignment.BottomEnd).border(2.dp, c.card, CircleShape),
            )
        }
    }
}
