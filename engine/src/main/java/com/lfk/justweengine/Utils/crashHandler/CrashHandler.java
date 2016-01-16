package com.lfk.justweengine.Utils.crashHandler;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by liufengkai on 16/1/16.
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
    private static final String TAG = "CrashHandler";

    private Application context;

    private HashMap<String, String> info;

    private DateFormat formatter;

    private static CrashHandler instance;

    private Thread.UncaughtExceptionHandler uncaughtExceptionHandler;

    private Class<?> Activity;

    private String log;

    /**
     * 获取单例
     *
     * @return
     */
    public static CrashHandler getInstance() {
        if (instance == null) {
            instance = new CrashHandler();
        }
        return instance;
    }

    public void setRestartActivity(Class<?> activity) {
        this.Activity = activity;
    }

    /**
     * init
     *
     * @param context
     */
    public void init(Application context) {
        this.context = context;
        uncaughtExceptionHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        info = new HashMap<>();
        formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        CrashHandlerDefault.init();
    }


    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        handleException(ex);
        uncaughtExceptionHandler
                .uncaughtException(thread, ex);
    }

    private boolean handleException(Throwable throwable) {
        if (throwable == null)
            return false;
        collectDeviceInfo(context);
        log = writeCrashInfoToFile(throwable);
        restart(Activity);
        return true;
    }

    public void collectDeviceInfo(Context ctx) {
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(),
                    PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                String versionName = pi.versionName == null ? "null"
                        : pi.versionName;
                String versionCode = pi.versionCode + "";
                info.put("versionName", versionName);
                info.put("versionCode", versionCode);
                info.put("crashTime", formatter.format(new Date()));
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "an error occurred when collect package info", e);
        }
        Field[] fields = Build.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                info.put(field.getName(), field.get(null).toString());
                Log.d(TAG, field.getName() + " : " + field.get(null));
            } catch (Exception e) {
                Log.e(TAG, "an error occurred when collect crash info", e);
            }
        }
    }

    private String writeCrashInfoToFile(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        sb.append("crash log by JustWeEngine \n");
        for (Map.Entry<String, String> entry : info.entrySet()) {
            sb.append(entry.getKey())
                    .append("-->")
                    .append(entry.getValue())
                    .append("\n");
        }

        Writer writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        ex.printStackTrace(printWriter);
        Throwable cause = ex.getCause();
        while (cause != null) {
            cause.printStackTrace(printWriter);
            cause = cause.getCause();
        }
        printWriter.close();
        String result = writer.toString();
        sb.append(result);
        writeLog(sb.toString());
        return log;
    }

    private void writeLog(String log) {
        File file = new File(CrashHandlerDefault.Log_Default_Path
                + "/" + formatter.format(new Date()) + ".log");
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            byte[] bytes = log.getBytes();
            fileOutputStream.write(bytes);
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void restart(Class<?> activity) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.e(TAG, "error : ", e);
        }
        Intent intent = new Intent(context.getApplicationContext(), activity);
        PendingIntent restartIntent = PendingIntent.getActivity(
                context.getApplicationContext(), 0, intent,
                Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // 使用PendingIntent重启
        AlarmManager mgr =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 1000,
                restartIntent);
    }
}
