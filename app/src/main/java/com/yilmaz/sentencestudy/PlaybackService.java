package com.yilmaz.sentencestudy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PlaybackService extends Service {
    public static final String CMD_START_PLAYLIST="start_playlist";
    public static final String CMD_SPEAK_ONE="speak_one";
    public static final String CMD_PAUSE="pause";
    public static final String CMD_RESUME="resume";
    public static final String CMD_STOP="stop";
    public static final String CMD_NEXT="next";
    public static final String CMD_PREV="prev";
    public static final String ACTION_SENTENCE_CHANGED="com.yilmaz.sentencestudy.SENTENCE_CHANGED";
    public static final String ACTION_ONE_DONE="com.yilmaz.sentencestudy.ONE_DONE";
    public static volatile boolean isActive=false;

    private static final String CHANNEL="sentence_tts";
    private static final int NOTIF_ID=7301;

    private TextToSpeech tts;
    private boolean ready=false;
    private boolean paused=false;
    private boolean singleMode=false;
    private int singleRequestId=-1;
    private final Handler handler=new Handler(Looper.getMainLooper());

    private final List<Item> items=new ArrayList<>();
    private final List<Integer> order=new ArrayList<>();
    private int orderPos=0;
    private int partPos=0;
    private int trRepeat=1, enRepeat=1, gapMs=4000;
    private boolean trOn=true,enOn=true,autoAdvance=true;
    private String trVoice="", enVoice="";
    private float trRate=1f,enRate=1f;
    private final List<Part> parts=new ArrayList<>();

    static class Item { String tr,en; Item(String t,String e){tr=t;en=e;} }
    static class Part { String text,lang; Part(String t,String l){text=t;lang=l;} }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIF_ID, buildNotification("Hazır"));
        tts=new TextToSpeech(this,status->{
            ready=status==TextToSpeech.SUCCESS;
            if(ready){
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
                    @Override public void onStart(String id){}
                    @Override public void onDone(String id){ handler.post(()->onUtteranceDone(id)); }
                    @Override public void onError(String id){ handler.post(()->onUtteranceDone(id)); }
                });
                if(isActive && !paused) handler.post(this::speakCurrent);
            }
        });
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null) return START_STICKY;
        String a=intent.getAction();
        if(CMD_START_PLAYLIST.equals(a)) startPlaylist(intent.getStringExtra("config"));
        else if(CMD_SPEAK_ONE.equals(a)) speakOne(intent);
        else if(CMD_PAUSE.equals(a)) pausePlayback();
        else if(CMD_RESUME.equals(a)) resumePlayback();
        else if(CMD_STOP.equals(a)) stopEverything();
        else if(CMD_NEXT.equals(a)) nextSentence(1);
        else if(CMD_PREV.equals(a)) nextSentence(-1);
        return START_STICKY;
    }

    private void startPlaylist(String json){
        try{
            JSONObject o=new JSONObject(json==null?"{}":json);
            items.clear(); order.clear(); parts.clear();
            JSONArray ia=o.getJSONArray("items");
            for(int i=0;i<ia.length();i++){
                JSONObject x=ia.getJSONObject(i);
                items.add(new Item(x.optString("tr"),x.optString("en")));
            }
            JSONArray oa=o.getJSONArray("order");
            for(int i=0;i<oa.length();i++) order.add(oa.getInt(i));
            orderPos=Math.max(0,Math.min(o.optInt("orderPos",0),Math.max(0,order.size()-1)));
            trRepeat=Math.max(1,o.optInt("trRepeat",1));
            enRepeat=Math.max(1,o.optInt("enRepeat",1));
            if(trRepeat>enRepeat) trRepeat=enRepeat;
            trOn=o.optBoolean("trOn",true); enOn=o.optBoolean("enOn",true);
            trVoice=o.optString("trVoice",""); enVoice=o.optString("enVoice","");
            trRate=(float)o.optDouble("trRate",1.0); enRate=(float)o.optDouble("enRate",1.0);
            autoAdvance=o.optBoolean("autoAdvance",true);
            gapMs=Math.max(0,o.optInt("gapMs",4000));
            paused=false; singleMode=false; partPos=0; isActive=true;
            rebuildParts();
            updateNotification("Oynatılıyor");
            broadcastSentence();
            if(ready) speakCurrent();
        }catch(Exception e){ stopEverything(); }
    }

    private void speakOne(Intent i){
        singleMode=true; paused=false; isActive=true;
        singleRequestId=i.getIntExtra("requestId",-1);
        String text=i.getStringExtra("text");
        String lang=i.getStringExtra("lang");
        String voice=i.getStringExtra("voice");
        float rate=i.getFloatExtra("rate",1f);
        parts.clear(); parts.add(new Part(text==null?"":text,lang==null?"en":lang)); partPos=0;
        if("tr".equals(lang)){trVoice=voice==null?"":voice;trRate=rate;}else{enVoice=voice==null?"":voice;enRate=rate;}
        updateNotification("Seslendiriliyor");
        if(ready) speakCurrent();
    }

    private void rebuildParts(){
        parts.clear(); partPos=0;
        if(order.isEmpty()||items.isEmpty()) return;
        int idx=order.get(orderPos);
        if(idx<0||idx>=items.size()) return;
        Item it=items.get(idx);
        for(int i=0;i<trRepeat;i++){
            if(trOn) parts.add(new Part(it.tr,"tr"));
            if(enOn) parts.add(new Part(it.en,"en"));
        }
        for(int i=trRepeat;i<enRepeat;i++) if(enOn) parts.add(new Part(it.en,"en"));
    }

    private void speakCurrent(){
        if(!ready||paused||!isActive) return;
        if(partPos>=parts.size()){ finishSentence(); return; }
        Part p=parts.get(partPos);
        applyVoice(p.lang);
        tts.setSpeechRate("tr".equals(p.lang)?trRate:enRate);
        tts.speak(p.text,TextToSpeech.QUEUE_FLUSH,null,"part-"+orderPos+"-"+partPos+"-"+System.nanoTime());
    }

    private void applyVoice(String lang){
        try{
            String wanted="tr".equals(lang)?trVoice:enVoice;
            Set<Voice> vs=tts.getVoices();
            Voice selected=null;
            if(vs!=null && wanted!=null && !wanted.isEmpty()) for(Voice v:vs) if(wanted.equals(v.getName())){selected=v;break;}
            if(selected!=null){ tts.setVoice(selected); return; }
            tts.setLanguage("tr".equals(lang)?new Locale("tr","TR"):Locale.UK);
        }catch(Exception ignored){}
    }

    private void onUtteranceDone(String id){
        if(!isActive||paused) return;
        if(singleMode){
            int req=singleRequestId;
            singleMode=false; isActive=false;
            sendBroadcast(new Intent(ACTION_ONE_DONE).setPackage(getPackageName()).putExtra("requestId",req));
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
            return;
        }
        partPos++;
        if(partPos<parts.size()){ speakCurrent(); return; }
        finishSentence();
    }

    private void finishSentence(){
        if(!autoAdvance){ isActive=false; updateNotification("Tamamlandı"); return; }
        handler.postDelayed(()->{
            if(!isActive||paused||order.isEmpty()) return;
            orderPos=(orderPos+1)%order.size();
            rebuildParts(); broadcastSentence(); speakCurrent();
        },gapMs);
    }

    private void pausePlayback(){
        paused=true; if(tts!=null) tts.stop(); updateNotification("Duraklatıldı");
    }
    private void resumePlayback(){
        if(!isActive) return; paused=false; updateNotification("Oynatılıyor"); if(ready) speakCurrent();
    }
    private void nextSentence(int delta){
        if(order.isEmpty()) return;
        if(tts!=null) tts.stop(); handler.removeCallbacksAndMessages(null);
        orderPos=(orderPos+delta)%order.size(); if(orderPos<0) orderPos+=order.size();
        rebuildParts(); broadcastSentence(); if(!paused&&ready) speakCurrent();
    }
    private void stopEverything(){
        isActive=false; paused=false; handler.removeCallbacksAndMessages(null); if(tts!=null) tts.stop();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }

    private void broadcastSentence(){
        sendBroadcast(new Intent(ACTION_SENTENCE_CHANGED).setPackage(getPackageName()).putExtra("orderPos",orderPos));
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"Sentence Study playback",NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }
    private PendingIntent actionPI(String action,int request){
        Intent i=new Intent(this,PlaybackService.class).setAction(action);
        return PendingIntent.getService(this,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification buildNotification(String state){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_media_play)
         .setContentTitle("Sentence Study")
         .setContentText(state)
         .setContentIntent(content)
         .setOngoing(isActive)
         .addAction(android.R.drawable.ic_media_previous,"Önceki",actionPI(CMD_PREV,2))
         .addAction(paused?android.R.drawable.ic_media_play:android.R.drawable.ic_media_pause,paused?"Devam":"Duraklat",actionPI(paused?CMD_RESUME:CMD_PAUSE,3))
         .addAction(android.R.drawable.ic_media_next,"Sonraki",actionPI(CMD_NEXT,4))
         .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Durdur",actionPI(CMD_STOP,5));
        return b.build();
    }
    private void updateNotification(String state){ getSystemService(NotificationManager.class).notify(NOTIF_ID,buildNotification(state)); }

    @Override public void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(tts!=null){tts.stop();tts.shutdown();}
        isActive=false;
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
