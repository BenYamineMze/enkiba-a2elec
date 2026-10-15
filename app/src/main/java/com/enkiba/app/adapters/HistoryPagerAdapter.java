package com.enkiba.app.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.enkiba.app.fragments.SalesHistoryFragment;
import com.enkiba.app.fragments.ProductHistoryFragment;

public class HistoryPagerAdapter extends FragmentStateAdapter {

    public HistoryPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0)
            return new SalesHistoryFragment();
        else
            return new ProductHistoryFragment();
    }

    @Override
    public int getItemCount() {
        return 2; // 2 onglets
    }
}
