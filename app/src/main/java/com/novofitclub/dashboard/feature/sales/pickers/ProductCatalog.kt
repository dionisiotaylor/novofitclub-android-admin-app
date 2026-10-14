package com.novofitclub.dashboard.feature.sales.pickers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.bool
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.imageUrl
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * One entry from `sale-creator/v1/products`. The catalog mixes memberships
 * (`physical_product: false` with a `membership_time`), physical products
 * (`physical_product: true`, tracked `stock`) and presumably services — the
 * product picker only offers `physical_product == true` items.
 */
data class ProductCatalogItem(
    val id: Int,
    val title: String,
    val price: Double,
    val isPhysicalProduct: Boolean,
    val stock: Int?,
    val imageUrl: String?,
    /** Present only for sellable membership plans (`{time, unit, promo}`). */
    val hasMembershipDuration: Boolean,
) {
    val isMembership: Boolean get() = !isPhysicalProduct && hasMembershipDuration

    companion object {
        fun from(fields: JSONObject): ProductCatalogItem? {
            val id = fields.firstNumber("id") ?: return null
            return ProductCatalogItem(
                id = id.toInt(),
                title = fields.firstString("title", "name") ?: "—",
                price = fields.firstNumber("price") ?: 0.0,
                isPhysicalProduct = fields.bool("physical_product") ?: false,
                stock = fields.firstNumber("stock")?.toInt(),
                imageUrl = fields.imageUrl(),
                hasMembershipDuration = fields.obj("membership_time") != null,
            )
        }
    }
}

/**
 * Loads the full product catalog once (eager-then-filter-locally). Shared by
 * the membership and product pickers of one edit screen; each picker keeps
 * its own search text and passes it in.
 */
class ProductCatalogViewModel(private val api: WPApiClient) : ViewModel() {
    var items by mutableStateOf<List<ProductCatalogItem>>(emptyList())
        private set

    /**
     * Stays true until both the catalog and the popularity ranking have
     * loaded, so the list doesn't flash unsorted and then re-sort.
     */
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    private var isFetching = false

    /**
     * Names from the dashboard's `top_sold_12mo`, most-sold first — only
     * used to reorder, matched by trimmed case-insensitive title.
     */
    private var popularMembershipNames: List<String> = emptyList()
    private var popularProductNames: List<String> = emptyList()

    /** Guarded against overlapping calls (both pickers call it). */
    fun load() {
        if (items.isNotEmpty() || isFetching) return
        isFetching = true
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val catalog = async { loadCatalog() }
            val popularity = async { loadPopularity() }
            catalog.await()
            popularity.await()
            isLoading = false
            isFetching = false
        }
    }

    private suspend fun loadCatalog() {
        val loaded = mutableListOf<ProductCatalogItem>()
        var page = 1
        while (page <= MAX_PAGES) {
            try {
                val response = api.getObject(
                    "sale-creator/v1/products",
                    mapOf("per_page" to PER_PAGE.toString(), "page" to page.toString()),
                )
                val fields = response.arr("items")?.objects() ?: emptyList()
                if (fields.isEmpty()) break
                loaded += fields.mapNotNull { ProductCatalogItem.from(it) }
                if (response.bool("has_more") != true) break
                page += 1
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (page == 1) errorMessage = e.message
                break
            }
        }
        items = loaded
    }

    /** Best-effort: on failure both lists keep catalog order. */
    private suspend fun loadPopularity() {
        val data = try {
            api.getObject("sale-creator/v1/dashboard")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val topSold = data.obj("top_sold_12mo") ?: JSONObject()
        popularMembershipNames = names(topSold.arr("memberships"))
        popularProductNames = names(topSold.arr("products"))
    }

    private fun names(array: JSONArray?): List<String> =
        (array?.objects() ?: emptyList()).mapNotNull { it.firstString("name") }

    /** Only physical products — memberships/services aren't offered here. */
    fun physicalProducts(query: String): List<ProductCatalogItem> =
        sortByPopularity(
            items.filter { it.isPhysicalProduct && (query.isEmpty() || it.title.contains(query, ignoreCase = true)) },
            popularProductNames,
        )

    fun memberships(query: String): List<ProductCatalogItem> =
        sortByPopularity(
            items.filter { it.isMembership && (query.isEmpty() || it.title.contains(query, ignoreCase = true)) },
            popularMembershipNames,
        )

    private fun sortByPopularity(list: List<ProductCatalogItem>, popularNames: List<String>): List<ProductCatalogItem> {
        if (popularNames.isEmpty()) return list
        val ranks = HashMap<String, Int>()
        popularNames.forEachIndexed { index, name -> ranks.putIfAbsent(name.trim().lowercase(), index) }
        // sortedBy is stable, so ties keep catalog order.
        return list.sortedBy { ranks[it.title.trim().lowercase()] ?: Int.MAX_VALUE }
    }

    private companion object {
        const val PER_PAGE = 200
        const val MAX_PAGES = 50
    }
}
