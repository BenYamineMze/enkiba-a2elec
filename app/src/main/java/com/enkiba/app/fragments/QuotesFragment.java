package com.enkiba.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.enkiba.app.R;
import com.enkiba.app.activities.QuoteDetailsActivity;
import com.enkiba.app.adapters.QuoteAdapter;
import com.enkiba.app.models.Quote;
import com.enkiba.app.modelsDao.QuoteDao;
import com.enkiba.app.utils.SessionManager;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

/**
 * Onglet « Devis » de l'historique. Un admin voit tous les devis, un vendeur les siens.
 */
public class QuotesFragment extends Fragment {

    private RecyclerView recycler;
    private TextView txtEmpty;
    private TextInputEditText edtSearch;
    private QuoteDao quoteDao;
    private SessionManager sessionManager;
    private QuoteAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_quotes, container, false);
        recycler = view.findViewById(R.id.recyclerQuotes);
        txtEmpty = view.findViewById(R.id.txtEmptyQuotes);
        edtSearch = view.findViewById(R.id.edtSearchQuote);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));

        quoteDao = new QuoteDao(requireContext());
        sessionManager = new SessionManager(requireContext());

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) adapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadQuotes(); // rechargé au retour du détail (statut, conversion, suppression)
    }

    private void loadQuotes() {
        boolean isAdmin = "admin".equalsIgnoreCase(sessionManager.getRole());
        List<Quote> quotes = quoteDao.getQuotes(isAdmin ? null : sessionManager.getUserId());
        adapter = new QuoteAdapter(quotes, this::openQuote);
        recycler.setAdapter(adapter);
        txtEmpty.setVisibility(quotes.isEmpty() ? View.VISIBLE : View.GONE);
        if (edtSearch.getText() != null && edtSearch.getText().length() > 0) {
            adapter.filter(edtSearch.getText().toString());
        }
    }

    private void openQuote(Quote quote) {
        Intent intent = new Intent(getContext(), QuoteDetailsActivity.class);
        intent.putExtra(QuoteDetailsActivity.EXTRA_QUOTE_ID, quote.getId());
        startActivity(intent);
    }
}
