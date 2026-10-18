package com.enkiba.app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.enkiba.app.data.local.ShopDetails;
import com.enkiba.app.data.local.ShopStore;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.Quote;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.UserDao;
import com.enkiba.app.pdf.EnkibaPdf;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Point d'entrée des PDF pour le reste de l'app : génère (pdf/EnkibaPdf) puis ouvre ou non.
 * Les fichiers sont rangés dans Documents/Enkiba/{Factures, Devis, Rapports, Etiquettes}.
 * À appeler hors du thread principal ; `ctx` doit être une Activity pour l'ouverture.
 */
public class PDFUtils {

    private static final String TAG = "PDFUtils";
    private static final String FILE_PROVIDER_AUTHORITY = "com.enkiba.app.fileprovider";
    private static final double KMF_TO_EUR = 0.00203;

    private PDFUtils() {}

    // --- Factures et devis ---------------------------------------------------------------

    /** Génère la facture et l'ouvre dans le lecteur PDF. */
    public static File generateInvoicePdf(Context ctx, SaleHeader header, List<SaleItemRow> items, Client client,
                                          String shopName, String shopAddress, String shopContact, String shopEmail) {
        return open(ctx, buildInvoicePdf(ctx, header, items, client, shopName, shopAddress, shopContact, shopEmail));
    }

    /** Génère la facture sans l'ouvrir (partage). @return le fichier, ou null en cas d'échec */
    public static File buildInvoicePdf(Context ctx, SaleHeader header, List<SaleItemRow> items, Client client,
                                       String shopName, String shopAddress, String shopContact, String shopEmail) {
        if (header == null || items == null || items.isEmpty()) return null;
        ShopDetails shop = new ShopDetails(shopName, shopAddress, shopContact, shopEmail);
        return EnkibaPdf.INSTANCE.invoice(ctx, header, items, client, shop, sellerName(ctx, header.getSellerId()));
    }

    /** Génère le devis et l'ouvre dans le lecteur PDF. */
    public static File generateQuotePdf(Context ctx, Quote quote, List<SaleItemRow> items, Client client,
                                        String shopName, String shopAddress, String shopContact, String shopEmail) {
        return open(ctx, buildQuotePdf(ctx, quote, items, client, shopName, shopAddress, shopContact, shopEmail));
    }

    /** Génère le devis sans l'ouvrir (partage). @return le fichier, ou null en cas d'échec */
    public static File buildQuotePdf(Context ctx, Quote quote, List<SaleItemRow> items, Client client,
                                     String shopName, String shopAddress, String shopContact, String shopEmail) {
        if (quote == null || items == null || items.isEmpty()) return null;
        ShopDetails shop = new ShopDetails(shopName, shopAddress, shopContact, shopEmail);
        return EnkibaPdf.INSTANCE.quote(ctx, quote, items, client, shop, sellerName(ctx, quote.getSellerId()));
    }

    // --- Rapports et étiquettes (boutique de l'utilisateur connecté) ---------------------

    /** Historique des ventes affichées, ouvert ensuite. @return false si rien n'a été généré */
    public static boolean generateSalesReportPdf(Context ctx, List<SaleHeader> sales) {
        if (sales == null || sales.isEmpty()) return false;
        return open(ctx, EnkibaPdf.INSTANCE.salesReport(ctx, sales, ShopStore.current(ctx))) != null;
    }

    /** Historique des opérations sur les articles, ouvert ensuite. */
    public static boolean generateProductHistoryPdf(Context ctx, List<ProductHistory> ops) {
        if (ops == null || ops.isEmpty()) return false;
        return open(ctx, EnkibaPdf.INSTANCE.operationsReport(ctx, ops, ShopStore.current(ctx))) != null;
    }

    /** Planche d'étiquettes QR des articles affichés, ouverte ensuite. */
    public static boolean generateQrListPdf(Context ctx, List<Product> products) {
        if (products == null || products.isEmpty()) return false;
        return open(ctx, EnkibaPdf.INSTANCE.qrSheet(ctx, products, ShopStore.current(ctx))) != null;
    }

    // --- Outils --------------------------------------------------------------------------

    /** Pied de page : nom et coordonnées renseignées, séparés par « • ». */
    static String shopFooter(String name, String address, String phone, String email) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{name, address, phone, email}) {
            if (part == null || part.trim().isEmpty()) continue;
            if (sb.length() > 0) sb.append(" • ");
            sb.append(part.trim());
        }
        return sb.toString();
    }

    private static String sellerName(Context ctx, String sellerId) {
        if (sellerId == null) return null;
        User u = new UserDao(ctx).getUserById(sellerId);
        if (u == null) return null;
        String name = ((u.getUsername() == null ? "" : u.getUsername()) + " "
                + (u.getLastname() == null ? "" : u.getLastname())).trim();
        return name.isEmpty() ? null : name;
    }

    private static File open(Context ctx, File file) {
        if (file != null) openPdf(ctx, file);
        return file;
    }

    private static void openPdf(Context ctx, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(ctx, FILE_PROVIDER_AUTHORITY, file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            ctx.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Impossible d'ouvrir le PDF : " + e.getMessage(), e);
        }
    }

    /** Conversion indicative (taux fixe). */
    public static double kmfToEur(double kmf) {
        return kmf * KMF_TO_EUR;
    }

    /** « 1 234,56 » (deux décimales, format français). */
    public static String formatEur(double eur) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.00", symbols).format(eur);
    }
}
