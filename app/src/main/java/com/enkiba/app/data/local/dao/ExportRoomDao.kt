package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.enkiba.app.data.local.entity.ExportItemRow
import com.enkiba.app.data.local.entity.ExportReturnRow
import com.enkiba.app.data.local.entity.ExportSaleRow
import com.enkiba.app.data.local.entity.ExportStockRow


/** Export comptable (admin) : données brutes d'un mois, dans l'ordre chronologique. */
@Dao
interface ExportRoomDao {

    /** Toutes les ventes du mois, annulées comprises (le comptable doit les voir). */
    @Query(
        "SELECT s.id, s.sale_date, s.payment_method, s.total_price, s.has_negotiation, s.deleted_at, " +
            "TRIM(COALESCE(u.username, '') || ' ' || COALESCE(u.lastname, '')) AS seller_name, " +
            "TRIM(COALESCE(c.firstname, '') || ' ' || COALESCE(c.lastname, '')) AS client_name, " +
            "COALESCE((SELECT SUM(r.refund_amount) FROM sale_returns r WHERE r.sale_id = s.id), 0) AS refunded " +
            "FROM sales s LEFT JOIN users u ON u.id = s.seller_id LEFT JOIN clients c ON c.id = s.client_id " +
            "WHERE s.sale_date >= :start AND s.sale_date < :end ORDER BY s.sale_date"
    )
    fun sales(start: String, end: String): List<ExportSaleRow>

    /** Lignes des ventes non annulées du mois, avec retours et prix d'achat figé. */
    @Query(
        "SELECT si.sale_id, s.sale_date, si.product_name, si.quantity, si.list_price, si.unit_price, si.total_price, " +
            "COALESCE((SELECT SUM(r.quantity) FROM sale_returns r WHERE r.sale_item_id = si.id), 0) AS returned, " +
            "COALESCE((SELECT SUM(r.refund_amount) FROM sale_returns r WHERE r.sale_item_id = si.id), 0) AS refunded, " +
            "c.cost_price FROM sale_items si JOIN sales s ON s.id = si.sale_id " +
            "LEFT JOIN sale_item_costs c ON c.sale_item_id = si.id " +
            "WHERE s.deleted_at IS NULL AND s.sale_date >= :start AND s.sale_date < :end ORDER BY s.sale_date, si.rowid"
    )
    fun items(start: String, end: String): List<ExportItemRow>

    /** Retours faits pendant le mois (quelle que soit la date de la vente). */
    @Query(
        "SELECT r.created_at, r.sale_id, si.product_name, r.quantity, r.refund_amount, " +
            "TRIM(COALESCE(u.username, '') || ' ' || COALESCE(u.lastname, '')) AS by_name " +
            "FROM sale_returns r LEFT JOIN sale_items si ON si.id = r.sale_item_id LEFT JOIN users u ON u.id = r.created_by " +
            "WHERE r.created_at >= :start AND r.created_at < :end ORDER BY r.created_at"
    )
    fun returns(start: String, end: String): List<ExportReturnRow>

    /** État du stock au moment de l'export. */
    @Query(
        "SELECT p.name, p.category, p.quantity, p.price, c.cost_price FROM products p " +
            "LEFT JOIN product_costs c ON c.product_id = p.id WHERE p.deleted_at IS NULL ORDER BY p.category, p.name"
    )
    fun stock(): List<ExportStockRow>
}
