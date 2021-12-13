package cn.thinkingdata.android;

import static cn.thinkingdata.android.utils.TDConstants.KEY_BACKGROUND_DURATION;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import cn.thinkingdata.android.utils.ITime;
import cn.thinkingdata.android.utils.TDConstants;
import cn.thinkingdata.android.utils.TDUtils;
import cn.thinkingdata.android.utils.PropertyUtils;
import cn.thinkingdata.android.utils.TDLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;


@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
class ThinkingDataActivityLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "ThinkingAnalytics.ThinkingDataActivityLifecycleCallbacks";
    private boolean resumeFromBackground = false;
    private final Object mActivityLifecycleCallbacksLock = new Object();
    private final ThinkingAnalyticsSDK mThinkingDataInstance;
    private volatile Boolean isLaunch =  true;
    private EventTimer startTimer;
    private WeakReference<Activity> mCurrentActivity;
    private final List<WeakReference<Activity>> mStartedActivityList = new ArrayList<>();

    ThinkingDataActivityLifecycleCallbacks(ThinkingAnalyticsSDK instance, String mainProcessName) {
        this.mThinkingDataInstance = instance;
    }
    public Activity currentActivity()
    {
        if(mCurrentActivity != null)
        {
            return  mCurrentActivity.get();
        }
        return  null;
    }
    @Override
    public void onActivityCreated(Activity activity, Bundle bundle) {
        TDLog.i(TAG,"onActivityCreated");
        mCurrentActivity = new WeakReference<>(activity);

    }

    private boolean notStartedActivity(Activity activity, boolean remove) {
        synchronized (mActivityLifecycleCallbacksLock) {
            Iterator<WeakReference<Activity>> it = mStartedActivityList.iterator();
            while (it.hasNext()) {
                WeakReference<Activity> current = it.next();
                if (current.get() == activity) {
                    if (remove) it.remove();
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        TDLog.i(TAG,"onActivityStarted");
        mCurrentActivity = new WeakReference<>(activity);
        try {
            synchronized (mActivityLifecycleCallbacksLock) {
                if (mStartedActivityList.size() == 0) {

                    trackAppStart(activity, null);
                }
                if (notStartedActivity(activity, false)) {
                    mStartedActivityList.add(new WeakReference<>(activity));
                } else {
                    TDLog.w(TAG, "Unexpected state. The activity might not be stopped correctly: " + activity);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void trackAppStart(Activity activity, ITime time) {
        if (isLaunch||resumeFromBackground) {
            isLaunch =false;
            if (mThinkingDataInstance.isAutoTrackEnabled()) {
                try {
                    if (!mThinkingDataInstance.isAutoTrackEventTypeIgnored(ThinkingAnalyticsSDK.AutoTrackEventType.APP_START)) {
                        JSONObject properties = new JSONObject();
                        properties.put(TDConstants.KEY_RESUME_FROM_BACKGROUND, resumeFromBackground);
                        //to-do
                        if (!TDPresetProperties.disableList.contains(TDConstants.KEY_START_REASON)) {
                            properties.put(TDConstants.KEY_START_REASON, getStartReason());
                        }
                        TDUtils.getScreenNameAndTitleFromActivity(properties, activity);

                        if(startTimer != null)
                        {
                            double duration = Double.parseDouble(startTimer.duration());
                            //to-do
                            properties.put(KEY_BACKGROUND_DURATION, duration);
                        }else {
                            properties.put(KEY_BACKGROUND_DURATION, 0);
                        }
                        if (null == time) {
                            mThinkingDataInstance.autoTrack(TDConstants.APP_START_EVENT_NAME, properties);

                        } else {
                            if (!mThinkingDataInstance.hasDisabled()) {
                                // track APP_START with cached time and properties.
                                JSONObject finalProperties = mThinkingDataInstance.getAutoTrackStartProperties();
                                TDUtils.mergeJSONObject(properties, finalProperties, mThinkingDataInstance.mConfig.getDefaultTimeZone());
                                DataDescription dataDescription = new DataDescription(mThinkingDataInstance, TDConstants.DataType.TRACK, finalProperties, time);
                                dataDescription.eventName = TDConstants.APP_START_EVENT_NAME;
                                mThinkingDataInstance.trackInternal(dataDescription);
                            }
                        }
                    }

                    if (time == null && !mThinkingDataInstance.isAutoTrackEventTypeIgnored(ThinkingAnalyticsSDK.AutoTrackEventType.APP_END)) {
                        mThinkingDataInstance.timeEvent(TDConstants.APP_END_EVENT_NAME);
                    }
                } catch (Exception e) {
                    TDLog.i(TAG, e);
                }
            }
            try {
                mThinkingDataInstance.appBecomeActive();
                startTimer = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {

        synchronized (mActivityLifecycleCallbacksLock) {
            if (notStartedActivity(activity, false)) {
                TDLog.i(TAG, "onActivityResumed: the SDK was initialized after the onActivityStart of " + activity);
                mStartedActivityList.add(new WeakReference<>(activity));
                if (mStartedActivityList.size() == 1) {
                    trackAppStart(activity, mThinkingDataInstance.getAutoTrackStartTime());
                    mThinkingDataInstance.flush();
                    isLaunch = false;
                }
            }
        }

        try {
            boolean mShowAutoTrack = true;
            if (mThinkingDataInstance.isActivityAutoTrackAppViewScreenIgnored(activity.getClass())) {
                mShowAutoTrack = false;
            }

            if (mThinkingDataInstance.isAutoTrackEnabled() && mShowAutoTrack && !mThinkingDataInstance.isAutoTrackEventTypeIgnored(ThinkingAnalyticsSDK.AutoTrackEventType.APP_VIEW_SCREEN)) {
                try {
                    JSONObject properties = new JSONObject();
                    properties.put(TDConstants.SCREEN_NAME, activity.getClass().getCanonicalName());
                    TDUtils.getScreenNameAndTitleFromActivity(properties, activity);

                    if (activity instanceof ScreenAutoTracker) {
                        ScreenAutoTracker screenAutoTracker = (ScreenAutoTracker) activity;

                        String screenUrl = screenAutoTracker.getScreenUrl();
                        JSONObject otherProperties = screenAutoTracker.getTrackProperties();
                        if (otherProperties != null && PropertyUtils.checkProperty(otherProperties)) {
                            TDUtils.mergeJSONObject(otherProperties, properties, mThinkingDataInstance.mConfig.getDefaultTimeZone());
                        } else {
                            TDLog.d(TAG, "invalid properties: " + otherProperties);
                        }
                        mThinkingDataInstance.trackViewScreenInternal(screenUrl, properties);
                    } else {
                        ThinkingDataAutoTrackAppViewScreenUrl autoTrackAppViewScreenUrl = activity.getClass().getAnnotation(ThinkingDataAutoTrackAppViewScreenUrl.class);
                        if (autoTrackAppViewScreenUrl != null && (TextUtils.isEmpty(autoTrackAppViewScreenUrl.appId()) ||
                                        mThinkingDataInstance.getToken().equals(autoTrackAppViewScreenUrl.appId()))) {
                            String screenUrl = autoTrackAppViewScreenUrl.url();
                            if (TextUtils.isEmpty(screenUrl)) {
                                screenUrl = activity.getClass().getCanonicalName();
                            }
                            mThinkingDataInstance.trackViewScreenInternal(screenUrl, properties);
                        } else {
                            mThinkingDataInstance.autoTrack(TDConstants.APP_VIEW_EVENT_NAME, properties);
                        }
                    }
                } catch (Exception e) {
                    TDLog.i(TAG, e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        synchronized (mActivityLifecycleCallbacksLock) {
            if (notStartedActivity(activity, false)) {
                TDLog.i(TAG, "onActivityPaused: the SDK was initialized after the onActivityStart of " + activity);
                mStartedActivityList.add(new WeakReference<>(activity));
                if (mStartedActivityList.size() == 1) {
                    trackAppStart(activity, mThinkingDataInstance.getAutoTrackStartTime());
                    mThinkingDataInstance.flush();
                    isLaunch = false;
                }
            }
        }
    }


    void onAppStartEventEnabled() {
        synchronized (mActivityLifecycleCallbacksLock) {
            if (isLaunch) {
                if (mThinkingDataInstance.isAutoTrackEnabled()) {
                    try {
                        if (!mThinkingDataInstance.isAutoTrackEventTypeIgnored(ThinkingAnalyticsSDK.AutoTrackEventType.APP_START)) {
                            TimerTask task = new TimerTask() {
                                @Override
                                public void run() {
                                    if(isLaunch)
                                    {   isLaunch = false;
                                        JSONObject properties = new JSONObject();
                                        try {
                                            properties.put(TDConstants.KEY_RESUME_FROM_BACKGROUND, resumeFromBackground);
                                            //to-do
                                            if (!TDPresetProperties.disableList.contains(TDConstants.KEY_START_REASON)) {
                                                properties.put(TDConstants.KEY_START_REASON, getStartReason());
                                            }
                                            properties.put(KEY_BACKGROUND_DURATION, 0);
                                        } catch (JSONException exception) {
                                            exception.printStackTrace();
                                        }finally {
                                            mThinkingDataInstance.autoTrack(TDConstants.APP_START_EVENT_NAME, properties);
                                            mThinkingDataInstance.flush();
                                        };
                                    }
                                }
                            };
                            Timer timer = new Timer();
                            timer.schedule(task,100);//100ms后执行TimeTask的run方法

                        }
                    } catch (Exception e) {
                        TDLog.i(TAG, e);
                    }
                }

            }
        }
    }

    public static Object wrap(Object o) {
        if (o == null) {
            return JSONObject.NULL;
        }
        if (o instanceof JSONArray || o instanceof JSONObject) {
            return o;
        }

        if (o.equals(JSONObject.NULL)) {
            return o;
        }
        try {
            if (o instanceof Collection) {
                return new JSONArray((Collection) o);
            } else if (o.getClass().isArray()) {
                return toJSONArray(o);
            }
            if (o instanceof Map) {
                return new JSONObject((Map) o);
            }
            if (o instanceof Boolean ||
                    o instanceof Byte ||
                    o instanceof Character ||
                    o instanceof Double ||
                    o instanceof Float ||
                    o instanceof Integer ||
                    o instanceof Long ||
                    o instanceof Short ||
                    o instanceof String) {
                return o;
            }
            if (o.getClass().getPackage().getName().startsWith("java.")) {
                return o.toString();
            }
        } catch (Exception ignored) {

        }

        return null;

    }

    public static JSONArray toJSONArray(Object array) throws JSONException {
        JSONArray result = new JSONArray();
        if (!array.getClass().isArray()) {
            throw new JSONException("Not a primitive array: " + array.getClass());
        }
        final int length = Array.getLength(array);
        for (int i = 0; i < length; ++i) {
            result.put(wrap(Array.get(array, i)));
        }
        return result;
    }
    String getStartReason()
    {
        JSONObject object = new JSONObject();
        JSONObject data = new JSONObject();
        if(mCurrentActivity != null)
        {
            Activity activity = mCurrentActivity.get();
            Intent intent = activity.getIntent();
            if (intent != null) {
                String uri =  intent.getDataString();
                try {
                    if(!TextUtils.isEmpty(uri))
                    {
                        object.put("url",uri);
                    }
                    Bundle bundle = intent.getExtras();
                    if(bundle != null)
                    {
                        Set<String> keys = bundle.keySet();
                        for (String key : keys) {
                            Object value =  bundle.get(key);
                            Object supportValue = wrap(value);
                            if(supportValue != null && supportValue != JSONObject.NULL)
                            {
                                data.put(key,wrap(value));
                            }
                        }
                        object.put("data",data);
                    }

                } catch (JSONException exception) {
                    exception.printStackTrace();
                }
            }
        }
        return  object.toString();
    }

    @Override
    public void onActivityStopped(Activity activity) {
        TDLog.i(TAG,"onActivityStopped");
        try {
            synchronized (mActivityLifecycleCallbacksLock) {
                if (notStartedActivity(activity, true)) {
                    TDLog.i(TAG, "onActivityStopped: the SDK might be initialized after the onActivityStart of " + activity);
                    return;
                }
                if (mStartedActivityList.size() == 0) {
                    mCurrentActivity = null;
                    try {
                        mThinkingDataInstance.appEnterBackground();
                        resumeFromBackground = true;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (mThinkingDataInstance.isAutoTrackEnabled()) {
                        JSONObject properties = new JSONObject();
                        if (!mThinkingDataInstance.isAutoTrackEventTypeIgnored(ThinkingAnalyticsSDK.AutoTrackEventType.APP_END)) {
                            try {
                                TDUtils.getScreenNameAndTitleFromActivity(properties, activity);
                            } catch (Exception e) {
                                TDLog.i(TAG, e);
                            }finally {
                                mThinkingDataInstance.autoTrack(TDConstants.APP_END_EVENT_NAME, properties);
                            }
                        }
                    }
                    try {
                        startTimer = new EventTimer(TimeUnit.SECONDS);
                        mThinkingDataInstance.flush();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }

}
