package com.flatfisher.dialogflowchatbotsample;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.AsyncTask;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.os.Build;
import android.speech.tts.UtteranceProgressListener;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.content.res.AssetFileDescriptor;
import android.widget.Toast;

import java.io.IOException;

import com.github.bassaer.chatmessageview.model.Message;
import com.github.bassaer.chatmessageview.view.ChatView;
import com.google.gson.Gson;
import com.google.gson.JsonElement;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.api.AIServiceException;
import ai.api.RequestExtras;
import ai.api.android.AIConfiguration;
import ai.api.android.AIDataService;
import ai.api.android.GsonFactory;
import ai.api.model.AIContext;
import ai.api.model.AIError;
import ai.api.model.AIEvent;
import ai.api.model.AIRequest;
import ai.api.model.AIResponse;
import ai.api.model.Metadata;
import ai.api.model.Result;
import ai.api.model.Status;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, TextToSpeech.OnInitListener {

    public static final String TAG = MainActivity.class.getName();
    private Gson gson = GsonFactory.getGson();
    private AIDataService aiDataService;
    private ChatView chatView;
    private User myAccount;
    private User droidKaigiBot;
    private TextToSpeech tts;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initChatView();

        //Language, Dialogflow Client access token
        final LanguageConfig config = new LanguageConfig("ja", "a2b52a5b0d4a4530ae26ea7efcdb87da");
        initService(config);

        //TTS
        tts = new TextToSpeech(this,this);

    }

    @Override
    public void onClick(View v) {
        //new message
        final Message message = new Message.Builder()
                .setUser(myAccount)
                .setRightMessage(true)
                .setMessageText(chatView.getInputText())
                .hideIcon(true)
                .build();
        //Set to chat view
        chatView.send(message);
        sendRequest(chatView.getInputText());
        Log.d("mes",chatView.getInputText());
        /*
        if (mediaPlayer == null){
            //Start Music
            audioPlay();
        }else{
            //Stop Music
            audioStop();
        }*/
        //Reset edit text
        chatView.setInputText("");
    }

    /*
     * TTS
     */
    @Override
    public void onInit(int status) {
        if (TextToSpeech.SUCCESS == status) {
            Log.d(TAG, "initialized");
        } else {
            Log.e(TAG, "failed to initialize");
        }
    }

    private void shutDown(){
        if (null != tts) {
            // to release the resource of TextToSpeech
            tts.shutdown();
        }
    }

    private void speechText(String string) {
        if (0 < string.length()) {
            if (tts.isSpeaking()) {
                tts.stop();
                //return;
            }
            setSpeechRate(1.0f);
            setSpeechPitch(1.0f);


            if (Build.VERSION.SDK_INT >= 21){
                // SDK 21 以上
                tts.speak(string, TextToSpeech.QUEUE_FLUSH, null, "messageID");
            }
            else{
                // tts.speak(text, TextToSpeech.QUEUE_FLUSH, null) に
                // KEY_PARAM_UTTERANCE_ID を HasMap で設定
                HashMap<String, String> map = new HashMap<String, String>();
                map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID,"messageID");
                tts.speak(string, TextToSpeech.QUEUE_FLUSH, map);
            }

            setTtsListener();
        }
    }

    // 読み上げのスピード
    private void setSpeechRate(float rate){
        if (null != tts) {
            tts.setSpeechRate(rate);
        }
    }

    // 読み上げのピッチ
    private void setSpeechPitch(float pitch){
        if (null != tts) {
            tts.setPitch(pitch);
        }
    }

    // 読み上げの始まりと終わりを取得
    private void setTtsListener(){
        // android version more than 15th
        if (Build.VERSION.SDK_INT >= 15){
            int listenerResult =
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onDone(String utteranceId) {
                            Log.d(TAG,"progress on Done " + utteranceId);
                        }

                        @Override
                        public void onError(String utteranceId) {
                            Log.d(TAG,"progress on Error " + utteranceId);
                        }

                        @Override
                        public void onStart(String utteranceId) {
                            Log.d(TAG,"progress on Start " + utteranceId);
                        }
                    });

            if (listenerResult != TextToSpeech.SUCCESS) {
                Log.e(TAG, "failed to add utterance progress listener");
            }
        }
        else {
            Log.e(TAG, "Build VERSION is less than API 15");
        }
    }

    protected void onDestroy() {
        super.onDestroy();
        shutDown();
    }

    private boolean audioSetup(){
        boolean fileCheck = false;

        // インタンスを生成
        mediaPlayer = new MediaPlayer();

        //音楽ファイル名, あるいはパス
        String filePath = "Lily_V3_Blue_eyes_free_ver.mp3";

        // assetsから mp3 ファイルを読み込み
        try(AssetFileDescriptor afdescripter = getAssets().openFd(filePath);)
        {
            // MediaPlayerに読み込んだ音楽ファイルを指定
            mediaPlayer.setDataSource(afdescripter.getFileDescriptor(),
                    afdescripter.getStartOffset(),
                    afdescripter.getLength());
            // 音量調整を端末のボタンに任せる
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer.prepare();
            fileCheck = true;
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        return fileCheck;
    }

    private void audioPlay() {

        if (mediaPlayer == null) {
            // audio ファイルを読出し
            if (audioSetup()){
                Toast.makeText(getApplication(), "Rread audio file", Toast.LENGTH_SHORT).show();
            }
            else{
                Toast.makeText(getApplication(), "Error: read audio file", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        else{
            // 繰り返し再生する場合
            mediaPlayer.stop();
            mediaPlayer.reset();
            // リソースの解放
            mediaPlayer.release();
        }

        // 再生する
        mediaPlayer.start();

        // 終了を検知するリスナー
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                Log.d("debug","end of audio");
                audioStop();
            }
        });
    }

    private void audioStop() {
        // 再生終了
        mediaPlayer.stop();
        // リセット
        mediaPlayer.reset();
        // リソースの解放
        mediaPlayer.release();

        mediaPlayer = null;
    }

    /*
     * AIRequest should have query OR event
     */
    private void sendRequest(String text) {
        Log.d(TAG, text);
        final String queryString = String.valueOf(text);
        final String eventString = null;
        final String contextString = null;

        if (TextUtils.isEmpty(queryString) && TextUtils.isEmpty(eventString)) {
            onError(new AIError(getString(R.string.non_empty_query)));
            return;
        }

        new AiTask().execute(queryString, eventString, contextString);
    }

    public class AiTask extends AsyncTask<String, Void, AIResponse> {
        private AIError aiError;

        @Override
        protected AIResponse doInBackground(final String... params) {
            final AIRequest request = new AIRequest();
            String query = params[0];
            String event = params[1];
            String context = params[2];

            if (!TextUtils.isEmpty(query)){
                request.setQuery(query);
            }

            if (!TextUtils.isEmpty(event)){
                request.setEvent(new AIEvent(event));
            }

            RequestExtras requestExtras = null;
            if (!TextUtils.isEmpty(context)) {
                final List<AIContext> contexts = Collections.singletonList(new AIContext(context));
                requestExtras = new RequestExtras(contexts, null);
            }

            try {
                return aiDataService.request(request, requestExtras);
            } catch (final AIServiceException e) {
                aiError = new AIError(e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(final AIResponse response) {
            if (response != null) {
                onResult(response);
            } else {
                onError(aiError);
            }
        }
    }


    private void onResult(final AIResponse response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Variables
                gson.toJson(response);
                final Status status = response.getStatus();
                final Result result = response.getResult();
                final String speech = result.getFulfillment().getSpeech();
                final Metadata metadata = result.getMetadata();
                final HashMap<String, JsonElement> params = result.getParameters();

                // Logging
                Log.d(TAG, "onResult");
                Log.i(TAG, "Received success response");
                Log.i(TAG, "Status code: " + status.getCode());
                Log.i(TAG, "Status type: " + status.getErrorType());
                Log.i(TAG, "Resolved query: " + result.getResolvedQuery());
                Log.i(TAG, "Action: " + result.getAction());
                Log.i(TAG, "Speech: " + speech);

                if (metadata != null) {
                    Log.i(TAG, "Intent id: " + metadata.getIntentId());
                    Log.i(TAG, "Intent name: " + metadata.getIntentName());
                }

                if (params != null && !params.isEmpty()) {
                    Log.i(TAG, "Parameters: ");
                    for (final Map.Entry<String, JsonElement> entry : params.entrySet()) {
                        Log.i(TAG, String.format("%s: %s",
                                entry.getKey(), entry.getValue().toString()));
                    }
                }

                //Update view to bot says
                final Message receivedMessage = new Message.Builder()
                        .setUser(droidKaigiBot)
                        .setRightMessage(false)
                        .setMessageText(speech)
                        .build();
                chatView.receive(receivedMessage);

                //返答の確認と読み上げ
                Log.d("speechText", speech);
                speechText(speech);
            }
        });
    }

    private void onError(final AIError error) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.e(TAG,error.toString());
            }
        });
    }

    private void initChatView() {
        int myId = 0;
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_action_user);
        String myName = "Fish";
        myAccount = new User(myId, myName, icon);

        int botId = 1;
        String botName = "DroidKaigi";
        droidKaigiBot = new User(botId, botName, icon);

        chatView = findViewById(R.id.chat_view);
        chatView.setRightBubbleColor(ContextCompat.getColor(this, R.color.green500));
        chatView.setLeftBubbleColor(Color.WHITE);
        chatView.setBackgroundColor(ContextCompat.getColor(this, R.color.blueGray500));
        chatView.setSendButtonColor(ContextCompat.getColor(this, R.color.lightBlue500));
        chatView.setSendIcon(R.drawable.ic_action_send);
        chatView.setRightMessageTextColor(Color.WHITE);
        chatView.setLeftMessageTextColor(Color.BLACK);
        chatView.setUsernameTextColor(Color.WHITE);
        chatView.setSendTimeTextColor(Color.WHITE);
        chatView.setDateSeparatorColor(Color.WHITE);
        chatView.setInputTextHint("new message...");
        chatView.setMessageMarginTop(5);
        chatView.setMessageMarginBottom(5);
        chatView.setOnClickSendButtonListener(this);
    }

    private void initService(final LanguageConfig languageConfig) {
        final AIConfiguration.SupportedLanguages lang =
                AIConfiguration.SupportedLanguages.fromLanguageTag(languageConfig.getLanguageCode());
        final AIConfiguration config = new AIConfiguration(languageConfig.getAccessToken(),
                lang,
                AIConfiguration.RecognitionEngine.System);
        aiDataService = new AIDataService(this, config);
    }
}