package com.yilmaz.vocab10000;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Comparator;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {
    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    @SuppressLint({"SetJavaScriptEnabled", "JavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        initTts();
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            tts.setLanguage(Locale.UK);
            tts.setSpeechRate(0.96f);
            tts.setPitch(0.92f);
            chooseBritishMaleVoice();
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onError(String utteranceId) { notifyDone(utteranceId); }
                @Override public void onDone(String utteranceId) { notifyDone(utteranceId); }
            });
            ttsReady = true;
        });
    }

    private void chooseBritishMaleVoice() {
        try {
            Set<Voice> voices = tts.getVoices();
            if (voices == null) return;
            Voice best = voices.stream()
                    .filter(v -> v.getLocale() != null)
                    .filter(v -> "en".equalsIgnoreCase(v.getLocale().getLanguage()))
                    .filter(v -> "GB".equalsIgnoreCase(v.getLocale().getCountry()))
                    .sorted(Comparator.comparingInt(this::voiceScore).reversed())
                    .findFirst().orElse(null);
            if (best != null) tts.setVoice(best);
        } catch (Exception ignored) { }
    }

    private int voiceScore(Voice v) {
        String n = v.getName() == null ? "" : v.getName().toLowerCase(Locale.ROOT);
        int score = 0;
        if (n.contains("male")) score += 100;
        if (n.contains("daniel") || n.contains("george") || n.contains("ryan") || n.contains("oliver") || n.contains("arthur") || n.contains("alfie") || n.contains("thomas")) score += 60;
        if (!v.isNetworkConnectionRequired()) score += 10;
        return score;
    }

    private void notifyDone(String utteranceId) {
        if (utteranceId == null || !utteranceId.startsWith("vocab-")) return;
        String id = utteranceId.substring(6);
        runOnUiThread(() -> webView.evaluateJavascript("window.__ttsDone(" + id + ")", null));
    }

    public class TtsBridge {
        @JavascriptInterface
        public void speak(String text, int requestId) {
            runOnUiThread(() -> {
                if (!ttsReady || tts == null) {
                    webView.evaluateJavascript("window.__ttsDone(" + requestId + ")", null);
                    return;
                }
                tts.stop();
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "vocab-" + requestId);
            });
        }

        @JavascriptInterface
        public void stop() {
            runOnUiThread(() -> {
                if (tts != null) tts.stop();
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
