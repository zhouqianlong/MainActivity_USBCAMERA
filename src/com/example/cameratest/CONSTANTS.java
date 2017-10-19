package com.example.cameratest;

import android.os.Environment;

public class CONSTANTS {
	//服务模块名字
	public final static String ffmpeg_format = "mp4";
	public final static String ffmpeg_path = "/mnt/sdcard/";
	public final static String ffmpeg_filename = "stream." + ffmpeg_format;
	public final static String ffmpeg_link = ffmpeg_path + "stream." + ffmpeg_format;
	public final static int CAMERA_MODE_FRONT=0;
	public final static int CAMERA_MODE_BACK=1;
	public final static int CAMERA_MODE_USB=2;

	//录播新--------------------------------------------
	public final static int LOBU_SAMPLEAUDIORATEINHZ = 44100;
	public final static int LOBU_IMAGEWIDTH = 640;//(1280);
	public final static int LOBU_IMAGEHEIGHT=480;//(720);
	public final static int LOBU_FRAMERATE=30;
}
