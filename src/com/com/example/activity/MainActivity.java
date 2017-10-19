package com.com.example.activity;

import android.app.Activity;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.example.cameratest.CONSTANTS;
import com.example.cameratest.CameraService;
import com.example.cameratest.R;
import com.example.cameratest.TimerService;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.USBMonitor;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ShortBuffer;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity implements View.OnClickListener,
        CameraDialog.CameraDialogParent,
        TextureView.SurfaceTextureListener,
        MediaPlayer.OnCompletionListener,
        CameraService.OnTimerOutListener {
    private static final boolean DEBUG = true;
    private static final String TAG = "MainActivity";

    TimerService timerService;
    CameraService camerService;
    private USBMonitor mUSBMonitor;
    private final Object mSync = new Object();
    RadioButton mFrontCamera,mBackCamera,mUSBCamera;

    private MediaPlayer mediaPlayer;
    private TextureView surfaceView;
    public TextView textView;
    //add by flex --audio recorder and player
    private AudioRecordRunnable audioRecordRunnable;
    private AudioRecord audioRecord;
    private Thread audioThread;
    volatile boolean runAudioThread = false;
    //end add


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ((Button)findViewById(R.id.buttonStart)).setOnClickListener(this);
        ((Button)findViewById(R.id.buttonStop)).setOnClickListener(this);
        ((Button)findViewById(R.id.buttonPlay)).setOnClickListener(this);
        ((Button)findViewById(R.id.buttonStopPlay)).setOnClickListener(this);

        mFrontCamera = (RadioButton)findViewById(R.id.radioButtonfront);
        mBackCamera = (RadioButton)findViewById(R.id.radioButtonback);
        mUSBCamera = (RadioButton)findViewById(R.id.radioButtonUSB);
        mFrontCamera.setOnClickListener(this);
        mBackCamera.setOnClickListener(this);
        mUSBCamera.setOnClickListener(this);

        timerService = new TimerService();
        camerService = new CameraService(this,getApplicationContext(),timerService);
        mUSBMonitor = new USBMonitor(this, mOnDeviceConnectListener);

        new Thread(){
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        camerService.initService(MainActivity.this);
                        //mFrontCamera.setChecked(true);
                        //camerService.SwitchCamera(CONSTANTS.CAMERA_MODE_FRONT);
                    }
                });
            }
        }.start();

        //初始化播放内容
        surfaceView = (TextureView) findViewById(R.id.surfaceViewplay);
        surfaceView.setSurfaceTextureListener(this);
        surfaceView.setOnClickListener(this);
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnCompletionListener(this);

        textView = (TextView)findViewById(R.id.textViewTime);
        ((Button)findViewById(R.id.buttonStart)).setVisibility(View.VISIBLE);
        ((Button)findViewById(R.id.buttonStop)).setVisibility(View.VISIBLE);

        //add by flex --audio recorder and player
        ((Button)findViewById(R.id.btn_playwav)).setOnClickListener(this);
        ((Button)findViewById(R.id.btn_recorderwav)).setOnClickListener(this);
        audioSetting();
        //end add
    }

    //处理消息
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if(msg.arg1 == 100){
                textView.setText((String)msg.obj);
            }else{
                textView.setText(timerService.getTimeStr());
            }
        }
    };

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonStart:
                if(!camerService.isRecording()){
                    camerService.startRecording();
                    ((Button)findViewById(R.id.buttonStart)).setVisibility(View.INVISIBLE);
                    ((Button)findViewById(R.id.buttonStop)).setVisibility(View.VISIBLE);
                }
                break;
            case R.id.buttonStop:
                if(camerService.isRecording()){
                    camerService.stopRecording();
                    ((Button)findViewById(R.id.buttonStart)).setVisibility(View.VISIBLE);
                    ((Button)findViewById(R.id.buttonStop)).setVisibility(View.INVISIBLE);
                }
                break;
            case R.id.buttonStopPlay:{
                if(mediaPlayer.isPlaying()){
                    mediaPlayer.stop();
                }
                break;
            }
            case R.id.buttonPlay:{
                if(!mediaPlayer.isPlaying()){
                    prepare();
                    mediaPlayer.start();
                }
                break;
            }
            case R.id.radioButtonfront:{
                if(camerService.isRecording())
                    break;

                mFrontCamera.setChecked(true);
                mBackCamera.setChecked(false);
                mUSBCamera.setChecked(false);
                camerService.SwitchCamera(CONSTANTS.CAMERA_MODE_FRONT);
                ((Button)findViewById(R.id.buttonStart)).setVisibility(View.VISIBLE);
                ((Button)findViewById(R.id.buttonStop)).setVisibility(View.VISIBLE);
                break;
            }
            case R.id.radioButtonback:{
                if(camerService.isRecording())
                    break;
                mFrontCamera.setChecked(false);
                mBackCamera.setChecked(true);
                mUSBCamera.setChecked(false);
                camerService.SwitchCamera(CONSTANTS.CAMERA_MODE_BACK);
                ((Button)findViewById(R.id.buttonStart)).setVisibility(View.VISIBLE);
                ((Button)findViewById(R.id.buttonStop)).setVisibility(View.VISIBLE);
                break;
            }
            case R.id.radioButtonUSB:{
                if(camerService.isRecording())
                    break;

                mFrontCamera.setChecked(false);
                mBackCamera.setChecked(false);
                mUSBCamera.setChecked(true);
                CameraDialog.showDialog(MainActivity.this);
                ((Button)findViewById(R.id.buttonStart)).setVisibility(View.VISIBLE);
                ((Button)findViewById(R.id.buttonStop)).setVisibility(View.VISIBLE);
                break;
            }
            //add by flex --audio recorder and player
            case R.id.btn_playwav:{
                audioSetting();
                playAudio();
                break;
            }
            case  R.id.btn_recorderwav:{
                //录音
                if(!runAudioThread){
                    audioRecordRunnable = new AudioRecordRunnable();
                    audioThread = new Thread(audioRecordRunnable);
                    runAudioThread = true;
                    audioThread.start();
                    ((Button)findViewById(R.id.btn_recorderwav)).setText("停止录音");
                }else{
                    runAudioThread = false;
                    try {
                        audioThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    audioThread = null;
                    ((Button)findViewById(R.id.btn_recorderwav)).setText("开始录音");
                }
                //停止
                break;
            }
            //end add
        }
    }

    //开启外放声音
    public void audioSetting(){
        AudioManager audioManager =	((AudioManager) this.getSystemService(this.AUDIO_SERVICE));
        boolean isWiredHeadsetOn = audioManager.isWiredHeadsetOn();
        isWiredHeadsetOn = true;
        audioManager.setMode(isWiredHeadsetOn ?	AudioManager.MODE_IN_CALL : AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(!isWiredHeadsetOn);
    }

    public void playAudio(){
        int samplerate = 44100;
        FileInputStream inputStream = null;
        File file = null;
        try {
            file = new File("/mnt/sdcard/testsound.pcm");
            inputStream = new FileInputStream(file);
        } catch (Exception e) {
            e.printStackTrace();
        }


        // 声音文件一秒钟buffer的大小
        int mAudioMinBufSize = AudioTrack.getMinBufferSize(
                samplerate,
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, // 指定在流的类型
                // STREAM_ALARM：警告声
                // STREAM_MUSCI：音乐声，例如music等
                // STREAM_RING：铃声
                // STREAM_SYSTEM：系统声音
                // STREAM_VOCIE_CALL：电话声音
                samplerate,// 设置音频数据的采样率
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,// 设置输出声道为双声道立体声
                AudioFormat.ENCODING_PCM_16BIT,// 设置音频数据块是8位还是16位
                mAudioMinBufSize,
                AudioTrack.MODE_STREAM);// 设置模式类型，在这里设置为流类型
        mAudioTrack.play();
        byte[] buffer = new byte[4096];
        while(true){
            int nread = 0;
            try {
                nread = inputStream.read(buffer,0,4096);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(nread <= 0)
                break;

            mAudioTrack.write(buffer, 0, nread);
        }
        //int count;
       // while(true){
            //mAudioTrack.write(buffer, 0, 4096);
       // }
        if(inputStream != null){
            try {
                inputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mAudioTrack.stop();
        mAudioTrack.release();
    }

    public static byte[] getBytes(short data)
    {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) (data & 0xff);
        bytes[1] = (byte) ((data & 0xff00) >> 8);
        return bytes;
    }

    //---------------------------------------------
    // audio thread, gets and encodes audio data
    //---------------------------------------------
    class AudioRecordRunnable implements Runnable {
        @Override
        public void run() {
            int sampleAudioRateInHz= 44100;
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

            DataOutputStream dos = null;
            File file = null;
            try {
                file = new File("/mnt/sdcard/testsound.pcm");
                dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file,false)));
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Audio
            int bufferSize = 0;
            ShortBuffer audioData;
            int bufferReadResult;
            bufferSize = AudioRecord.getMinBufferSize(
                    sampleAudioRateInHz,AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleAudioRateInHz,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);

            audioData = ShortBuffer.allocate(bufferSize);
            audioRecord.startRecording();

            /* ffmpeg_audio encoding loop */
            while (runAudioThread) {
                bufferReadResult = audioRecord.read(audioData.array(), 0, audioData.capacity());
                if(bufferReadResult<0)continue;
                audioData.limit(bufferReadResult);
                if (bufferReadResult > 0) {
                    //audioData
                    try {
                        for(int i = 0; i < audioData.limit(); i++){
                            dos.writeShort(audioData.get(i));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }

            /* encoding finish, release recorder */
            if(dos != null){
                try {
                    dos.flush();
                    dos.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Toast.makeText(MainActivity.this, "onResume", Toast.LENGTH_LONG).show();
        mUSBMonitor.register();
    }

    @Override
    public void onPause() {
        Toast.makeText(MainActivity.this, "onPause", Toast.LENGTH_LONG).show();
        mUSBMonitor.unregister();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mUSBMonitor.unregister();
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        super.onDestroy();
    }

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onAttach:");
            Toast.makeText(MainActivity.this, "USB_DEVICE_ATTACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            if (DEBUG) Log.v(TAG, "onConnect:");
            Toast.makeText(MainActivity.this, "onConnect", Toast.LENGTH_LONG).show();
            camerService.SwitchUSBCamera(device,ctrlBlock,createNew);
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            if (DEBUG) Log.v(TAG, "onDisconnect:");
            camerService.CloseUSBCamera();
        }

        @Override
        public void onDettach(final UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDettach:");
            Toast.makeText(MainActivity.this, "USB_DEVICE_DETACHED", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCancel() {
        }
    };

    /**
     * to access from CameraDialog
     * @return
     */
    @Override
    public USBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    private void prepare() {
        try {
            mediaPlayer.reset();
            //mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            // 设置需要播放的视频
            mediaPlayer.setDataSource(CONSTANTS.ffmpeg_link);
            // 把视频画面输出到Surface
            //mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.seekTo(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {

    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mediaPlayer.setSurface(new Surface(surface));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onTimerFresh() {
        handler.sendEmptyMessage(0);
    }

    @Override
    public void onLog(String log) {
        Message msg = new Message();
        msg.arg1 = 100;
        msg.obj = log;
        handler.sendMessage(msg);
    }
}
