package com.example.cameratest;

import java.text.SimpleDateFormat;

public class TimerService {
    //开始时间
    long startTime = 0;
    //结束时间
    long stopTime = 0;
    //计数器
    long frameTime = 0;
    //暂停时间
    long pauseTime = 0;
    //上次的时间
    long prevTime = 0;

    //状态，开始、结束、暂停
    public enum TimerStatus {
        START, STOP,PAUSE
    }
    TimerStatus status = TimerStatus.STOP;
    // 初始化
	public TimerService() {
	}

    //开始
    public void start(){
        startTime = System.currentTimeMillis();
        prevTime = startTime;
        status = TimerStatus.START;
        frameTime = 0;
    }

    //停止
    public void stop(){
        stopTime = System.currentTimeMillis();
        status = TimerStatus.STOP;
    }

    //暂停
    public void pause(){
        pauseTime = System.currentTimeMillis();
        status = TimerStatus.PAUSE;
    }

    //暂停
    public void startpause(){
        status = TimerStatus.START;
        startTime = System.currentTimeMillis();
        prevTime = startTime;
    }

    //获取计数
    public long getTimer(){
        long nowTimer = System.currentTimeMillis();
        frameTime += 1000 * (nowTimer - prevTime);
        prevTime = nowTimer;
        return  frameTime;
    }

    public long getCurrent(){
        return frameTime;
    }

    public static String unitFormat(int i) {
        String retStr = null;
        if (i >= 0 && i < 10)
            retStr = "0" + Integer.toString(i);
        else
            retStr = "" + i;
        return retStr;
    }

    public static String secToTime(int time) {
        String timeStr = null;
        int hour = 0;
        int minute = 0;
        int second = 0;
        if (time <= 0)
            return "00:00";
        else {
            minute = time / 60;
            if (minute < 60) {
                second = time % 60;
                timeStr = unitFormat(minute) + ":" + unitFormat(second);
            } else {
                hour = minute / 60;
                if (hour > 99)
                    return "99:59:59";
                minute = minute % 60;
                second = time - hour * 3600 - minute * 60;
                timeStr = unitFormat(hour) + ":" + unitFormat(minute) + ":" + unitFormat(second);
            }
        }
        return timeStr;
    }

    public String getTimeStr(){
        long n = (frameTime/1000)/1000;
        //SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");//初始化Formatter的转换格式。
        String hms = secToTime((int)n);// formatter.format(n);
        return hms;
    }


}
