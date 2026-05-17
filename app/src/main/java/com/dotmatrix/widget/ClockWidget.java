package com.dotmatrix.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Renders the clock to a transparent bitmap for RemoteViews. Minute updates use
 * {@link AlarmManager} with an explicit {@link PendingIntent} to this receiver
 * so updates continue after the process is killed (TIME_TICK only works while
 * a dynamically registered receiver is alive, which OEMs often tear down).
 */
public class ClockWidget extends AppWidgetProvider {

    static final String ACTION_MINUTE_TICK = "com.dotmatrix.widget.ACTION_MINUTE_TICK";
    private static final int REQ_MINUTE_ALARM = 1001;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_MINUTE_TICK.equals(intent.getAction())) {
            Context app = context.getApplicationContext();
            AppWidgetManager mgr = AppWidgetManager.getInstance(app);
            ComponentName widget = new ComponentName(app, ClockWidget.class);
            int[] ids = mgr.getAppWidgetIds(widget);
            if (ids.length == 0) {
                cancelMinuteAlarm(app);
                return;
            }
            for (int id : ids) {
                updateAppWidget(app, mgr, id);
            }
            scheduleNextMinuteAlarm(app);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Context app = context.getApplicationContext();
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(app, appWidgetManager, appWidgetId);
        }
        scheduleNextMinuteAlarm(app);
    }

    @Override
    public void onEnabled(Context context) {
        Context app = context.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(app);
        ComponentName widget = new ComponentName(app, ClockWidget.class);
        for (int id : mgr.getAppWidgetIds(widget)) {
            updateAppWidget(app, mgr, id);
        }
        scheduleNextMinuteAlarm(app);
    }

    @Override
    public void onDisabled(Context context) {
        cancelMinuteAlarm(context.getApplicationContext());
    }

    static void scheduleNextMinuteAlarm(Context appContext) {
        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(appContext, ClockWidget.class);
        intent.setAction(ACTION_MINUTE_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(
                appContext,
                REQ_MINUTE_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAt = nextMinuteWallMillis();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    private static void cancelMinuteAlarm(Context appContext) {
        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(appContext, ClockWidget.class);
        intent.setAction(ACTION_MINUTE_TICK);
        PendingIntent pi = PendingIntent.getBroadcast(
                appContext,
                REQ_MINUTE_ALARM,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }

    private static long nextMinuteWallMillis() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 1);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.clock_widget_layout);
        views.setInt(R.id.widget_root, "setBackgroundResource", 0);
        views.setInt(R.id.widget_image, "setBackgroundResource", 0);

        Typeface typeface;
        try {
            typeface = Typeface.createFromAsset(context.getAssets(), "ndot_55.ttf");
        } catch (RuntimeException e) {
            typeface = Typeface.MONOSPACE;
        }

        Bitmap bitmap = buildClockBitmap(context, typeface);
        views.setImageViewBitmap(R.id.widget_image, bitmap);

        Intent clockIntent = context.getPackageManager()
                .getLaunchIntentForPackage("com.google.android.deskclock");
        if (clockIntent == null) {
            clockIntent = new Intent(Intent.ACTION_MAIN);
            clockIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            clockIntent.setPackage("com.google.android.deskclock");
        }
        clockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                clockIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        views.setOnClickPendingIntent(R.id.widget_image, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static Bitmap buildClockBitmap(Context context, Typeface typeface) {
        final int width = 2000;
        final int height = 680;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.TRANSPARENT);

        Canvas canvas = new Canvas(bitmap);

        Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        timePaint.setTypeface(typeface);
        timePaint.setTextSize(420f);
        timePaint.setColor(Color.WHITE);
        timePaint.setTextAlign(Paint.Align.CENTER);
        // Widen horizontal dot pitch toward vertical grid; spacing is in ems of this paint's text size
        timePaint.setLetterSpacing(0.16f);
        timePaint.setTextScaleX(1.14f);

        Paint datePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        datePaint.setTypeface(typeface);
        datePaint.setTextSize(108f);
        datePaint.setColor(Color.argb(210, 255, 255, 255));
        datePaint.setTextAlign(Paint.Align.CENTER);
        datePaint.setLetterSpacing(0.2f);
        datePaint.setTextScaleX(1.1f);

        Paint.FontMetrics timeFm = new Paint.FontMetrics();
        timePaint.getFontMetrics(timeFm);
        Paint.FontMetrics dateFm = new Paint.FontMetrics();
        datePaint.getFontMetrics(dateFm);

        float timeLineHeight = timeFm.descent - timeFm.ascent;
        float dateLineHeight = dateFm.descent - dateFm.ascent;
        // Inter-line gap in the same module ballpark as the font dot step (ties the two lines together visually)
        float interLineGap = timePaint.getTextSize() * 0.22f;
        float blockHeight = timeLineHeight + interLineGap + dateLineHeight;
        float blockTop = (height - blockHeight) / 2f;
        float timeBaseline = blockTop - timeFm.ascent;
        float dateBaseline = timeBaseline + timeFm.descent + interLineGap - dateFm.ascent;

        Locale locale = Locale.getDefault();
        Date now = new Date();
        String timeText = new SimpleDateFormat("hh:mm", locale).format(now);
        String dateText = new SimpleDateFormat("EEE, MMM d", locale).format(now);

        canvas.drawText(timeText, width / 2f, timeBaseline, timePaint);
        canvas.drawText(dateText, width / 2f, dateBaseline, datePaint);

        return bitmap;
    }
}
