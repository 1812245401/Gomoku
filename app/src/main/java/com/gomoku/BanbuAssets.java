package com.gomoku;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

/*
 * 本地资源服务器（WebViewClient 子类）。
 *
 * 为什么需要它：
 *   assets/recognize/recognize.js 是 ES module，内部用了 import.meta.url 与动态 import()
 *   （见 recognition-accelerator.js 的 new URL('recognition-worker.js', import.meta.url)）。
 *   这些语法只在 http(s) 源下可用，file:// 与 assets:// 下必然失败。
 *   本工程是纯 SDK 工程（无 androidx，也就没有 WebViewAssetLoader），
 *   所以用 shouldInterceptRequest 自己把 assets/recognize/ 挂到一个 https 假域上：
 *
 *       https://banbu.local/recognize/host.html
 *       https://banbu.local/recognize/recognize.js
 *       https://banbu.local/recognize/recognition-accelerator.js
 *       https://banbu.local/recognize/recognition-worker.js
 *
 *   请求全部在本地被拦截并直接读取 assets，不产生任何真实网络访问（离线可用）。
 */
public class BanbuAssets extends WebViewClient {

    public static final String SCHEME = "https";
    public static final String HOST = "banbu.local";
    public static final String DIR = "recognize";
    private static final String PAGE = "host.html";

    private final Context context;

    public BanbuAssets(Context context) {
        this.context = context;
    }

    public static String pageUrl() {
        return SCHEME + "://" + HOST + "/" + DIR + "/" + PAGE;
    }

    /* API < 21 走这个重载 */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
        return serve(url);
    }

    /* API >= 21 走这个重载 */
    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request == null || request.getUrl() == null) return null;
        return serve(request.getUrl().toString());
    }

    private WebResourceResponse serve(String url) {
        if (url == null) return null;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null || !HOST.equalsIgnoreCase(host)) {
            /* 不是假域的请求，交回系统默认处理 */
            return null;
        }
        String path = uri.getPath();
        if (path == null || path.length() == 0) {
            path = "/" + DIR + "/" + PAGE;
        }
        if (path.startsWith("/")) path = path.substring(1);
        if (path.indexOf("..") >= 0) return notFound();
        if (!path.startsWith(DIR + "/")) return notFound();
        try {
            InputStream in = context.getAssets().open(path);
            return ok(mimeOf(path), in);
        } catch (IOException e) {
            return notFound();
        }
    }

    private static String mimeOf(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js") || path.endsWith(".mjs")) return "text/javascript";
        if (path.endsWith(".wasm")) return "application/wasm";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private static WebResourceResponse ok(String mime, InputStream in) {
        if (Build.VERSION.SDK_INT >= 21) {
            HashMap<String, String> headers = new HashMap<String, String>();
            headers.put("Cache-Control", "no-store");
            headers.put("Access-Control-Allow-Origin", "*");
            return new WebResourceResponse(mime, "utf-8", 200, "OK", headers, in);
        }
        return new WebResourceResponse(mime, "utf-8", in);
    }

    private static WebResourceResponse notFound() {
        InputStream in = new ByteArrayInputStream(new byte[0]);
        if (Build.VERSION.SDK_INT >= 21) {
            HashMap<String, String> headers = new HashMap<String, String>();
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers, in);
        }
        return new WebResourceResponse("text/plain", "utf-8", in);
    }
}