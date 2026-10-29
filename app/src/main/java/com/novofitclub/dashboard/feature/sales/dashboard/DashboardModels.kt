package com.novofitclub.dashboard.feature.sales.dashboard

import com.novofitclub.dashboard.core.net.arr
import com.novofitclub.dashboard.core.net.firstNumber
import com.novofitclub.dashboard.core.net.firstString
import com.novofitclub.dashboard.core.net.obj
import com.novofitclub.dashboard.core.net.objects
import com.novofitclub.dashboard.core.ui.splitEmbeddedId
import com.novofitclub.dashboard.core.util.Formatters
import org.json.JSONArray
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Parses `GET sale-creator/v1/dashboard` — the same aggregate stats shown in
 * wp-admin's Sales → Dashboard screen, computed server-side by the same PHP
 * functions that render that page.
 */
data class DashboardStats(
    val month: Int,
    val year: Int,
    val clients: ClientCounts,
    val revenue: Revenue,
    val expiringIn7Days: Int,
    val monthlyRevenue12mo: List<MonthPoint>,
    val salesVolume12mo: List<MonthPoint>,
    val newMembersTrend6mo: List<MonthPoint>,
    val revenueByPaymentMethod: PaymentMethodBreakdown,
    val clientsPerPlan: List<PlanBreakdown>,
    val revenueSplit: RevenueSplit,
    val topSoldMemberships: List<TopSoldItem>,
    val topSoldProducts: List<TopSoldItem>,
) {
    data class ClientCounts(val total: Int, val active: Int, val activePercent: Int, val inactive: Int)

    data class Revenue(
        val today: Double,
        val todaySalesCount: Int,
        val monthTotal: Double,
        val monthVsLastMonthPercent: Double,
    )

    /** One point in a monthly series ("2025-10" + a value), plotted on a category axis. */
    data class MonthPoint(val month: String, val value: Double) {
        val shortLabel: String
            get() {
                val parts = month.split("-")
                val monthNumber = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size != 2 || monthNumber == null || monthNumber !in 1..12) return month
                return "${MONTH_NAMES[monthNumber - 1]} ${parts[0].takeLast(2)}"
            }
    }

    data class PaymentMethodBreakdown(val cash: Double, val card: Double, val sinpe: Double)

    data class PlanBreakdown(val plan: String, val value: String, val total: Int, val active: Int)

    /** [ajustes]: the month's "Diferencia / Ajuste" lines (themes/.../app/sales/ajustes.php). */
    data class RevenueSplit(val memberships: Double, val products: Double, val services: Double, val ajustes: Double = 0.0)

    data class TopSoldItem(val name: String, val count: Int)

    companion object {
        private val MONTH_NAMES = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")

        private fun JSONObject.int(key: String): Int = (firstNumber(key) ?: 0.0).toInt()
        private fun JSONObject.dbl(key: String): Double = firstNumber(key) ?: 0.0

        fun from(fields: JSONObject): DashboardStats? {
            val period = fields.obj("period") ?: return null
            val clients = fields.obj("clients") ?: return null
            val revenue = fields.obj("revenue") ?: return null
            val payment = fields.obj("revenue_by_payment_method") ?: JSONObject()
            val split = fields.obj("revenue_split") ?: JSONObject()
            val topSold = fields.obj("top_sold_12mo") ?: JSONObject()

            return DashboardStats(
                month = period.int("month"),
                year = period.int("year"),
                clients = ClientCounts(
                    total = clients.int("total"),
                    active = clients.int("active"),
                    activePercent = clients.int("active_percent"),
                    inactive = clients.int("inactive"),
                ),
                revenue = Revenue(
                    today = revenue.dbl("today"),
                    todaySalesCount = revenue.int("today_sales_count"),
                    monthTotal = revenue.dbl("month_total"),
                    monthVsLastMonthPercent = revenue.dbl("month_vs_last_month_percent"),
                ),
                expiringIn7Days = fields.int("expiring_in_7_days"),
                monthlyRevenue12mo = monthPoints(fields.arr("monthly_revenue_12mo"), "amount"),
                salesVolume12mo = monthPoints(fields.arr("sales_volume_12mo"), "count"),
                newMembersTrend6mo = monthPoints(fields.arr("new_members_trend_6mo"), "count"),
                revenueByPaymentMethod = PaymentMethodBreakdown(
                    cash = payment.dbl("cash"),
                    card = payment.dbl("card"),
                    sinpe = payment.dbl("sinpe"),
                ),
                clientsPerPlan = (fields.arr("clients_per_plan")?.objects() ?: emptyList()).mapNotNull { obj ->
                    val plan = obj.firstString("plan") ?: return@mapNotNull null
                    val value = obj.firstString("value") ?: return@mapNotNull null
                    PlanBreakdown(plan, value, obj.int("total"), obj.int("active"))
                },
                revenueSplit = RevenueSplit(
                    memberships = split.dbl("memberships"),
                    products = split.dbl("products"),
                    services = split.dbl("services"),
                    ajustes = split.dbl("ajustes"),
                ),
                topSoldMemberships = topSoldItems(topSold.arr("memberships")),
                topSoldProducts = topSoldItems(topSold.arr("products")),
            )
        }

        private fun monthPoints(array: JSONArray?, valueKey: String): List<MonthPoint> =
            (array?.objects() ?: emptyList()).mapNotNull { obj ->
                val month = obj.firstString("month") ?: return@mapNotNull null
                MonthPoint(month, obj.firstNumber(valueKey) ?: 0.0)
            }

        private fun topSoldItems(array: JSONArray?): List<TopSoldItem> =
            (array?.objects() ?: emptyList()).mapNotNull { obj ->
                val name = obj.firstString("name") ?: return@mapNotNull null
                TopSoldItem(name, obj.int("count"))
            }
    }
}

/**
 * One row from `GET sale-creator/v1/dashboard/expiring` — a member whose
 * membership expires soon. `name` arrives as "Real Name – member_id".
 */
data class ExpiringMember(
    val id: Int,
    val name: String,
    val plan: String,
    val expiresAt: Date?,
    val daysLeft: Int,
) {
    companion object {
        fun from(fields: JSONObject): ExpiringMember? {
            val memberId = fields.firstNumber("member_id") ?: return null
            val rawName = fields.firstString("name") ?: "—"
            val rawExpiration = fields.firstString("expires_at")
            return ExpiringMember(
                id = memberId.toInt(),
                name = splitEmbeddedId(rawName).first ?: rawName,
                plan = fields.firstString("plan") ?: "—",
                // Most rows are "yyyy-MM-dd", but a handful come as a bare "yyyyMMdd".
                expiresAt = Formatters.parseDate(rawExpiration) ?: rawExpiration?.let(::parseCompact),
                daysLeft = (fields.firstNumber("days_left") ?: 0.0).toInt(),
            )
        }

        private fun parseCompact(raw: String): Date? = try {
            SimpleDateFormat("yyyyMMdd", Locale.US).apply { isLenient = false }.parse(raw)
        } catch (_: ParseException) {
            null
        }
    }
}
