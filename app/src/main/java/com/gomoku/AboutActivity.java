package com.gomoku;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 关于页。
 *
 * 纯离线应用，本页只做本地展示与"跳转外部 App"的 Intent，
 * 不请求任何网络权限（QQ / GitHub / 邮箱 / 协议 / 隐私 全部交给
 * 系统浏览器或对应 App 打开）。
 */
public class AboutActivity extends Activity {

    /* 联系与项目信息。改这里即可同步全页。 */
    private static final String APP_NAME = "Gomoku";
    private static final String APP_VERSION = "1.1.0";

    private static final String GITHUB_URL =
            "https://github.com/1812245401/Gomoku";
    private static final String QQ_NUMBER = "1812245401";
    private static final String EMAIL = "1812245401@qq.com";

    /* QQ 网页版加好友兜底链接（没有装 QQ 时用浏览器打开）。 */
    private static final String QQ_WEB_URL =
            "https://wpa.qq.com/msgrd?v=3&uin=" + QQ_NUMBER
                    + "&site=qq&menu=yes";

    /* 项目内相对路径；有 README / LICENSE / PRIVACY 的网页地址。 */
    private static final String README_URL = GITHUB_URL + "#readme";
    private static final String LICENSE_URL =
            GITHUB_URL + "/blob/main/LICENSE";
    private static final String PRIVACY_URL =
            GITHUB_URL + "/blob/main/PRIVACY.md";

    private static final int COLOR_TITLE = 0xFF2B2B2B;
    private static final int COLOR_TEXT = 0xFF555555;
    private static final int COLOR_ACCENT = 0xFF2B6CB0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(0xFFFAFAF8);
            getWindow().setNavigationBarColor(0xFFF6F5F2);
        }

        int systemUi = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

        if (Build.VERSION.SDK_INT >= 23) {
            systemUi |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }

        if (Build.VERSION.SDK_INT >= 26) {
            systemUi |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }

        getWindow().getDecorView().setSystemUiVisibility(systemUi);

        setContentView(buildContentView());
    }

    /* ---------------------------------------------------------------
     * 整页用代码构建，避免新增 layout 文件（也方便你以后调）。
     * --------------------------------------------------------------- */

    private View buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFAFAF8);

        int pad = dp(20);
        root.setPadding(pad, dp(28), pad, dp(28));

        /* 返回栏 */
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView btnBack = new TextView(this);
        btnBack.setText("← 返回");
        btnBack.setTextSize(16f);
        btnBack.setTextColor(COLOR_ACCENT);
        btnBack.setPadding(0, dp(4), dp(12), dp(4));
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        topBar.addView(btnBack);
        root.addView(topBar);

        /* 标题区 */
        TextView tvApp = new TextView(this);
        tvApp.setText(APP_NAME);
        tvApp.setTextSize(28f);
        tvApp.setTextColor(COLOR_TITLE);
        tvApp.setPadding(0, dp(12), 0, dp(2));
        root.addView(tvApp);

        TextView tvSub = new TextView(this);
        tvSub.setText("五子棋 / 连珠 打谱分析 · 本地 AI 引擎");
        tvSub.setTextSize(14f);
        tvSub.setTextColor(COLOR_TEXT);
        root.addView(tvSub);

        TextView tvVersion = new TextView(this);
        tvVersion.setText("版本 " + APP_VERSION);
        tvVersion.setTextSize(13f);
        tvVersion.setTextColor(COLOR_TEXT);
        tvVersion.setPadding(0, dp(4), 0, dp(16));
        root.addView(tvVersion);

        root.addView(section("引擎"));
        root.addView(info("KataGo", "智子同款 24b 权重（INT8 量化），棋盘评估"));
        root.addView(info("Rapfi", "VCF / 算杀专用解算器"));
        root.addView(info("运行方式", "完全离线 · 纯 CPU 推理 · 不联网"));

        root.addView(section("联系与链接"));
        root.addView(clickable("项目主页 (GitHub)",
                "github.com/1812245401/Gomoku",
                new Runnable() {
                    @Override
                    public void run() {
                        openUrl(GITHUB_URL);
                    }
                }));
        root.addView(clickable("联系作者 (QQ)",
                QQ_NUMBER,
                new Runnable() {
                    @Override
                    public void run() {
                        openQq();
                    }
                }));
        root.addView(clickable("邮箱",
                EMAIL,
                new Runnable() {
                    @Override
                    public void run() {
                        openMail();
                    }
                }));

        root.addView(section("声明"));
        root.addView(clickable("开源协议 (MIT)",
                "点击查看 LICENSE",
                new Runnable() {
                    @Override
                    public void run() {
                        openUrl(LICENSE_URL);
                    }
                }));
        root.addView(clickable("隐私政策",
                "本应用不收集任何个人数据",
                new Runnable() {
                    @Override
                    public void run() {
                        openUrl(PRIVACY_URL);
                    }
                }));

        TextView tvCopy = new TextView(this);
        tvCopy.setText("© 2026 w_wall · MIT License\n如果这个项目帮到你，欢迎在 GitHub 点个 Star ⭐");
        tvCopy.setTextSize(12f);
        tvCopy.setTextColor(COLOR_TEXT);
        tvCopy.setPadding(0, dp(24), 0, 0);
        root.addView(tvCopy);

        return root;
    }

    /* ------------------------- 小组件构造 ------------------------- */

    private TextView section(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(COLOR_TEXT);
        tv.setPadding(0, dp(20), 0, dp(8));
        return tv;
    }

    private TextView info(String key, String value) {
        TextView tv = new TextView(this);
        tv.setText(key + "： " + value);
        tv.setTextSize(14f);
        tv.setTextColor(COLOR_TITLE);
        tv.setPadding(0, dp(3), 0, dp(3));
        return tv;
    }

    private TextView clickable(String title, String sub, final Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(title + "\n" + sub);
        tv.setTextSize(14f);
        tv.setTextColor(COLOR_ACCENT);
        tv.setPadding(0, dp(10), 0, dp(10));

        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });

        return tv;
    }

    /* --------------------------- 跳转 --------------------------- */

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            toast("没有可用的浏览器");
        }
    }

    /**
     * 优先用 QQ App 打开加好友页，没装 QQ 时用浏览器兜底。
     */
    private void openQq() {
        try {
            Intent i = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("mqqwpa://im/chat?chat_type=wpa&uin=" + QQ_NUMBER));
            startActivity(i);
        } catch (Exception e) {
            openUrl(QQ_WEB_URL);
        }
    }

    private void openMail() {
        try {
            Intent i = new Intent(Intent.ACTION_SENDTO);
            i.setData(Uri.parse("mailto:" + EMAIL));
            i.putExtra(Intent.EXTRA_SUBJECT, "Gomoku 反馈");
            startActivity(i);
        } catch (Exception e) {
            toast("没有可用的邮件应用");
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        float d = getResources().getDisplayMetrics().density;
        return (int) (v * d + 0.5f);
    }
}
