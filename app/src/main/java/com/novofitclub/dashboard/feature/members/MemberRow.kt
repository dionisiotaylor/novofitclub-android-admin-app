package com.novofitclub.dashboard.feature.members

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.ui.MemberAvatar
import com.novofitclub.dashboard.core.ui.NovoColors
import com.novofitclub.dashboard.core.ui.NovoFonts
import com.novofitclub.dashboard.core.ui.cardBackground

/** Card row for the Miembros list: avatar + status dot, name/phone, colored status label. */
@Composable
fun MemberRow(member: Member, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().cardBackground().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            MemberAvatar(member.initials, member.avatarColor, member.photoUrl, 44.dp)
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(member.status.color)
                    .border(2.dp, NovoColors.card, CircleShape),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(member.name, style = NovoFonts.semibold(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = member.phone.orEmpty()
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = NovoFonts.body(13).copy(color = NovoColors.textSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            member.statusLabel,
            style = NovoFonts.semibold(12).copy(color = member.status.color),
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 92.dp),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = NovoColors.textSecondary.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Pulsing placeholder shaped like [MemberRow], shown while the list loads. */
@Composable
fun MemberRowSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val fill = NovoColors.textSecondary.copy(alpha = 0.25f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha)
            .cardBackground()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(fill))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(150.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(fill))
            Box(Modifier.width(90.dp).height(11.dp).clip(RoundedCornerShape(4.dp)).background(fill))
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.width(56.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(fill))
    }
}
