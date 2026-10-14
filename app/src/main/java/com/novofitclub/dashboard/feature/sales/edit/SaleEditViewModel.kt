package com.novofitclub.dashboard.feature.sales.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novofitclub.dashboard.core.net.WPApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Edits a sale against `PUT sale-creator/v1/sales/{id}`, or creates one
 * against `POST sale-creator/v1/sales` when [saleId] is null.
 */
class SaleEditViewModel(private val api: WPApiClient, val saleId: String?) : ViewModel() {
    /** Sent with a create so a double-tap / retry can't create the sale twice. */
    private val submissionToken = UUID.randomUUID().toString()

    var payload by mutableStateOf(if (saleId == null) SaleEditPayload() else null)
    var isLoading by mutableStateOf(saleId != null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var isDeleting by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var saveError by mutableStateOf<String?>(null)
        private set
    var deleteError by mutableStateOf<String?>(null)
        private set

    val isCreating: Boolean get() = saleId == null

    init {
        load()
    }

    fun load() {
        val id = saleId ?: return
        isLoading = true
        loadError = null
        viewModelScope.launch {
            try {
                val fields = api.getObject("sale-creator/v1/sales/$id")
                payload = SaleEditPayload.fromFields(fields)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = e.message ?: "No se pudo leer la venta."
            }
            isLoading = false
        }
    }

    inline fun update(transform: (SaleEditPayload) -> SaleEditPayload) {
        payload = payload?.let(transform)
    }

    /** Calls [onSuccess] when saved; on failure leaves the form open showing [saveError]. */
    fun save(onSuccess: () -> Unit) {
        val current = payload ?: return
        if (isSaving) return
        isSaving = true
        saveError = null
        viewModelScope.launch {
            try {
                if (saleId != null) {
                    api.putObject("sale-creator/v1/sales/$saleId", current.patchBody())
                } else {
                    val body = current.patchBody().put("submission_token", submissionToken)
                    api.postObject("sale-creator/v1/sales", body)
                }
                isSaving = false
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                saveError = e.message
                isSaving = false
            }
        }
    }

    /** Only relevant in edit mode. Without `force` WordPress moves the post to Trash. */
    fun delete(onSuccess: () -> Unit) {
        val id = saleId ?: return
        isDeleting = true
        deleteError = null
        viewModelScope.launch {
            try {
                api.deleteObject("wp/v2/sale/$id")
                isDeleting = false
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deleteError = e.message
                isDeleting = false
            }
        }
    }
}
