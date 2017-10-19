package com.example.cameratest;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.usb.UsbDevice;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.PowerManager;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.RelativeLayout;


import com.com.example.encoder.MediaAudioEncoder;
import com.com.example.encoder.MediaEncoder;
import com.com.example.encoder.MediaMuxerWrapper;
import com.com.example.encoder.MediaVideoEncoder;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraService {
	private final static String CLASS_LABEL = "RecordActivity";
	private final static String LOG_TAG = CLASS_LABEL;
	private PowerManager.WakeLock mWakeLock;
	private boolean isPreviewOn = false;
	private int sampleAudioRateInHz = CONSTANTS.LOBU_SAMPLEAUDIORATEINHZ;
	private int imageWidth = CONSTANTS.LOBU_IMAGEWIDTH;
	private int imageHeight = CONSTANTS.LOBU_IMAGEHEIGHT;
	private int frameRate = CONSTANTS.LOBU_FRAMERATE;

	/* video data getting thread */
    private final Object mSync = new Object();
    private UVCCamera mUVCCamera = null;
    private int mCurrentCameraType = CONSTANTS.CAMERA_MODE_BACK;
	private Camera cameraDevice;
	private SurfaceView cameraView;
    TimerService timerService;
    public SurfaceHolder mPreviewSurface;

	/*
	 * The number of seconds in the continuous record loop (or 0 to disable
	 * loop).
	 */
    Activity mActivity;
    Context context;
    OnTimerOutListener timerOutListener;
    RecorderYUV mRecorder;

    public interface OnTimerOutListener {
        public void onTimerFresh();
        public void onLog(String log);
    }

	// 初始化
	public CameraService(Activity mActivity, Context context,TimerService timerService) {
		this.mActivity = mActivity;
		this.context = context;
        this.timerService = timerService;
        mRecorder = new RecorderYUV();
	}

    //初始化服务
	public boolean initService(OnTimerOutListener timerOutListener) {
        mUVCCamera = new UVCCamera();
		// 电源控制
		PowerManager pm = (PowerManager) mActivity.getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
		mWakeLock.acquire();
        initLayout();
        this.timerOutListener = timerOutListener;
		return true;
	}

    public void initLayout() {
        /* get size of screen */
        cameraView = (SurfaceView)mActivity.findViewById(R.id.surfaceViewPreview);
        cameraView.getHolder().addCallback(surfaceHolderCallback);
        cameraView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    //---------------------------------------------------------------------------------------
    // initialize ffmpeg_recorder
    //---------------------------------------------------------------------------------------
    private MediaMuxerWrapper mMuxer = null;
    private MediaVideoEncoder mVideoEncoder = null;
    boolean mIsRecording = false;
    private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {
            mIsRecording = true;
        }

        @Override
        public void onStopped(final MediaEncoder encoder) {
            if (encoder instanceof MediaVideoEncoder){
                mIsRecording = false;
            }
        }
    };

    //是否在录制
    public boolean isRecording(){
        return mIsRecording;
    }

    //选择设备进行录像
    public boolean SwitchCamera(int device){
        stopPreview();
        mCurrentCameraType = device;

        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();//得到摄像头的个数

        for(int i = 0; i < cameraCount; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if(mCurrentCameraType == CONSTANTS.CAMERA_MODE_FRONT){
                if(cameraInfo.facing  == Camera.CameraInfo.CAMERA_FACING_FRONT){
                    cameraDevice = Camera.open(i);
                }
            }else{
                if(cameraInfo.facing  == Camera.CameraInfo.CAMERA_FACING_BACK){
                    cameraDevice = Camera.open(i);
                }
            }
        }

        Camera.Parameters camParams = cameraDevice.getParameters();
        List<Camera.Size> sizes = camParams.getSupportedPreviewSizes();
        // Sort the list in ascending order
        Collections.sort(sizes, new Comparator<Camera.Size>() {
            public int compare(final Camera.Size a, final Camera.Size b) {
                return a.width * a.height - b.width * b.height;
            }
        });

        // Pick the first preview size that is equal or bigger, or pick the last (biggest) option if we cannot
        // reach the initial settings of imageWidth/imageHeight.
        for (int i = 0; i < sizes.size(); i++) {
            if ((sizes.get(i).width >= imageWidth && sizes.get(i).height >= imageHeight) || i == sizes.size() - 1) {
                imageWidth = sizes.get(i).width;
                imageHeight = sizes.get(i).height;
                Log.v(LOG_TAG, "Changed to supported resolution: " + imageWidth + "x" + imageHeight);
                break;
            }
        }
        camParams.setPreviewSize(imageWidth, imageHeight);
        Log.v(LOG_TAG, "Setting imageWidth: " + imageWidth + " imageHeight: " + imageHeight + " frameRate: " + frameRate);

        camParams.setPreviewFrameRate(frameRate);
        Log.v(LOG_TAG, "Preview Framerate: " + camParams.getPreviewFrameRate());

        cameraDevice.setParameters(camParams);

        startPreview();
        return true;
    }

    //选择USB摄像头
    public boolean SwitchUSBCamera(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew){
        stopPreview();
        mCurrentCameraType = CONSTANTS.CAMERA_MODE_USB;
        mUVCCamera = new UVCCamera();
        mUVCCamera.open(ctrlBlock);

        List<Size> sizes = mUVCCamera.getSupportedSizeList();
        // Sort the list in ascending order
        Collections.sort(sizes, new Comparator<Size>() {
            public int compare(final Size a, final Size b) {
                return a.width * a.height - b.width * b.height;
            }
        });

        // Pick the first preview size that is equal or bigger, or pick the last (biggest) option if we cannot
        // reach the initial settings of imageWidth/imageHeight.
        for (int i = 0; i < sizes.size(); i++) {
            //timerOutListener.onLog(sizes.get(i).width + ":" + sizes.get(i).height);

            /*
            if ((sizes.get(i).width >= imageWidth && sizes.get(i).height >= imageHeight) || i == sizes.size() - 1) {
                imageWidth = sizes.get(i).width;
                imageHeight = sizes.get(i).height;
                Log.v(LOG_TAG, "Changed to supported resolution: " + imageWidth + "x" + imageHeight);
                break;
            }*/
        }

        try {
           // mUVCCamera.setPreviewSize(UVCCamera.DEFAULT_PREVIEW_WIDTH, UVCCamera.DEFAULT_PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
            mUVCCamera.setPreviewSize(imageWidth,imageHeight,UVCCamera.PIXEL_FORMAT_NV21);
        } catch (final IllegalArgumentException e) {
            try {
                // fallback to YUV mode
                mUVCCamera.setPreviewSize(imageWidth, imageHeight, UVCCamera.PIXEL_FORMAT_NV21);
            } catch (final IllegalArgumentException e1) {
                mUVCCamera.destroy();
                mUVCCamera = null;
            }
        }

        startPreview();
        return true;
    }


    public void startPreview() {
        if(mCurrentCameraType == CONSTANTS.CAMERA_MODE_USB){
            if(mUVCCamera != null){
                if ((mUVCCamera != null) && (mPreviewSurface != null)) {
                    mUVCCamera.setPreviewDisplay(mPreviewSurface.getSurface());
                    mUVCCamera.setFrameCallback(mUsbCameraCallback, UVCCamera.PIXEL_FORMAT_IYUV420);//PIXEL_FORMAT_NV21 PIXEL_FORMAT_YUV420SP PIXEL_FORMAT_IYUV420
                }
                mUVCCamera.startPreview();
            }
        }else{
            if (!isPreviewOn && cameraDevice != null) {
                isPreviewOn = true;
                try {
                    cameraDevice.setPreviewDisplay(mPreviewSurface);
                } catch (IOException e) {
                    e.printStackTrace();
                    cameraDevice.release();
                    cameraDevice = null;
                }
                cameraDevice.setPreviewCallback(mPhoneCameraCallBack);
                cameraDevice.startPreview();
            }
        }

    }

    public void stopPreview() {
        if(mCurrentCameraType == CONSTANTS.CAMERA_MODE_USB){
            if(mUVCCamera != null){
                mUVCCamera.stopPreview();
                //mPreviewSurface.getSurface().release();
                mUVCCamera.close();
                mUVCCamera.destroy();
                mUVCCamera = null;
            }
        }else{
            if (isPreviewOn && cameraDevice != null) {
                isPreviewOn = false;
                cameraDevice.stopPreview();
                //mPreviewSurface.getSurface().release();
                try {
                    cameraDevice.setPreviewDisplay(null);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                cameraDevice.setPreviewCallback(null);
                cameraDevice.release();
                cameraDevice = null;
            }
        }

    }

    private static void YUV420SP2YUV420(byte[] yuv420sp, byte[] yuv420, int width, int height)
    {
        if (yuv420sp == null ||yuv420 == null)
            return;
        int framesize = width*height;
        int i = 0, j = 0;
//copy y
        for (i = 0; i < framesize; i++)
        {
            yuv420[i] = yuv420sp[i];
        }
        i = 0;
        for (j = 0; j < framesize/2; j+=2)
        {
            yuv420[i + framesize*5/4] = yuv420sp[j+framesize];
            i++;
        }
        i = 0;
        for(j = 1; j < framesize/2;j+=2)
        {
            yuv420[i+framesize] = yuv420sp[j+framesize];
            i++;
        }
    }

    //USB摄像头的回调数据
    public final IFrameCallback mUsbCameraCallback = new IFrameCallback(){
        @Override
        public void onFrame(ByteBuffer frame) {
            if (mVideoEncoder != null) {
                mVideoEncoder.frameAvailableSoon();
                mVideoEncoder.encode(frame);
                timerService.getTimer();
                timerOutListener.onTimerFresh();

                frame.clear();
                byte[] bytes420sp = new byte[frame.capacity()];
                //byte[] bytes420 = new byte[frame.capacity()];
                frame.get(bytes420sp, 0, bytes420sp.length);
                //YUV420SP2YUV420(bytes420sp, bytes420, 640, 480);
                mRecorder.saveBuffer(bytes420sp);
                //timerOutListener.onLog("GOOD");
            }
        }
    };

    public class RecorderYUV{
        FileOutputStream outputStream = null;
        File file = null;
        public void Open(Context context){
            try {
                file = new File("/mnt/sdcard/test6.yuv");
                outputStream = new FileOutputStream(file,true);
                //outputStream = context.openFileOutput("/mnt/sdcard/test.yuv", Context.MODE_PRIVATE|Context.MODE_WORLD_WRITEABLE|Context.MODE_APPEND);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void Close(){
            if(outputStream == null)
                return;

            try {
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        public void saveBuffer(byte[] frame){
            try {
                outputStream.write(frame);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    //摄像头的录像
    public final PreviewCallback mPhoneCameraCallBack = new PreviewCallback(){
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (mVideoEncoder != null) {
                mVideoEncoder.frameAvailableSoon();
                mVideoEncoder.encode(ByteBuffer.wrap(data));


                mRecorder.saveBuffer(data);
                timerService.getTimer();
                timerOutListener.onTimerFresh();
            }
        }
    };

    //关闭
    public void CloseUSBCamera(){
        stopRecording();
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.close();
            }
        }
    }

    //开始录制
    public void startRecording(){
        if (mMuxer != null){
            return;
        }
        try {
            mMuxer = new MediaMuxerWrapper("."+CONSTANTS.ffmpeg_format);
            mVideoEncoder = new MediaVideoEncoder(mMuxer, mMediaEncoderListener);
            new MediaAudioEncoder(mMuxer, mMediaEncoderListener);
            mMuxer.prepare();
            mMuxer.startRecording();
            mRecorder.Open(context);
        } catch (IOException e) {
            e.printStackTrace();
        }
        timerService.start();
        timerOutListener.onLog("ok");
    }

    //停止录制
    public void stopRecording() {
        if (mMuxer != null) {
            mRecorder.Close();
            mMuxer.stopRecording();
            mMuxer = null;
        }
        mVideoEncoder = null;
        timerService.stop();
    }

    //---------------------------------------------
    // camera thread, gets and encodes video data
    //---------------------------------------------
    public SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback(){
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            mPreviewSurface = holder;
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    };

	public boolean destoryService() {
        stopPreview();
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
		return false;
	}
}
