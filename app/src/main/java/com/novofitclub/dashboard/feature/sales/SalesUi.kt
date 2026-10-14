package com.novofitclub.dashboard.feature.sales

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.SecondaryButton

/*
 * Small UI pieces shared by the Ventas screens (the iOS app has these inline
 * in each view: round icon buttons, search fields, sheet toolbars, …).
 */

/**
 * Gives [content] its own ViewModelStore that is cleared when it leaves
 * composition — so each pushed screen / sheet gets fresh ViewModels from
 * `viewModel { }` (like iOS creating a new @State view model per view),
 * instead of reusing activity-scoped ones from an earlier visit.
 */
@Composable
internal fun ScopedViewModels(content: @Composable () -> Unit) {
    val owner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { content() }
}

/** Full-screen layer drawn over another screen (the Android stand-in for an iOS sheet). */
@Composable
internal fun OverlayLayer(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(NovoColors.background)
            // Swallow taps so they don't reach the screen underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        content()
    }
}

/** 32/36dp dark circle with an icon — back / edit / dashboard buttons. */
@Composable
internal fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    size: Dp = 32.dp,
    iconSize: Dp = 16.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(NovoColors.card)
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = NovoColors.textPrimary, modifier = Modifier.size(iconSize))
    }
}

/** Rounded dark search field with a magnifier and an optional clear button. */
@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    showsClear: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NovoColors.card)
            .padding(horizontal = 14.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = NovoFonts.body(16).copy(color = NovoColors.textSecondary))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = NovoFonts.body(16),
                cursorBrush = SolidColor(NovoColors.accent),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showsClear && value.isNotEmpty()) {
            Icon(
                Icons.Filled.Cancel,
                contentDescription = "Borrar",
                tint = NovoColors.textSecondary,
                modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onValueChange("") },
            )
        }
    }
}

/**
 * Sheet-style top bar: "Cancelar" on the left, centered title, an optional
 * confirmation action on the right (iOS `.cancellationAction` /
 * `.confirmationAction` toolbar items).
 */
@Composable
internal fun SheetTopBar(
    title: String,
    onCancel: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 8.dp),
    ) {
        TextAction("Cancelar", onClick = onCancel)
        Text(
            title,
            style = NovoFonts.semibold(16),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
        // Keeps the title visually centered when there's no trailing action.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.widthIn(min = 88.dp),
        ) { trailing() }
    }
    HorizontalDivider(color = NovoColors.separator)
}

@Composable
internal fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    enabled: Boolean = true,
) {
    Text(
        text,
        style = (if (bold) NovoFonts.semibold(16) else NovoFonts.body(16)).copy(color = NovoColors.accent),
        modifier = modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** iOS `ContentUnavailableView("No se pudo cargar", …)` with an added retry. */
@Composable
internal fun UnavailableState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "No se pudo cargar",
    onRetry: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        modifier = modifier.fillMaxSize().padding(32.dp),
    ) {
        Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = NovoColors.textSecondary, modifier = Modifier.size(44.dp))
        Text(title, style = NovoFonts.title(20), textAlign = TextAlign.Center)
        Text(message, style = NovoFonts.body(14).copy(color = NovoColors.textSecondary), textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Reintentar", onRetry, modifier = Modifier.width(200.dp))
        }
    }
}

@Composable
internal fun CenteredSpinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NovoColors.accent)
    }
}

/** Uppercase gray section title with a bit of tracking (iOS `section(_:)`). */
@Composable
internal fun TrackedLabel(text: String, size: Int = 12, tracking: Double = 0.5, color: Color = NovoColors.textSecondary) {
    Text(text.uppercase(), style = NovoFonts.semibold(size).copy(color = color, letterSpacing = tracking.sp))
}

/** Pill with a tinted background — sale status / days-left badges. */
@Composable
internal fun StatusPill(text: String, color: Color, horizontal: Dp = 12.dp, vertical: Dp = 5.dp) {
    Text(
        text,
        style = NovoFonts.semibold(12).copy(color = color),
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = horizontal, vertical = vertical),
    )
}

/** iOS `.pickerStyle(.segmented)`. */
@Composable
internal fun <T> SegmentedPicker(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = NovoColors.accent,
                    activeContentColor = Color.White,
                    activeBorderColor = NovoColors.accent,
                    inactiveContainerColor = NovoColors.panel,
                    inactiveContentColor = NovoColors.textPrimary,
                    inactiveBorderColor = NovoColors.separator,
                ),
            ) {
                Text(label(option), style = NovoFonts.semibold(13).copy(color = Color.Unspecified), maxLines = 1)
            }
        }
    }
}

/** Pulsing opacity used by the skeleton placeholders. */
@Composable
internal fun Modifier.pulsing(): Modifier {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return this.alpha(alpha)
}

@Composable
internal fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(NovoColors.textSecondary.copy(alpha = 0.25f)),
    )
}

/** Plain `Divider().background(Theme.separator)`. */
@Composable
internal fun RowDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(color = NovoColors.separator, modifier = modifier)
}

/** A stroked capsule outline, used by filter chips. */
internal fun Modifier.capsule(background: Color, border: Color): Modifier =
    this.clip(CircleShape).background(background).border(1.dp, border, CircleShape)

/** Single-line heading text that steps its size down to fit (iOS `minimumScaleFactor`). */
@Composable
internal fun AutoShrinkText(text: String, maxSize: Int, minSize: Int, color: Color = NovoColors.textPrimary) {
    var size by remember(text) { mutableIntStateOf(maxSize) }
    Text(
        text,
        style = NovoFonts.heading(size).copy(color = color),
        maxLines = 1,
        softWrap = false,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && size > minSize) size -= 1
        },
    )
}
