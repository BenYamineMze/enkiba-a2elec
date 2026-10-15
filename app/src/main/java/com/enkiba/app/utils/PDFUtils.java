package com.enkiba.app.utils;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.enkiba.app.R;
import com.enkiba.app.database.DatabaseHelper;
import com.enkiba.app.models.Client;
import com.enkiba.app.models.Product;
import com.enkiba.app.models.ProductHistory;
import com.enkiba.app.models.SaleHeader;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.models.User;
import com.enkiba.app.modelsDao.UserDao; // 🚨 NOUVEAU 🚨

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PDFUtils {

    private static final String TAG = "PDFUtils";

    private static final String FILE_PROVIDER_AUTHORITY = "com.enkiba.app.fileprovider";

    private static final double KMF_TO_EUR = 0.00203;


    /**
     * Génère un PDF professionnel pour l'historique des ventes.
     * Retourne le File créé (ou null en cas d'erreur).
     */
    public static boolean generateSalesReportPdf(Context ctx, List<SaleHeader> sales, String shopTitle) {
        try {
            if (sales == null) sales = java.util.Collections.emptyList();

            int pageWidth = 595;
            int pageHeight = 842;

            PdfDocument pdf = new PdfDocument();

            Paint paint = new Paint();
            Paint paintBold = new Paint();
            paintBold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int x = 20;
            int y = 20;

            Resources res = ctx.getResources();

            // --- HEADER ---
            Paint headerBg = new Paint();
            headerBg.setColor(0xFF1C7D41);
            RectF headerRect = new RectF(0, 0, pageWidth, 120);
            canvas.drawRect(headerRect, headerBg);

            // Logo
            try {
                Bitmap logo = BitmapFactory.decodeResource(res, R.drawable.ic_logo);
                if (logo != null) {
                    Bitmap scaled = Bitmap.createScaledBitmap(logo, 64, 64, true);
                    canvas.drawBitmap(scaled, x, y + 6, null);
                }
            } catch (Exception ignored) {}

            paintBold.setColor(0xFFFFFFFF);
            paintBold.setTextSize(18);
            canvas.drawText(shopTitle, x + 90, y + 30, paintBold);

            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(10);
            float dateWidth = paint.measureText(dateStr);
            canvas.drawText(dateStr, pageWidth - x - dateWidth, y + 20, paint);

            // --- TABLE HEADER ---
            int tableY = y + 130;
            int rowHeight = 22;

            int colVente = x;
            int colDate = colVente + 80;
            int colPayment = colDate + 140;
            int colTotal = colPayment + 120;
            int colSeller = colTotal + 100;

            paintBold.setColor(0xFF1C7D41);
            paintBold.setTextSize(12);
            canvas.drawText("Vente", colVente, tableY, paintBold);
            canvas.drawText("Date", colDate, tableY, paintBold);
            canvas.drawText("Paiement", colPayment, tableY, paintBold);
            canvas.drawText("Total (KMF)", colTotal, tableY, paintBold);
            canvas.drawText("Vendeur", colSeller, tableY, paintBold);

            paint.setColor(0xFF000000);
            paint.setTextSize(10);

            int currentY = tableY + 25;
            DecimalFormat df = new DecimalFormat("#,###.##");

            double grandTotal = 0.0;

            for (SaleHeader s : sales) {

                if (currentY + 40 > pageHeight - 60) {
                    pdf.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdf.getPages().size() + 1).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    currentY = 30;
                }

                canvas.drawText("N° " + s.getId(), colVente, currentY, paint);
                canvas.drawText(CurrencyUtils.formatSaleDate(s.getSaleDate()), colDate, currentY, paint);
                //canvas.drawText(DatabaseUtils.formatIsoToDisplay(s.getSaleDate()), colDate, currentY, paint);
                canvas.drawText(s.getPaymentMethod(), colPayment, currentY, paint);
                canvas.drawText(df.format(s.getTotalPrice()), colTotal, currentY, paint);

                // Le nom d'utilisateur est censé être déjà chargé dans SaleHeader.username
                canvas.drawText(s.getUsername(), colSeller, currentY, paint);

                grandTotal += s.getTotalPrice();
                currentY += rowHeight;
            }

            // --- FOOTER ---
            currentY += 10;
            paintBold.setTextSize(13);
            canvas.drawText("Total des ventes : " + df.format(grandTotal) + " KMF",
                    colTotal - 80, currentY, paintBold);
            double totalEur = kmfToEur(grandTotal);
            paint.setTextSize(11);
            canvas.drawText("≈ " + formatEur(totalEur) + " €",
                    colTotal - 80, currentY + 18, paint);

            pdf.finishPage(page);

            // SAVING
            File folder = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "GStock");
            if (!folder.exists()) folder.mkdirs();

            String fileName = "Historique_ventes_" +
                    new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date()) +
                    ".pdf";

            File file = new File(folder, fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                pdf.writeTo(fos);
                pdf.close();

                openPdf(ctx, file);
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }



    public static boolean generateProductHistoryPdf(Context ctx, List<ProductHistory> list, String title) {
        try {
            int pageWidth = 595;
            int pageHeight = 842;

            PdfDocument pdf = new PdfDocument();
            Paint paint = new Paint();
            Paint paintBold = new Paint();
            paintBold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int x = 20;
            int y = 20;

            Resources res = ctx.getResources();

            // --- HEADER ---
            Paint headerBg = new Paint();
            headerBg.setColor(0xFF1C7D41);
            RectF headerRect = new RectF(0, 0, pageWidth, 90);
            canvas.drawRect(headerRect, headerBg);

            // Logo
            try {
                Bitmap logo = BitmapFactory.decodeResource(res, R.drawable.ic_logo);
                if (logo != null) {
                    Bitmap scaled = Bitmap.createScaledBitmap(logo, 64, 64, true);
                    canvas.drawBitmap(scaled, x, y + 6, null);
                }
            } catch (Exception ignored) {
            }

            paintBold.setColor(0xFFFFFFFF);
            paintBold.setTextSize(18);
            canvas.drawText(title, x + 90, y + 30, paintBold);

            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date());
            paint.setColor(0xFFFFFFFF);
            paint.setTextSize(10);
            float dateWidth = paint.measureText(dateStr);
            canvas.drawText(dateStr, pageWidth - x - dateWidth, y + 20, paint);

            paint.setColor(0xFFDDDDDD);
            canvas.drawLine(x, y + 90, pageWidth - x, y + 90, paint);

            // --- TABLE HEADER ---
            int tableY = y + 110;
            int rowHeight = 20;

            int colProduct = x;
            int colOp = colProduct + 120;
            int colDate = colOp + 90;
            int colUser = colDate + 100;
            int colOld = colUser + 90;
            int colNew = colOld + 70;

            Paint headerCellBg = new Paint();
            headerCellBg.setColor(0xFFE8F3EA);
            canvas.drawRect(x, tableY - 8, pageWidth - x, tableY + rowHeight - 8, headerCellBg);

            paintBold.setColor(0xFF1C7D41);
            paintBold.setTextSize(11);

            canvas.drawText("Produit", colProduct, tableY + 4, paintBold);
            canvas.drawText("Opération", colOp, tableY + 4, paintBold);
            canvas.drawText("Date", colDate, tableY + 4, paintBold);
            canvas.drawText("Utilisateur", colUser, tableY + 4, paintBold);
            canvas.drawText("Ancien", colOld, tableY + 4, paintBold);
            canvas.drawText("Nouveau", colNew, tableY + 4, paintBold);

            // --- ROWS ---
            paint.setColor(0xFF000000);
            paint.setTextSize(10);

            int currentY = tableY + rowHeight + 2;

            for (ProductHistory p : list) {

                // Saut de page
                if (currentY + 40 > pageHeight - 60) {
                    pdf.finishPage(page);
                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdf.getPages().size() + 1).create();
                    page = pdf.startPage(pageInfo);
                    canvas = page.getCanvas();
                    currentY = 30;
                }

                canvas.drawText(p.getProductName(), colProduct, currentY, paint);
                canvas.drawText(p.getOperation(), colOp, currentY, paint);
                canvas.drawText(CurrencyUtils.formatSaleDate(p.getTimestamp()), colDate, currentY, paint);
                canvas.drawText(p.getUsername(), colUser, currentY, paint);
                canvas.drawText(String.valueOf(p.getOldValue()), colOld, currentY, paint);
                canvas.drawText(String.valueOf(p.getNewValue()), colNew, currentY, paint);

                currentY += rowHeight;
            }

            // FOOTER
            paint.setColor(0xFF777777);
            paint.setTextSize(9);
            float fw = paint.measureText(dateStr);
            canvas.drawText(dateStr, (pageWidth - fw) / 2, pageHeight - 30, paint);

            pdf.finishPage(page);

            File folder = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "GStock");
            if (!folder.exists()) folder.mkdirs();

            String fileName = "historique_produits_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(new Date()) + ".pdf";
            File file = new File(folder, fileName);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                pdf.writeTo(fos);
                pdf.close();

                openPdf(ctx, file);
                return true; // SUCCÈS
            } catch (Exception e) {
                e.printStackTrace();
                pdf.close();
                return false; // ÉCHEC
            }
        }catch (Exception e) {
            e.printStackTrace();
            return false; // ÉCHEC global
        }
    }

    public static File generateInvoicePdf(Context ctx,
                                          SaleHeader header,
                                          List<SaleItemRow> items,
                                          Client client,
                                          String shopName,
                                          String shopAddress,
                                          String shopContact,
                                          String shopEmail) {

        try {
            if (header == null || items == null || items.isEmpty())
                return null;

            int pageWidth = 595, pageHeight = 842, margin = 20;

            PdfDocument pdf = new PdfDocument();
            DecimalFormat df = new DecimalFormat("#,##0.00");
            Paint paint = new Paint();
            Paint bold = new Paint();
            bold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int x = margin, y = margin + 18;

            // --------------------------------------------------------------------
            // HEADER (fond + logo + infos shop)
            // --------------------------------------------------------------------
            Paint headerBg = new Paint();
            headerBg.setColor(0xFF1C7D41);
            canvas.drawRect(new RectF(0, 0, pageWidth, 90), headerBg);

            try {
                Bitmap logo = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_logo);
                Bitmap scaled = Bitmap.createScaledBitmap(logo, 70, 70, true);
                canvas.drawBitmap(scaled, x, 10, null);
            } catch (Exception ignored) {}

            bold.setColor(Color.WHITE);
            bold.setTextSize(20);
            canvas.drawText(shopName, x + 90, 35, bold);

            paint.setColor(Color.WHITE);
            paint.setTextSize(10);
            canvas.drawText(shopAddress, pageWidth - margin - 170, 25, paint);

            canvas.drawText("Tél : " + shopContact, pageWidth - margin - 170, 40, paint);

            if (shopEmail != null && !shopEmail.isEmpty()) {
                canvas.drawText("Email : " + shopEmail, pageWidth - margin - 170, 55, paint);
            }


            // --------------------------------------------------------------------
            // TITRE FACTURE + DATE
            // --------------------------------------------------------------------
            bold.setColor(Color.BLACK);
            bold.setTextSize(16);
            canvas.drawText("FACTURE #" + header.getId(), x, 130, bold);

            paint.setTextSize(12);
            paint.setColor(0xFF000000);
            canvas.drawText("Date : " + header.getSaleDate(), x, 150, paint);

            // --------------------------------------------------------------------
            // CLIENT
            // --------------------------------------------------------------------
            int clientY = 200;
            bold.setTextSize(14);
            canvas.drawText("Client :", x, clientY, bold);

            paint.setTextSize(12);
            paint.setColor(0xFF000000);
            if (client != null) {
                canvas.drawText(client.getFirstname() + " " + client.getLastname(), x, clientY + 20, paint);

                if (client.getAddress() != null && !client.getAddress().isEmpty())
                    canvas.drawText(client.getAddress(), x, clientY + 40, paint);

                if (client.getPhone() != null && !client.getPhone().isEmpty())
                    canvas.drawText("Tél : " + client.getPhone(), x, clientY + 60, paint);

                if (client.getEmail() != null && !client.getEmail().isEmpty())
                    canvas.drawText("Email : " + client.getEmail(), x, clientY + 80, paint);
            }

            // --------------------------------------------------------------------
            // TABLE HEADER & CONTENT
            // --------------------------------------------------------------------
            int tableY = clientY + 120;
            int rowH = 22;

            int colProduct = x;
            int colQty = colProduct + 230;
            int colUnit = colQty + 60;
            int colTotal = colUnit + 80;

            bold.setColor(0xFF1C7D41);
            bold.setTextSize(12);
            canvas.drawText("Article", colProduct, tableY, bold);
            canvas.drawText("Qté", colQty, tableY, bold);
            canvas.drawText("Prix/U", colUnit, tableY, bold);
            canvas.drawText("Total", colTotal, tableY, bold);

            Paint border = new Paint();
            border.setColor(Color.GRAY);
            border.setStrokeWidth(1);
            canvas.drawLine(x, tableY + 5, pageWidth - margin, tableY + 5, border);

            int cy = tableY + rowH;

            paint.setColor(Color.BLACK);
            paint.setTextSize(11);

            for (SaleItemRow r : items) {
                canvas.drawText(r.getProductName(), colProduct, cy, paint);
                canvas.drawText(String.valueOf(r.getQuantity()), colQty, cy, paint);
                canvas.drawText(df.format(r.getUnitPrice()), colUnit, cy, paint);
                canvas.drawText(df.format(r.getTotal()), colTotal, cy, paint);

                canvas.drawLine(x, cy + 4, pageWidth - margin, cy + 4, border);

                cy += rowH;
            }

            // --------------------------------------------------------------------
            // MODE DE PAIEMENT + VENDEUR
            // --------------------------------------------------------------------
            cy += 25;
            canvas.drawText("Mode de paiement : " + header.getPaymentMethod(), x, cy, paint);

            // 🚨 CORRECTION : Remplacement de DatabaseHelper par UserDao 🚨
            UserDao userDao = new UserDao(ctx);
            String vendeurName= "—";
            User vendeurUser = userDao.getUserById(header.getUserId());
            if (vendeurUser != null) {
                vendeurName = vendeurUser.getUsername() + " " + vendeurUser.getLastname();
            }
            canvas.drawText("Vendeur : " + vendeurName, x + 250, cy, paint);

            // --------------------------------------------------------------------
            // TOTALS
            // --------------------------------------------------------------------
            cy += 40;
            bold.setTextSize(14);
            canvas.drawText("Total : " + df.format(header.getTotalPrice()) + " KMF", colTotal - 40, cy, bold);

            double euroTotal = kmfToEur(header.getTotalPrice());
            paint.setTextSize(12);
            canvas.drawText("Total en Euro : " + df.format(euroTotal) + " €", colTotal - 40, cy + 20, paint);

            // --------------------------------------------------------------------
            // FOOTER
            // --------------------------------------------------------------------
            paint.setColor(0xFF777777);
            paint.setTextSize(9);

            String footer = shopName + " • " + shopAddress + " • " + shopContact;
            if (shopEmail != null && !shopEmail.isEmpty()) {
                footer += " • " + shopEmail;
            }

            canvas.drawText(footer, (pageWidth - paint.measureText(footer)) / 2, pageHeight - 30, paint);

            // Finish & save
            pdf.finishPage(page);

            File folder = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "GStock/Invoices");
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, "facture_" + header.getId() + ".pdf");
            FileOutputStream fos = new FileOutputStream(file);

            pdf.writeTo(fos);
            fos.close();
            pdf.close();

            openPdf(ctx, file);

            return file;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File generateQuotePdf(Context ctx,
                                          SaleHeader header,
                                          List<SaleItemRow> items,
                                          Client client,
                                          String shopName,
                                          String shopAddress,
                                          String shopContact,
                                          String shopEmail) {

        try {
            if (header == null || items == null || items.isEmpty())
                return null;

            int pageWidth = 595, pageHeight = 842, margin = 20;

            PdfDocument pdf = new PdfDocument();
            DecimalFormat df = new DecimalFormat("#,##0.00");
            Paint paint = new Paint();
            Paint bold = new Paint();
            bold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = pdf.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int x = margin, y = margin + 18;

            // --------------------------------------------------------------------
            // HEADER (fond + logo + infos shop)
            // --------------------------------------------------------------------
            Paint headerBg = new Paint();
            headerBg.setColor(0xFF1C7D41);
            canvas.drawRect(new RectF(0, 0, pageWidth, 90), headerBg);

            try {
                Bitmap logo = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.ic_logo);
                Bitmap scaled = Bitmap.createScaledBitmap(logo, 70, 70, true);
                canvas.drawBitmap(scaled, x, 10, null);
            } catch (Exception ignored) {}

            bold.setColor(Color.WHITE);
            bold.setTextSize(20);
            canvas.drawText(shopName, x + 90, 35, bold);

            paint.setColor(Color.WHITE);
            paint.setTextSize(10);
            canvas.drawText(shopAddress, pageWidth - margin - 170, 25, paint);

            canvas.drawText("Tél : " + shopContact, pageWidth - margin - 170, 40, paint);

            if (shopEmail != null && !shopEmail.isEmpty()) {
                canvas.drawText("Email : " + shopEmail, pageWidth - margin - 170, 55, paint);
            }


            // --------------------------------------------------------------------
            // TITRE FACTURE + DATE
            // --------------------------------------------------------------------
            bold.setColor(Color.BLACK);
            bold.setTextSize(16);
            canvas.drawText("DEVIS N° DEV-" + header.getId(), x, 130, bold);

            paint.setTextSize(12);
            paint.setColor(0xFF000000);
            canvas.drawText("Date : " + header.getSaleDate(), x, 150, paint);

            // --------------------------------------------------------------------
            // CLIENT
            // --------------------------------------------------------------------
            int clientY = 200;
            bold.setTextSize(14);
            canvas.drawText("Client :", x, clientY, bold);

            paint.setTextSize(12);
            paint.setColor(0xFF000000);
            if (client != null) {
                canvas.drawText(client.getFirstname() + " " + client.getLastname(), x, clientY + 20, paint);

                if (client.getAddress() != null && !client.getAddress().isEmpty())
                    canvas.drawText(client.getAddress(), x, clientY + 40, paint);

                if (client.getPhone() != null && !client.getPhone().isEmpty())
                    canvas.drawText("Tél : " + client.getPhone(), x, clientY + 60, paint);

                if (client.getEmail() != null && !client.getEmail().isEmpty())
                    canvas.drawText("Email : " + client.getEmail(), x, clientY + 80, paint);
            }

            // --------------------------------------------------------------------
            // TABLE HEADER & CONTENT
            // --------------------------------------------------------------------
            int tableY = clientY + 120;
            int rowH = 22;

            int colProduct = x;
            int colQty = colProduct + 230;
            int colUnit = colQty + 60;
            int colTotal = colUnit + 80;

            bold.setColor(0xFF1C7D41);
            bold.setTextSize(12);
            canvas.drawText("Article", colProduct, tableY, bold);
            canvas.drawText("Qté", colQty, tableY, bold);
            canvas.drawText("Prix/U", colUnit, tableY, bold);
            canvas.drawText("Total", colTotal, tableY, bold);

            Paint border = new Paint();
            border.setColor(Color.GRAY);
            border.setStrokeWidth(1);
            canvas.drawLine(x, tableY + 5, pageWidth - margin, tableY + 5, border);

            int cy = tableY + rowH;

            paint.setColor(Color.BLACK);
            paint.setTextSize(11);

            for (SaleItemRow r : items) {
                canvas.drawText(r.getProductName(), colProduct, cy, paint);
                canvas.drawText(String.valueOf(r.getQuantity()), colQty, cy, paint);
                canvas.drawText(df.format(r.getUnitPrice()), colUnit, cy, paint);
                canvas.drawText(df.format(r.getTotal()), colTotal, cy, paint);

                canvas.drawLine(x, cy + 4, pageWidth - margin, cy + 4, border);

                cy += rowH;
            }

            // --------------------------------------------------------------------
            // MODE DE PAIEMENT + VENDEUR
            // --------------------------------------------------------------------
            cy += 25;
            canvas.drawText("Mode de paiement : " + header.getPaymentMethod(), x, cy, paint);

            // 🚨 CORRECTION : Remplacement de DatabaseHelper par UserDao 🚨
            UserDao userDao = new UserDao(ctx);
            String vendeurName= "—";
            User vendeurUser = userDao.getUserById(header.getUserId());
            if (vendeurUser != null) {
                vendeurName = vendeurUser.getUsername() + " " + vendeurUser.getLastname();
            }
            canvas.drawText("Vendeur : " + vendeurName, x + 250, cy, paint);

            // --------------------------------------------------------------------
            // TOTALS
            // --------------------------------------------------------------------
            cy += 40;
            bold.setTextSize(14);
            canvas.drawText("Total : " + df.format(header.getTotalPrice()) + " KMF", colTotal - 40, cy, bold);

            double euroTotal = kmfToEur(header.getTotalPrice());
            paint.setTextSize(12);
            canvas.drawText("Total en Euro : " + df.format(euroTotal) + " €", colTotal - 40, cy + 20, paint);

            // --------------------------------------------------------------------
            // FOOTER
            // --------------------------------------------------------------------
            paint.setColor(0xFF777777);
            paint.setTextSize(9);

            String footer = shopName + " • " + shopAddress + " • " + shopContact;
            if (shopEmail != null && !shopEmail.isEmpty()) {
                footer += " • " + shopEmail;
            }

            canvas.drawText(footer, (pageWidth - paint.measureText(footer)) / 2, pageHeight - 30, paint);

            // Finish & save
            pdf.finishPage(page);

            File folder = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "GStock/Invoices");
            if (!folder.exists()) folder.mkdirs();

            File file = new File(folder, "facture_" + header.getId() + ".pdf");
            FileOutputStream fos = new FileOutputStream(file);

            pdf.writeTo(fos);
            fos.close();
            pdf.close();

            openPdf(ctx, file);

            return file;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void generateQrListPdf(Context context, List<Product> products) {

        String pdfPath = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
        ) + "/qr_codes_produits.pdf";

        try {

            PdfDocument doc = new PdfDocument();
            Paint paint = new Paint();

            int pageWidth = 1200;
            int pageHeight = 1800;
            int pageNumber = 1;

            PdfDocument.PageInfo pageInfo =
                    new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();

            PdfDocument.Page page = doc.startPage(pageInfo);
            Canvas canvas = page.getCanvas();

            int y = 150;

            paint.setTextSize(40);
            paint.setFakeBoldText(true);
            canvas.drawText("QR Codes Produits", 400, y, paint);

            y += 120;
            paint.setFakeBoldText(false);

            for (Product p : products) {

                // Nouvelle page si besoin
                if (y > pageHeight - 350) {
                    doc.finishPage(page);
                    pageNumber++;

                    pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create();
                    page = doc.startPage(pageInfo);
                    canvas = page.getCanvas();

                    y = 150;
                }

                // Nom du produit
                canvas.drawText(p.getName(), 50, y, paint);

                // Générer QR directement depuis "PRODUCT_X"
                if (p.getQr_code() != null) {

                    Bitmap qrBitmap = QrUtils.generateQrBitmap(p.getQr_code());
                    Bitmap resized = Bitmap.createScaledBitmap(qrBitmap, 250, 250, false);

                    int qrX = pageWidth - 350;
                    int qrY = y - 150;

                    canvas.drawBitmap(resized, qrX, qrY, paint);
                }

                y += 320;
            }

            doc.finishPage(page);

            File file = new File(pdfPath);
            FileOutputStream out = new FileOutputStream(file);
            doc.writeTo(out);

            doc.close();
            out.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
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

    // Conversion simple
    public static double kmfToEur(double kmf) {
        return kmf * KMF_TO_EUR;
    }

    // Formatage euros (deux décimales)
    public static String formatEur(double eur) {
        // format avec 2 décimales
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(eur);
    }
}