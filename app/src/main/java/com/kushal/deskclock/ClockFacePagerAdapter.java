package com.kushal.deskclock;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Adapter that serves the two Clock Face fragments to the ViewPager2.
 *
 * Position 0 → ModernStandByFragment  (iOS 17 style split-screen)
 * Position 1 → RetroSplitFlapFragment (mechanical flip clock)
 */
public class ClockFacePagerAdapter extends FragmentStateAdapter {

    private static final int FACE_COUNT = 2;

    public ClockFacePagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return new ModernStandByFragment();
        if (position == 1) return new RetroSplitFlapFragment();
        return new SpotifyStandByFragment();
    }

    @Override
    public int getItemCount() {
        return 3;
    }
}
