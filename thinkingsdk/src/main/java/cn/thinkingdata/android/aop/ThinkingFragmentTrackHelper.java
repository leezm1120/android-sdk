
package cn.thinkingdata.android.aop;

import android.os.Bundle;
import android.view.View;

import cn.thinkingdata.android.ThinkingDataRuntimeBridge;

/**
 * 通过asm调用方法发送数据
 */
public class ThinkingFragmentTrackHelper {

    private static final String TAG = "ThinkingAnalytics";

    public static void onFragmentViewCreated(Object object, View rootView, Bundle bundle) {
        ThinkingDataRuntimeBridge.onFragmentCreateView(object, rootView);
    }

    public static void onFragmentResume(Object object) {
        ThinkingDataRuntimeBridge.onFragmentOnResume(object);
    }

    public static void onFragmentPause(Object object) {
    }

    public static void onFragmentSetUserVisibleHint(Object object, boolean isVisibleToUser) {
        ThinkingDataRuntimeBridge.onFragmentSetUserVisibleHint(object, isVisibleToUser);
    }

    public static void onFragmentHiddenChanged(Object object, boolean hidden) {
        ThinkingDataRuntimeBridge.onFragmentHiddenChanged(object, hidden);
    }
}
