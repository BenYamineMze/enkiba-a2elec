package com.enkiba.app.ui;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.enkiba.app.adapters.CartBottomSheetAdapter;
import com.enkiba.app.models.SaleItemRow;
import com.enkiba.app.R;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class CartBottomSheet extends BottomSheetDialogFragment {

    private List<SaleItemRow> cartList;
    private Runnable onCartUpdated;

    public CartBottomSheet(List<SaleItemRow> cartList, Runnable onCartUpdated) {
        this.cartList = cartList;
        this.onCartUpdated = onCartUpdated;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        View view = LayoutInflater.from(getContext()).inflate(R.layout.bottomsheet_cart, null);
        dialog.setContentView(view);

        RecyclerView recyclerView = view.findViewById(R.id.cartRecyclerView);
        TextView textTotal = view.findViewById(R.id.textTotalCart);
        Button btnClose = view.findViewById(R.id.btnCloseCart);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        CartBottomSheetAdapter adapter = new CartBottomSheetAdapter(cartList, () -> {
            updateTotal(textTotal);
        });

        recyclerView.setAdapter(adapter);
        updateTotal(textTotal);

        btnClose.setOnClickListener(v -> dismiss());

        return dialog;
    }

    private void updateTotal(TextView textTotal) {
        double total = 0;
        for (SaleItemRow s : cartList) total += s.getTotal();
        textTotal.setText("Total : " + total + " KMF");
    }
}
