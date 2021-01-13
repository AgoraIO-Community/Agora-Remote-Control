package io.agora.openlive.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.SurfaceView;
import android.text.TextUtils;

import io.agora.cloud.common.base.ICloudClient;
import io.agora.cloud.common.utils.RTMConfig;
import io.agora.cloud.service.client.CloudClient;
import io.agora.common.Constant;
import io.agora.openlive.R;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineEx;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Random;

public class WorkerThread extends Thread {
    private final static Logger log = LoggerFactory.getLogger(WorkerThread.class);

    private final Context mContext;

    private static final int ACTION_WORKER_THREAD_QUIT = 0X1010; // quit this thread

    private static final int ACTION_WORKER_JOIN_CHANNEL = 0X2010;

    private static final int ACTION_WORKER_LEAVE_CHANNEL = 0X2011;

    private static final int ACTION_WORKER_RTM_LOGIN = 0X3010;

    private static final int ACTION_WORKER_RTM_LOGOUT = 0x3011;

    private static final class WorkerThreadHandler extends Handler {

        private WorkerThread mWorkerThread;

        WorkerThreadHandler(WorkerThread thread) {
            this.mWorkerThread = thread;
        }

        public void release() {
            mWorkerThread = null;
        }

        @Override
        public void handleMessage(Message msg) {
            if (this.mWorkerThread == null) {
                log.warn("handler is already released! " + msg.what);
                return;
            }

            switch (msg.what) {
                case ACTION_WORKER_THREAD_QUIT:
                    mWorkerThread.exit();
                    break;
                case ACTION_WORKER_JOIN_CHANNEL: {
                    Object[] data = (Object[]) msg.obj;
                    mWorkerThread.joinChannel((String)data[0], msg.arg1, (ChannelMediaOptions)data[1]);
                    break;
                }
                case ACTION_WORKER_LEAVE_CHANNEL: {
                    String channel = (String) msg.obj;
                    mWorkerThread.leaveChannel(channel);
                    break;
                }
//                case ACTION_WORKER_RTM_LOGIN:{
//                    String peerId = (String) msg.obj;
//                    mWorkerThread.rtmLogin(peerId);
//
//                }
//                case ACTION_WORKER_RTM_LOGOUT:{
//                    mWorkerThread.rtmLogout();
//                }
            }
        }
    }

    private WorkerThreadHandler mWorkerHandler;

    private boolean mReady;
    private  String appId;

    public final void waitForReady() {
        while (!mReady) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.debug("wait for " + WorkerThread.class.getSimpleName());
        }
    }

    @Override
    public void run() {
        log.trace("start to run");
        Looper.prepare();

        mWorkerHandler = new WorkerThreadHandler(this);

        ensureRtcEngineReadyLock();

        mReady = true;

        // enter thread looper
        Looper.loop();
    }

    private RtcEngineEx mRtcEngine;
    private Object renderSync = new Object();

    public final void joinChannel(final String channel, int uid, ChannelMediaOptions options) {
        if (Thread.currentThread() != this) {
            log.warn("joinChannel() - worker thread asynchronously " + channel + " " + uid);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_JOIN_CHANNEL;
            envelop.obj = new Object[]{channel,options};
            envelop.arg1 = uid;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        ensureRtcEngineReadyLock();

        String accessToken = mContext.getString(R.string.agora_access_token);
        if (TextUtils.isEmpty(accessToken) || TextUtils.equals(accessToken, "#YOUR ACCESS TOKEN#")) {
            accessToken = null; // default, no token
        }

        if (mRtcEngine.joinChannel(accessToken, channel, uid, options)  != Constants.ERR_OK) {
            log.error("joinChannel:failed!");
        } else {
            log.info("joinChannel:success!");
        }

        mRtcEngineConfig.mChannel = channel;

        log.debug("joinChannel " + channel + " " + (uid & 0xFFFFFFFFL));
    }

    public final void leaveChannel(String channel) {
        if (Thread.currentThread() != this) {
            log.warn("leaveChannel() - worker thread asynchronously " + channel);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_LEAVE_CHANNEL;
            envelop.obj = channel;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        if (mRtcEngine != null) {
            mRtcEngine.leaveChannel();
        }

        int clientRole = mRtcEngineConfig.mClientRole;
        mRtcEngineConfig.reset();
        log.debug("leaveChannel " + channel + " " + clientRole);
    }

    private RtcEngineConfig mRtcEngineConfig;

    public final RtcEngineConfig getRtcEngineConfig() {
        return mRtcEngineConfig;
    }

    private final MyEngineEventHandler mEngineEventHandler;

    private RtcEngine ensureRtcEngineReadyLock() {
        if (mRtcEngine == null) {
            appId = mContext.getString(R.string.private_app_id);
            if (TextUtils.isEmpty(appId)) {
                throw new RuntimeException("NEED TO use your App ID, get your own ID at https://dashboard.agora.io/");
            }
            try {
                mRtcEngine = (RtcEngineEx) RtcEngine.create(mContext, appId, mEngineEventHandler.mRtcEventHandler);
            } catch (Exception e) {
                log.error(Log.getStackTraceString(e));
                throw new RuntimeException("NEED TO check rtc sdk init fatal error\n" + Log.getStackTraceString(e));
            }
            mRtcEngine.setChannelProfile(Constants.CHANNEL_PROFILE_CLOUD_GAMING);
            mRtcEngine.enableVideo();
            mRtcEngine.setLogFile(Environment.getExternalStorageDirectory()
                    + File.separator + mContext.getPackageName() + "/log/agora-rtc.log");

            // Warning: only enable dual stream mode if there will be more than one broadcaster in the channel
            mRtcEngine.enableDualStreamMode(false);
        }
        return mRtcEngine;
    }

    public MyEngineEventHandler eventHandler() {
        return mEngineEventHandler;
    }

    public RtcEngine getRtcEngine() {
        return mRtcEngine;
    }

    public String getAppId(){
        return appId;
    }


    /**
     * call this method to exit
     * should ONLY call this method when this thread is running
     */
    public final void exit() {
        if (Thread.currentThread() != this) {
            log.warn("exit() - exit app thread asynchronously");
            mWorkerHandler.sendEmptyMessage(ACTION_WORKER_THREAD_QUIT);
            return;
        }

        mReady = false;

        // TODO should remove all pending(read) messages

        log.debug("exit() > start");

        // exit thread looper
        Looper.myLooper().quit();

        mWorkerHandler.release();

        log.debug("exit() > end");
    }

    public WorkerThread(Context context) {
        this.mContext = context;

        this.mRtcEngineConfig = new RtcEngineConfig();

        Random random = new Random(System.currentTimeMillis());
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        mRtcEngineConfig.mUid = pref.getInt(ConstantApp.PrefManager.PREF_PROPERTY_UID, 0);
        while (mRtcEngineConfig.mUid == 0) {
          mRtcEngineConfig.mUid = random.nextInt(~(1<<31));
        }
        pref.edit().putInt(ConstantApp.PrefManager.PREF_PROPERTY_UID, mRtcEngineConfig.mUid).apply();
        log.info("ctor:mUid={}", mRtcEngineConfig.mUid);

        this.mEngineEventHandler = new MyEngineEventHandler(mContext, this.mRtcEngineConfig);
    }

}
