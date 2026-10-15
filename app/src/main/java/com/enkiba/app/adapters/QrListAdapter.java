package com.enkiba.app.adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.models.Product;
import com.enkiba.app.utils.QrUtils;

import java.util.List;

public class QrListAdapter extends RecyclerView.Adapter<QrListAdapter.QrViewHolder> {

    private final List<Product> productList;
    private final Context context;

    public QrListAdapter(Context context, List<Product> productList) {
        this.context = context;
        this.productList = productList;
    }

    @NonNull
    @Override
    public QrViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Utilisation du nouveau layout item_article_card
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_qr_code, parent, false);
        return new QrViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull QrViewHolder holder, int position) {
        Product p = productList.get(position);

        holder.tvName.setText(p.getName());

        if (p.getQr_code() != null) {
            // Génération du Bitmap QR Code
            Bitmap bmp = QrUtils.generateQrBitmap(p.getQr_code());
            holder.imgQr.setImageBitmap(bmp);

            // Gestion du clic sur l'image miniature pour l'agrandir
            holder.imgQr.setOnClickListener(v -> {
                showQrDialog(holder.itemView.getContext(), p.getName(), bmp);
            });

            // Rendre aussi la carte entière cliquable pour la même action (optionnel, pour meilleure UX)
            holder.itemView.setOnClickListener(v -> {
                showQrDialog(holder.itemView.getContext(), p.getName(), bmp);
            });
        }
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    // Méthode pour afficher le QR Code en grand dans une boite de dialogue
    private void showQrDialog(Context ctx, String title, Bitmap qrBitmap) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(title);

        ImageView imageView = new ImageView(ctx);
        imageView.setImageBitmap(qrBitmap);
        imageView.setPadding(32, 32, 32, 32);
        // Ajuster la taille de l'image dans le dialog si nécessaire
        imageView.setMinimumHeight(500);
        imageView.setMinimumWidth(500);

        builder.setView(imageView);
        builder.setPositiveButton("Fermer", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    static class QrViewHolder extends RecyclerView.ViewHolder {

        ImageView imgQr;
        TextView tvName;

        public QrViewHolder(@NonNull View itemView) {
            super(itemView);
            // Correspondance avec les IDs du fichier item_article_card.xml
            imgQr = itemView.findViewById(R.id.imgQrCode);
            tvName = itemView.findViewById(R.id.tvArticleName);
        }
    }
}