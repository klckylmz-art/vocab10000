package com.yilmaz.sentencestudy;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView webView;
    private TextToSpeech voiceProbe;
    private volatile boolean voicesReady = false;

    private final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (webView == null) return;
            String action = intent.getAction();
            if (PlaybackService.ACTION_SENTENCE_CHANGED.equals(action)) {
                int pos = intent.getIntExtra("orderPos", -1);
                if (pos >= 0) runOnUiThread(() -> webView.evaluateJavascript("window.__nativeSentenceChanged(" + pos + ")", null));
            } else if (PlaybackService.ACTION_ONE_DONE.equals(action)) {
                int id = intent.getIntExtra("requestId", -1);
                if (id >= 0) runOnUiThread(() -> webView.evaluateJavascript("window.__ttsDone(" + id + ")", null));
            }
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new TtsBridge(), "AndroidTTS");

        initVoiceProbe();
        requestNotificationPermission();
        registerPlaybackReceiver();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void registerPlaybackReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(PlaybackService.ACTION_SENTENCE_CHANGED);
        f.addAction(PlaybackService.ACTION_ONE_DONE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(playbackReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(playbackReceiver, f);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44);
        }
    }

    private void initVoiceProbe() {
        voiceProbe = new TextToSpeech(this, status -> voicesReady = status == TextToSpeech.SUCCESS);
    }

    public class TtsBridge {
        @JavascriptInterface public String getVoicesJson() {
            JSONArray arr = new JSONArray();
            if (!voicesReady || voiceProbe == null) return arr.toString();
            try {
                Set<Voice> set = voiceProbe.getVoices();
                if (set == null) return arr.toString();
                List<Voice> list = new ArrayList<>(set);
                list.sort(Comparator.comparing(v -> {
                    Locale l = v.getLocale();
                    String lang = l == null ? "" : l.toLanguageTag();
                    return lang + "|" + v.getName();
                }));
                for (Voice v : list) {
                    Locale l = v.getLocale();
                    if (l == null) continue;
                    String language = l.getLanguage();
                    if (!"en".equalsIgnoreCase(language) && !"tr".equalsIgnoreCase(language)) continue;
                    JSONObject o = new JSONObject();
                    o.put("name", v.getName());
                    o.put("lang", l.toLanguageTag());
                    arr.put(o);
                }
            } catch (Exception ignored) { }
            return arr.toString();
        }

        @JavascriptInterface public void startPlayback(String configJson) {
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.setAction(PlaybackService.CMD_START_PLAYLIST);
            i.putExtra("config", configJson);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        }

        @JavascriptInterface public void speakOne(String text, String lang, String voice, double rate, int requestId) {
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.setAction(PlaybackService.CMD_SPEAK_ONE);
            i.putExtra("text", text);
            i.putExtra("lang", lang);
            i.putExtra("voice", voice);
            i.putExtra("rate", (float) rate);
            i.putExtra("requestId", requestId);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        }

        @JavascriptInterface public void pause() { sendSimple(PlaybackService.CMD_PAUSE); }
        @JavascriptInterface public void resume() { sendSimple(PlaybackService.CMD_RESUME); }
        @JavascriptInterface public void stop() { sendSimple(PlaybackService.CMD_STOP); }
        @JavascriptInterface public boolean isServiceActive() { return PlaybackService.isActive; }

        private void sendSimple(String action) {
            Intent i = new Intent(MainActivity.this, PlaybackService.class);
            i.setAction(action);
            startService(i);
        }
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(playbackReceiver); } catch (Exception ignored) { }
        if (voiceProbe != null) { voiceProbe.stop(); voiceProbe.shutdown(); }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
