package com.tf.speech;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;

    private RecognizeCommands recognizeCommands = null;

    private Thread recordingThread;
    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;

    private static final long AVERAGE_WINDOW_DURATION_MS = 1000;
    private static final float DETECTION_THRESHOLD = 0.50f;
    private static final int SUPPRESSION_MS = 1500;
    private static final int MINIMUM_COUNT = 3;

    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 30;
    private static final String LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt";
    private static final String MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.tflite";

    private static final int REQUEST_RECORD_AUDIO = 13;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    private List<String> labels = new ArrayList<String>();

    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    private final Interpreter.Options tfLiteOptions = new Interpreter.Options();
    private MappedByteBuffer tfLiteModel;

    private Interpreter tfLite;

    boolean shouldContinue = true;

    //선택 스피너
    Spinner sp_main1, sp_main2;
    LinearLayout ll_hint_spinner1, ll_hint_spinner2;

    //음악재생
    MediaPlayer mediaPlayer;

    //종료버튼
    Button btn_end;
    //시작 이미지 아이콘
    ImageView iv_start;

    int type = 0;

    Handler handler;

    String[] allist1;
    String[] allist2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler(getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                if (msg.what == 1) {
                    mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.ns_chosun);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            NotificationSomethings("알림", "다음 정거장은 조선대 입니다. 하차 준비해주세요.");
                            handler.sendEmptyMessageDelayed(2, 3000);
                        }
                    });
                    mediaPlayer.setLooping(false);
                    mediaPlayer.start();

                    type = 2;
                } else if (msg.what == 2) {
                    mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.ts_chosun);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            NotificationSomethings("알림", "이번 정거장은 조선대 입니다. 하차 해주세요.");
                            type = 0;
                        }
                    });
                    mediaPlayer.setLooping(false);
                    mediaPlayer.start();
                    type = 3;

                }
            }
        };

        sp_main1 = findViewById(R.id.sp_main1);
        sp_main2 = findViewById(R.id.sp_main2);
        ll_hint_spinner1 = findViewById(R.id.ll_hint_spinner);
        ll_hint_spinner2 = findViewById(R.id.ll_hint_spinner2);

        mediaPlayer = new MediaPlayer();

        btn_end = findViewById(R.id.btn_end);
        btn_end.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        iv_start = findViewById(R.id.iv_start);
        iv_start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (type == 0) {
                    mediaPlayer = MediaPlayer.create(MainActivity.this, R.raw.na_top);
                    mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            NotificationSomethings("알림", "감사합니다. 버스 결제되었습니다.");
                            handler.sendEmptyMessageDelayed(1, 3000);
                        }
                    });
                    mediaPlayer.setLooping(false);
                    mediaPlayer.start();
                     /* startRecording();
        startRecognition();*/
                    type = 1;
                }
            }
        });
        //   initTf();

        initSp();


    }

    public void setType1() {
        spEnable(false);
        NotificationSomethings("알림", "감사합니다. 버스 결제되었습니다.");
        type = 1;
    }

    private void setType2() {
        spEnable(false);
        NotificationSomethings("알림", "다음 정거장은 " + allist2[sp_main2.getSelectedItemPosition()] + " 입니다. 하차 준비해주세요.");
        type = 2;


    }

    private void setType3() {
        NotificationSomethings("알림", "이번 정거장은 " + allist2[sp_main2.getSelectedItemPosition()] + " 입니다. 하차 해주세요.");
        type = 3;
        spEnable(false);
    }

    private void setType4() {
        NotificationSomethings("알림", "하차하셨습니다.");
        type = 0;
        spEnable(true);
    }

    private void spEnable(boolean isEnable) {
        sp_main1.setEnabled(isEnable);
        sp_main2.setEnabled(isEnable);
    }


    public static final String NOTIFICATION_CHANNEL_ID = "10001";

    public void NotificationSomethings(String title, String content) {


        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_foreground)) //BitMap 이미지 요구
                .setContentTitle(title)
                .setContentText(content)
                // 더 많은 내용이라서 일부만 보여줘야 하는 경우 아래 주석을 제거하면 setContentText에 있는 문자열 대신 아래 문자열을 보여줌
                //.setStyle(new NotificationCompat.BigTextStyle().bigText("더 많은 내용을 보여줘야 하는 경우..."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent) // 사용자가 노티피케이션을 탭시 ResultActivity로 이동하도록 설정
                .setAutoCancel(true);

        //OREO API 26 이상에서는 채널 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            builder.setSmallIcon(getNotificationIcon()); //mipmap 사용시 Oreo 이상에서 시스템 UI 에러남
            CharSequence channelName = "노티페케이션 채널";
            String description = "오레오 이상을 위한 것임";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, importance);
            channel.setDescription(description);

            // 노티피케이션 채널을 시스템에 등록
            assert notificationManager != null;
            notificationManager.createNotificationChannel(channel);

        } else
            builder.setSmallIcon(R.mipmap.ic_launcher); // Oreo 이하에서 mipmap 사용하지 않으면 Couldn't create icon: StatusBarIcon 에러남

        assert notificationManager != null;
        notificationManager.notify(1234, builder.build()); // 고유숫자로 노티피케이션 동작시킴

    }


    private int getNotificationIcon() {
        boolean useWhiteIcon = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);
        return useWhiteIcon ? R.drawable.icon : R.drawable.icon;
    }

    private void initSp() {
        allist1 = getResources().getStringArray(R.array.bus_list);
        ArrayAdapter<String> ArrayAdapter = new ArrayAdapter<>(this, R.layout.spinner_item, allist1);
        sp_main1.setAdapter(ArrayAdapter);

        //Action after clicking LinearLayout / Spinner;
        ll_hint_spinner1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //By clicking "Select ..." the Spinner is requested;
                sp_main1.performClick();
                //Make LinearLayout invisible
                setLinearVisibility(ll_hint_spinner1, false);
                //Disable LinearLayout
                ll_hint_spinner1.setEnabled(false);
                //After LinearLayout is off, Spinner will function normally;
                sp_main1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        sp_main1.setSelection(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        setLinearVisibility(ll_hint_spinner1, true);
                    }
                });
            }
        });


        allist2 = getResources().getStringArray(R.array.station_list);
        ArrayAdapter<String> ArrayAdapter2 = new ArrayAdapter<>(this, R.layout.spinner_item, allist2);
        sp_main2.setAdapter(ArrayAdapter2);

        //Action after clicking LinearLayout / Spinner;
        ll_hint_spinner2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //By clicking "Select ..." the Spinner is requested;
                sp_main2.performClick();
                //Make LinearLayout invisible
                setLinearVisibility(ll_hint_spinner2, false);
                //Disable LinearLayout
                ll_hint_spinner2.setEnabled(false);
                //After LinearLayout is off, Spinner will function normally;
                sp_main2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        sp_main2.setSelection(position);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        setLinearVisibility(ll_hint_spinner2, true);
                    }
                });
                sp_main2.setSelection(-1);
            }
        });

    }

    //Method to make LinearLayout invisible or visible;
    public void setLinearVisibility(LinearLayout layout, boolean visible) {
        if (visible) {
            layout.setVisibility(View.VISIBLE);
        } else {
            layout.setVisibility(View.INVISIBLE);
        }
    }

    private void initTf() {
        // Load the labels for the model, but only display those that don't start
        // with an underscore.
        String actualLabelFilename = LABEL_FILENAME.split("file:///android_asset/", -1)[1];
        Log.i(LOG_TAG, "Reading labels from: " + actualLabelFilename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open(actualLabelFilename)));
            String line;
            while ((line = br.readLine()) != null) {
                Log.e(LOG_TAG, "lable: " + line);
                labels.add(line);

            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        }

        // Set up an object to smooth recognition results to increase accuracy.
        recognizeCommands =
                new RecognizeCommands(
                        labels,
                        AVERAGE_WINDOW_DURATION_MS,
                        DETECTION_THRESHOLD,
                        SUPPRESSION_MS,
                        MINIMUM_COUNT,
                        MINIMUM_TIME_BETWEEN_SAMPLES_MS);

        String actualModelFilename = MODEL_FILENAME.split("file:///android_asset/", -1)[1];
        try {
            tfLiteModel = loadModelFile(getAssets(), actualModelFilename);
            tfLite = new Interpreter(tfLiteModel, tfLiteOptions);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        tfLite.resizeInput(0, new int[]{RECORDING_LENGTH, 1});
        tfLite.resizeInput(1, new int[]{1});

        // Start the recording and recognition threads.
        requestMicrophonePermission();

    }


    /**
     * Memory-map the model file in Assets.
     */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    public synchronized void stopRecording() {
        if (recordingThread == null) {
            return;
        }
        shouldContinue = false;
        recordingThread = null;
    }

    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // Estimate the buffer size we'll need for this device.
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        record.startRecording();

        Log.v(LOG_TAG, "Start recording");

        // Loop, gathering audio data and copying it to a round-robin buffer.
        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            int maxLength = recordingBuffer.length;
            int newRecordingOffset = recordingOffset + numberRead;
            int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
            int firstCopyLength = numberRead - secondCopyLength;
            // We store off all the data for the recognition thread to access. The ML
            // thread will copy out of this buffer into its own, while holding the
            // lock, so this should be thread safe.
            recordingBufferLock.lock();
            try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
                recordingOffset = newRecordingOffset % maxLength;
            } finally {
                recordingBufferLock.unlock();
            }
        }

        record.stop();
        record.release();
    }

    public synchronized void startRecognition() {
        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    public synchronized void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;
    }

    private void recognize() {

        Log.v(LOG_TAG, "Start recognition");

        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[][] floatInputBuffer = new float[RECORDING_LENGTH][1];
        float[][] outputScores = new float[1][labels.size()];
        int[] sampleRateList = new int[]{SAMPLE_RATE};

        // Loop, grabbing recorded data and running the recognition model on it.
        while (shouldContinueRecognition) {
            long startTime = new Date().getTime();
            // The recording thread places data in this round-robin buffer, so lock to
            // make sure there's no writing happening and then copy it to our own
            // local version.
            recordingBufferLock.lock();
            try {
                int maxLength = recordingBuffer.length;
                int firstCopyLength = maxLength - recordingOffset;
                int secondCopyLength = recordingOffset;
                System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
                System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
            } finally {
                recordingBufferLock.unlock();
            }

            // We need to feed in float values between -1.0f and 1.0f, so divide the
            // signed 16-bit inputs.
            for (int i = 0; i < RECORDING_LENGTH; ++i) {
                floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
            }

            Object[] inputArray = {floatInputBuffer, sampleRateList};
            Map<Integer, Object> outputMap = new HashMap<>();
            outputMap.put(0, outputScores);

            // Run the model.
            tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

            // Use the smoother to figure out if we've had a real recognition event.
            long currentTime = System.currentTimeMillis();
            final RecognizeCommands.RecognitionResult result =
                    recognizeCommands.processLatestResults(outputScores[0], currentTime);

            runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {

                            //   inferenceTimeTextView.setText(lastProcessingTimeMs + " ms"+result.isNewCommand+" "+result.foundCommand);

                            // If we do have a new command, highlight the right list entry.
                            Log.e("lablIndex", result.foundCommand + " " + result.isNewCommand);
                            if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                                int labelIndex = -1;
                                for (int i = 0; i < labels.size(); ++i) {
                                    if (labels.get(i).equals(result.foundCommand)) {
                                        labelIndex = i;
                                    }
                                }
                                Log.e("lablIndex", labelIndex + " ");
                                // 싸일런스와 알수없는 값일경우 포함하기때문에 -2해야함
                                switch (labelIndex - 2) {
                                    case 0:
                                        //다음 정거장은 서남동행동복지센터 입니다.
                                        if (type == 1) {
                                            if (sp_main2.getSelectedItemPosition() == 0) {
                                                setType2();
                                            }
                                        }
                                        break;
                                    case 1:
                                        //이번 정거장은 서남동행동복지센터 입니다.
                                        if (type == 2) {
                                            if (sp_main2.getSelectedItemPosition() == 2) {
                                                setType3();
                                            }
                                        }
                                        break;
                                    case 2:
                                        //다음 정거장은 조선대 입니다.
                                        if (type == 1) {
                                            if (sp_main2.getSelectedItemPosition() == 1) {
                                                setType2();
                                            }
                                        }
                                        break;
                                    case 3:
                                        //이번 정거장은 조선대 입니다.
                                        if (type == 2) {
                                            if (sp_main2.getSelectedItemPosition() == 2) {
                                                setType3();
                                            }
                                        }
                                        break;
                                    case 4:
                                        if (type == 1) {
                                            if (sp_main2.getSelectedItemPosition() == 2) {
                                                setType2();
                                            }
                                        }
                                        //다음 정거장은 살레시오여고 입니다.
                                        break;
                                    case 5:
                                        //이번 정거장은 살레시오여고 입니다.
                                        if (type == 2) {
                                            if (sp_main2.getSelectedItemPosition() == 2) {
                                                setType3();
                                            }
                                        }
                                        break;
                                    case 6:
                                        //감사합니다
                                        if (type == 0) {
                                            setType1();
                                        }
                                        break;
                                    case 7:
                                        //사용할 수 없는 카드입니다
                                        if (type == 0) {
                                            setType1();
                                        } else if (type == 3) {
                                            setType4();
                                        }
                                        break;
                                    case 8:
                                        //카드를 한 장만 대주십시오
                                        if (type == 0) {
                                            setType1();
                                        } else if (type == 3) {
                                            setType4();
                                        }
                                        break;
                                    case 9:
                                        //이미 처리된 카드입니다
                                        if (type == 0) {
                                            setType1();
                                        } else if (type == 3) {
                                            setType4();
                                        }
                                        break;
                                    case 10:
                                        //하차입니다.
                                        if (type == 3) {
                                            setType4();
                                        }
                                        break;

                                }


                            }
                        }
                    });
            try {
                // We don't need to run too frequently, so snooze for a bit.
                Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }


    }


    private void requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[]{android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
            startRecognition();
        }
    }
}