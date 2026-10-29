package com.novofitclub.dashboard.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.novofitclub.dashboard.R

/** Rounded card surface used throughout the design. */
fun Modifier.cardBackground(): Modifier =
    this.clip(RoundedCornerShape(NovoDimens.cornerRadius)).background(NovoColors.card)

/**
 * A card tinted with one colour and a left stripe of it: purple for
 * "Diferencia / Ajuste" content (like the purple rows of the wp-admin Sales
 * list), blue for a sale paid by canje.
 */
fun Modifier.tintedCard(tint: Color): Modifier =
    this.clip(RoundedCornerShape(NovoDimens.cornerRadius))
        .background(NovoColors.card)
        .background(tint.copy(alpha = 0.12f))
        .drawBehind { drawRect(tint, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }

/** Filled crimson pill button — the primary CTA style throughout the design. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = NovoColors.accent,
            contentColor = Color.White,
            disabledContainerColor = NovoColors.accent.copy(alpha = 0.4f),
            disabledContentColor = Color.White,
        ),
        modifier = modifier.fillMaxWidth().height(NovoDimens.controlHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, style = NovoFonts.semibold(16).copy(color = Color.White))
        }
    }
}

/** Outlined pill button — used for secondary/back actions. */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, NovoColors.accent),
        modifier = modifier.fillMaxWidth().height(NovoDimens.controlHeight),
    ) {
        Text(text, style = NovoFonts.semibold(16).copy(color = NovoColors.accent))
    }
}

/** Dark rounded text field used on Login and on every editable form. */
@Composable
fun ThemedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    isSecure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = NovoFonts.body(16).copy(color = NovoColors.textSecondary)) },
        textStyle = NovoFonts.body(16),
        singleLine = singleLine,
        visualTransformation = if (isSecure) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isSecure) KeyboardType.Password else keyboardType,
            autoCorrectEnabled = false,
        ),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = NovoColors.panel,
            unfocusedContainerColor = NovoColors.panel,
            disabledContainerColor = NovoColors.panel,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = NovoColors.accent,
        ),
        modifier = modifier.fillMaxWidth().height(NovoDimens.controlHeight + 4.dp),
    )
}

/** Small uppercase gray label above a group of fields. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = NovoFonts.semibold(12).copy(color = NovoColors.textSecondary),
        modifier = modifier,
    )
}

/** The official NOVO FIT CLUB hexagon badge (152×175 native aspect ratio). */
@Composable
fun BrandBadge(width: Dp = 120.dp) {
    Image(
        painter = painterResource(R.drawable.logo_novofit),
        contentDescription = "Novo Fit Club",
        contentScale = ContentScale.Fit,
        modifier = Modifier.width(width).height(width * (175f / 152f)),
    )
}

/**
 * Big bold title + gray subtitle used at the top of each tab; with [onBack]
 * set, adds a small back-circle + breadcrumb row for pushed screens.
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    breadcrumb: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    ) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = NovoColors.card),
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atrás",
                        tint = NovoColors.textPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (breadcrumb != null) {
                    Text(
                        breadcrumb,
                        style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary, letterSpacing = 0.4.sp),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = NovoFonts.heading(if (onBack == null) 32 else 28))
            if (subtitle != null) {
                Text(subtitle, style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary))
            }
        }
    }
}
