package com.gomoku;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 卡片式对话框。
 *
 * 系统 AlertDialog 的标题 / 列表行 / 按钮由 AlertController 固定布局，
 * 无法贴合本应用的墨绿主色、20dp 圆角与字体，因此这里改用
 * android.app.Dialog + 自绘卡片重做，保证与 colors.xml / styles.xml
 * 里的设计系统一致。
 *
 * 本类只负责外观，不涉及任何棋局 / 引擎 / 存档逻辑。
 */
final class UiDialog {

    /** 列表项点击回调，index 为被点项的序号。 */
    interface OnItemClick {
        void onClick(int index);
    }

    /** 底部按钮点击回调。 */
    interface OnActionClick {
        void onClick();
    }

    private UiDialog() {
    }

    static final class Builder {

        private final Activity activity;

        private String title;
        private String[] items;
        private String[] subtitles;
        private OnItemClick itemClick;
        private int dangerIndex = -1;
        private View inputView;

        private String positiveText;
        private OnActionClick positiveClick;
        private String negativeText;
        private OnActionClick negativeClick;

        Builder(Activity activity) {
            this.activity = activity;
        }

        Builder title(String value) {
            title = value;
            return this;
        }

        Builder items(String[] values, OnItemClick listener) {
            items = values;
            itemClick = listener;
            return this;
        }

        /** 两行式列表：主标题 + 次要说明。 */
        Builder items(String[] values, String[] subs, OnItemClick listener) {
            items = values;
            subtitles = subs;
            itemClick = listener;
            return this;
        }

        /** 把第 index 行渲染为危险色（用于「删除」）。 */
        Builder dangerIndex(int index) {
            dangerIndex = index;
            return this;
        }

        Builder input(View view) {
            inputView = view;
            return this;
        }

        Builder positive(String text, OnActionClick listener) {
            positiveText = text;
            positiveClick = listener;
            return this;
        }

        Builder negative(String text, OnActionClick listener) {
            negativeText = text;
            negativeClick = listener;
            return this;
        }

        void show() {
            final Dialog dialog = new Dialog(activity, R.style.UiDialogTheme);

            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setCanceledOnTouchOutside(true);

            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_dialog);

            buildTitle(card);
            buildItems(dialog, card);
            buildInput(card);
            buildActions(dialog, card);

            dialog.setContentView(card);
            applyWindow(dialog);
            dialog.show();
        }

        private void buildTitle(LinearLayout card) {
            if (title == null || title.length() == 0) {
                return;
            }

            TextView view = new TextView(activity);
            view.setText(title);
            view.setTextSize(17);
            view.setTextColor(activity.getResources().getColor(R.color.ink));
            view.setTypeface(
                    Typeface.create("sans-serif-medium", Typeface.NORMAL));
            view.setPadding(
                    UiTheme.dp(activity, 20),
                    UiTheme.dp(activity, 18),
                    UiTheme.dp(activity, 20),
                    UiTheme.dp(activity, 14));

            card.addView(view, new LinearLayout.LayoutParams(-1, -2));
        }

        private void buildItems(final Dialog dialog, LinearLayout card) {
            if (items == null || items.length == 0) {
                return;
            }

            addDivider(card, 0);

            for (int i = 0; i < items.length; i++) {
                final int index = i;

                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setBackgroundResource(R.drawable.bg_dialog_item);
                row.setClickable(true);
                row.setMinimumHeight(UiTheme.dp(activity, 52));
                row.setPadding(
                        UiTheme.dp(activity, 20),
                        UiTheme.dp(activity, 14),
                        UiTheme.dp(activity, 20),
                        UiTheme.dp(activity, 14));

                TextView primary = new TextView(activity);
                primary.setText(items[i]);
                primary.setTextSize(15);
                primary.setTextColor(activity.getResources().getColor(
                        i == dangerIndex ? R.color.danger : R.color.ink));
                row.addView(primary, new LinearLayout.LayoutParams(-1, -2));

                if (subtitles != null && i < subtitles.length
                        && subtitles[i] != null
                        && subtitles[i].length() > 0) {
                    TextView sub = new TextView(activity);
                    sub.setText(subtitles[i]);
                    sub.setTextSize(12);
                    sub.setTextColor(activity.getResources().getColor(
                            R.color.muted));

                    LinearLayout.LayoutParams subLp =
                            new LinearLayout.LayoutParams(-1, -2);
                    subLp.topMargin = UiTheme.dp(activity, 3);
                    row.addView(sub, subLp);
                }

                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dialog.dismiss();
                        if (itemClick != null) {
                            itemClick.onClick(index);
                        }
                    }
                });

                card.addView(row, new LinearLayout.LayoutParams(-1, -2));

                if (i < items.length - 1) {
                    addDivider(card, 20);
                }
            }
        }

        private void buildInput(LinearLayout card) {
            if (inputView == null) {
                return;
            }
            card.addView(inputView, new LinearLayout.LayoutParams(-1, -2));
        }

        private void buildActions(final Dialog dialog, LinearLayout card) {
            if (positiveText == null && negativeText == null) {
                return;
            }

            addDivider(card, 0);

            LinearLayout bar = new LinearLayout(activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            bar.setPadding(
                    UiTheme.dp(activity, 12),
                    UiTheme.dp(activity, 8),
                    UiTheme.dp(activity, 12),
                    UiTheme.dp(activity, 8));

            if (negativeText != null) {
                bar.addView(buildAction(dialog, negativeText,
                        R.color.muted, negativeClick));
            }
            if (positiveText != null) {
                bar.addView(buildAction(dialog, positiveText,
                        R.color.accent, positiveClick));
            }

            card.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        }

        private TextView buildAction(final Dialog dialog, String text,
                int colorRes, final OnActionClick listener) {

            TextView button = new TextView(activity);
            button.setText(text);
            button.setTextSize(14);
            button.setTextColor(activity.getResources().getColor(colorRes));
            button.setTypeface(
                    Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setGravity(Gravity.CENTER);
            button.setBackgroundResource(R.drawable.bg_dialog_item);
            button.setClickable(true);
            button.setMinWidth(UiTheme.dp(activity, 64));
            button.setPadding(
                    UiTheme.dp(activity, 16),
                    UiTheme.dp(activity, 10),
                    UiTheme.dp(activity, 16),
                    UiTheme.dp(activity, 10));

            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = UiTheme.dp(activity, 4);
            button.setLayoutParams(lp);

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    if (listener != null) {
                        listener.onClick();
                    }
                }
            });

            return button;
        }

        private void applyWindow(Dialog dialog) {
            Window window = dialog.getWindow();
            if (window == null) {
                return;
            }

            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            WindowManager.LayoutParams lp = window.getAttributes();
            int screen = activity.getResources()
                    .getDisplayMetrics().widthPixels;
            int margin = UiTheme.dp(activity, 32);
            int max = UiTheme.dp(activity, 360);
            int width = Math.min(screen - margin * 2, max);
            if (width > 0) {
                lp.width = width;
            }
            window.setAttributes(lp);
        }

        private void addDivider(LinearLayout parent, int insetDp) {
            View line = new View(activity);
            line.setBackgroundColor(
                    activity.getResources().getColor(R.color.line));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    -1, UiTheme.dp(activity, 1));
            if (insetDp > 0) {
                int inset = UiTheme.dp(activity, insetDp);
                lp.leftMargin = inset;
                lp.rightMargin = inset;
            }

            parent.addView(line, lp);
        }
    }
}
