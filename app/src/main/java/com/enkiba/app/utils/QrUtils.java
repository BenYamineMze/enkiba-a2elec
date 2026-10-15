package com.enkiba.app.utils;


import android.graphics.Bitmap;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import java.io.ByteArrayOutputStream;

public class QrUtils {

    public static String generateQrBase64(String text) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            int size = 300;

            com.google.zxing.common.BitMatrix bitMatrix =
                    writer.encode(text, BarcodeFormat.QR_CODE, size, size);

            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bmp.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, baos);

            return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);

        } catch (WriterException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Bitmap generateQrBitmap(String text) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            int size = 500;

            BitMatrix matrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bmp.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }

            return bmp;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
