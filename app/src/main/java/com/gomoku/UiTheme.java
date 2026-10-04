package com.gomoku;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;

/** Small native UI helpers; no dependencies or changes to game/engine logic. */
final class UiTheme {
    private UiTheme() { }
    static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
    static void applySystemBars(Activity activity) {
        int canvas = activity.getResources().getColor(R.color.canvas);
        if (Build.VERSION.SDK_INT >= 21) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            // Light bar icons are unavailable on older Android versions.
            activity.getWindow().setStatusBarColor(activity.getResources().getColor(
                    Build.VERSION.SDK_INT >= 23 ? R.color.canvas : R.color.accent));
            activity.getWindow().setNavigationBarColor(Build.VERSION.SDK_INT >= 26
                    ? canvas : activity.getResources().getColor(R.color.accent));
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        activity.getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    static View dialogInput(Activity activity, EditText input) {
        input.setTextColor(activity.getResources().getColor(R.color.ink));
        input.setHintTextColor(activity.getResources().getColor(R.color.muted));
        input.setTextSize(15);
        input.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        input.setBackgroundResource(R.drawable.bg_input);
        FrameLayout box = new FrameLayout(activity);
        box.setPadding(dp(activity, 24), dp(activity, 8), dp(activity, 24), dp(activity, 8));
        box.addView(input, new FrameLayout.LayoutParams(-1, -2));
        return box;
    }
}
