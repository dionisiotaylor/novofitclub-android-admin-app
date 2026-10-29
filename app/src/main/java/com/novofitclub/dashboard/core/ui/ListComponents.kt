package com.novofitclub.dashboard.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.novofitclub.dashboard.R

/**
 * Shared floating-card row used by every list screen: dark rounded surface,
 * white title, gray subtitle, crimson trailing value.
 */
@Composable
fun RecordRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().cardBackground().padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = NovoFonts.semibold(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!trailing.isNullOrEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = NovoFonts.semibold(15).copy(color = NovoColors.accent))
        }
    }
}

/** Small uppercase gray heading above a group of list rows. */
@Composable
fun ListSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = NovoFonts.semibold(13).copy(color = NovoColors.textSecondary),
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/**
 * Product/membership thumbnail for a sale — falls back to a generic bundled
 * placeholder when the item has no image (the common case for memberships).
 */
@Composable
fun SaleThumbnail(url: String?, size: Dp = 44.dp, cornerRadius: Dp = 12.dp) {
    val shape = RoundedCornerShape(cornerRadius)
    val placeholder = @Composable {
        Image(
            painterResource(R.drawable.generic_product_thumb),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape),
        )
    }
    if (url.isNullOrEmpty()) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
            modifier = Modifier.size(size).clip(shape),
        )
    }
}

/** Circle with initials, colored by list position. */
@Composable
fun InitialsAvatar(name: String, index: Int, size: Dp = 44.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(NovoColors.avatarPalette[Math.floorMod(index, NovoColors.avatarPalette.size)]),
    ) {
        Text(initials(name), style = NovoFonts.semibold((size.value * 0.36f).toInt()))
    }
}

/**
 * A member's round avatar: their turnstile access photo ([photoUrl]) when
 * there is one, otherwise the colored initials circle — which also shows
 * while the photo loads or if it fails, so it never shows blank.
 */
@Composable
fun MemberAvatar(initials: String, color: Color, photoUrl: String?, size: Dp, textColor: Color = NovoColors.textPrimary) {
    val placeholder = @Composable {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(size).clip(CircleShape).background(color),
        ) {
            Text(initials, style = NovoFonts.semibold((size.value * 0.34f).toInt()).copy(color = textColor))
        }
    }
    if (photoUrl.isNullOrEmpty()) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            loading = { placeholder() },
            error = { placeholder() },
            modifier = Modifier.size(size).clip(CircleShape),
        )
    }
}

/** Centered spinner for a whole-screen first load. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = NovoColors.accent)
    }
}

/** Centered error message with a retry button. */
@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth().padding(32.dp),
    ) {
        Text(message, style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary), textAlign = TextAlign.Center)
        SecondaryButton("Reintentar", onRetry)
    }
}

/** Centered gray message for an empty list. */
@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = NovoFonts.medium(14).copy(color = NovoColors.textSecondary), textAlign = TextAlign.Center)
    }
}
