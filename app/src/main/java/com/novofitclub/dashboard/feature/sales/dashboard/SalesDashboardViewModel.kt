package com.novofitclub.dashboard.feature.sales.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.objects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar

class SalesDashboardViewModel(
    private val api: WPApiClient,
    month: Int? = null,
    year: Int? = null,
) : ViewModel() {
    val month: Int
    val year: Int

    var stats by mutableStateOf<DashboardStats?>(null)
        private set
    var expiringMembers by mutableStateOf<List<ExpiringMember>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var isLoadingMoreExpiring by mutableStateOf(false)
        private set

    private var expiringPage by mutableIntStateOf(1)
    private var expiringTotalPages by mutableIntStateOf(1)

    val canLoadMoreExpiring: Boolean get() = expiringPage < expiringTotalPages

    init {
        val now = Calendar.getInstance()
        this.month = month ?: (now.get(Calendar.MONTH) + 1)
        this.year = year ?: now.get(Calendar.YEAR)
        load()
    }

    fun load() {
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val statsJob = launch { loadStats() }
            val expiringJob = launch { loadExpiring(1) }
            statsJob.join()
            expiringJob.join()
            isLoading = false
        }
    }

    fun loadMoreExpiring() {
        if (!canLoadMoreExpiring || isLoadingMoreExpiring) return
        isLoadingMoreExpiring = true
        viewModelScope.launch {
            loadExpiring(expiringPage + 1)
            isLoadingMoreExpiring = false
        }
    }

    private suspend fun loadStats() {
        try {
            val fields = api.getObject("sale-creator/v1/dashboard", mapOf("month" to "$month", "year" to "$year"))
            stats = DashboardStats.from(fields)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = e.message
        }
    }

    private suspend fun loadExpiring(page: Int) {
        try {
            val fields = api.getObject("sale-creator/v1/dashboard/expiring", mapOf("page" to "$page", "per_page" to "10"))
            val items = (fields.arr("items")?.objects() ?: emptyList()).mapNotNull { ExpiringMember.from(it) }
            expiringMembers = if (page == 1) items else expiringMembers + items
            expiringPage = (fields.firstNumber("page") ?: page.toDouble()).toInt()
            expiringTotalPages = (fields.firstNumber("total_pages") ?: 1.0).toInt()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (stats == null) errorMessage = e.message
        }
    }
}
