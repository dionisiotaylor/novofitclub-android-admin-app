package com.novofitclub.dashboard.feature.sales.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.novofitclub.dashboard.core.ui.cardBackground
import com.novofitclub.dashboard.feature.sales.SkeletonBlock
import com.novofitclub.dashboard.feature.sales.pulsing

/**
 * Pulsing placeholder shown while the dashboard loads — mirrors the real
 * layout (KPI grid, chart cards, breakdown lists) so nothing jumps.
 */
@Composable
internal fun DashboardSkeleton() {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = false)
            .padding(20.dp)
            .pulsing(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KpiTileSkeleton(Modifier.weight(1f))
                    KpiTileSkeleton(Modifier.weight(1f))
                }
            }
        }
        ChartCardSkeleton(180.dp)
        ChartCardSkeleton(180.dp)
        ChartCardSkeleton(160.dp)
        BreakdownCardSkeleton(3)
        BreakdownCardSkeleton(5)
    }
}

@Composable
private fun KpiTileSkeleton(modifier: Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.cardBackground().padding(14.dp)) {
        SkeletonBlock(70.dp, 10.dp)
        SkeletonBlock(90.dp, 20.dp)
        SkeletonBlock(60.dp, 10.dp)
    }
}

@Composable
private fun ChartCardSkeleton(height: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp)) {
        SkeletonBlock(160.dp, 12.dp)
        SkeletonBlock(null, height)
    }
}

@Composable
private fun BreakdownCardSkeleton(rows: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().cardBackground().padding(20.dp)) {
        SkeletonBlock(160.dp, 12.dp)
        repeat(rows) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    SkeletonBlock(100.dp, 12.dp)
                    Spacer(Modifier.weight(1f))
                    SkeletonBlock(50.dp, 12.dp)
                }
                SkeletonBlock(null, 6.dp)
            }
        }
    }
}
