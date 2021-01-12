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
import io.agora.common.Constant;
import io.agora.openlive.R;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.RtcEngineEx;
import io.agora.rtc2.video.EncodedVideoFrameInfo;
import io.agora.rtc2.video.IVideoEncodedImageReceiver;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;
import io.agora.rtm.ResultCallback;
import io.agora.rtm.RtmClient;
import io.agora.rtm.SendMessageOptions;

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

    private static final int ACTION_WORKER_CONFIG_ENGINE = 0X2012;


    private static final int ACTION_REGISTER_VIDEO_ENCODED_IMAGE_RECEIVER = 0X201A;


    private static final int ACTION_WORKER_RTM_LOGIN = 0X3010;

    private static final int ACTION_WORKER_RTM_LOGOUT = 0X3011;

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
                case ACTION_WORKER_CONFIG_ENGINE: {
                    Object[] configData = (Object[]) msg.obj;
                    mWorkerThread.configEngine((int) configData[0], (VideoEncoderConfiguration.VideoDimensions) configData[1]);
                    break;
                }
                case ACTION_WORKER_RTM_LOGIN: {
                    Object[] data = (Object[]) msg.obj;
                    mWorkerThread.rtmLogin((String)data[0], ( ResultCallback<Void>)data[1]);
                    break;
                }
                case ACTION_WORKER_RTM_LOGOUT: {
                    Object[] data = (Object[]) msg.obj;
                    mWorkerThread.rtmLogout();
                    break;
                }
            }
        }
    }

    private WorkerThreadHandler mWorkerHandler;

    private boolean mReady;

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
        ensureRtmClientReadyLock();

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
        mRtcEngine.disableAudio();

        if (mRtcEngine.joinChannel(accessToken, channel, uid, options)  != Constants.ERR_OK) {
            log.error("joinChannel:failed!");
        } else {
            log.info("joinChannel:success!");
        }
        mRtcEngine.disableAudio();

        mEngineConfig.mChannel = channel;

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


        int clientRole = mEngineConfig.mClientRole;
        mEngineConfig.reset();
        log.debug("leaveChannel " + channel + " " + clientRole);
    }

    private EngineConfig mEngineConfig;

    public final EngineConfig getEngineConfig() {
        return mEngineConfig;
    }

    private final MyEngineEventHandler mEngineEventHandler;

    public final void configEngine(int cRole, VideoEncoderConfiguration.VideoDimensions videoDimension) {
        if (Thread.currentThread() != this) {
            log.warn("configEngine() - worker thread asynchronously " + cRole + " " + videoDimension);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_CONFIG_ENGINE;
            envelop.obj = new Object[]{cRole, videoDimension};
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        ensureRtcEngineReadyLock();
        mEngineConfig.mClientRole = cRole;
        mEngineConfig.mVideoDimension = videoDimension;

//      mRtcEngine.setVideoProfile(mEngineConfig.mVideoProfile, true); // Earlier than 2.3.0
        mRtcEngine.setVideoEncoderConfiguration(new VideoEncoderConfiguration(videoDimension,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_30,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE));

        mRtcEngine.setClientRole(cRole);

        log.debug("configEngine " + cRole + " " + mEngineConfig.mVideoDimension);
    }


    private RtcEngine ensureRtcEngineReadyLock() {
        if (mRtcEngine == null) {
            String appId = mContext.getString(R.string.private_app_id);
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
            mRtcEngine.disableAudio();
            mRtcEngine.setLogFile(Environment.getExternalStorageDirectory()
                    + File.separator + mContext.getPackageName() + "/log/agora-rtc.log");
            mRtcEngine.setParameters("{\"engine.video.enable_hw_encoder\":\"false\"}");

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

        this.mEngineConfig = new EngineConfig();

        Random random = new Random(System.currentTimeMillis());
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        mEngineConfig.mUid = pref.getInt(ConstantApp.PrefManager.PREF_PROPERTY_UID, 0);
        while (mEngineConfig.mUid == 0) {
          mEngineConfig.mUid = random.nextInt(~(1<<31));
        }
        pref.edit().putInt(ConstantApp.PrefManager.PREF_PROPERTY_UID, mEngineConfig.mUid).apply();
        log.info("ctor:mUid={}", mEngineConfig.mUid);

        this.mEngineEventHandler = new MyEngineEventHandler(mContext, this.mEngineConfig);
        this.mRtmEventHandler = new MyRtmEventHandler();
    }



    private RtmClient rtmClient;
    private final MyRtmEventHandler mRtmEventHandler;

    public MyRtmEventHandler rtmEventHandler() {
        return mRtmEventHandler;
    }

    public RtmClient getRtmClient() {
        return rtmClient;
    }

    public final void rtmLogin(String userId, ResultCallback<Void> resultCallback) {
        if (Thread.currentThread() != this) {
            log.warn("rtmLogin:worker thread asynchronously. userId={}" + userId);
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_RTM_LOGIN;
            envelop.obj = new Object[]{userId,resultCallback};
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        ensureRtmClientReadyLock();

        log.info("rtmLogin:userId={}", userId);
        if(rtmClient == null) {
            log.error("rtmLogin rtmClient is null");
            return;
        }
        rtmClient.login(null, userId, resultCallback);
    }

    public final void rtmLogout() {
        if (Thread.currentThread() != this) {
            log.warn("rtmLogout:worker thread asynchronously.");
            Message envelop = new Message();
            envelop.what = ACTION_WORKER_RTM_LOGOUT;
            mWorkerHandler.sendMessage(envelop);
            return;
        }

        log.info("rtmLogout!");
        if(rtmClient == null) {
            log.error("rtmLogin rtmClient is null");
            return;
        }
        rtmClient.logout(null);
        rtmClient = null;
    }

    private RtmClient ensureRtmClientReadyLock() {
        if (rtmClient == null) {
            String appId = mContext.getString(R.string.private_app_id);
            if (TextUtils.isEmpty(appId)) {
                throw new RuntimeException("NEED TO use your App ID, get your own ID at https://dashboard.agora.io/");
            }
            try {
                rtmClient = RtmClient.createInstance(mContext,appId,mRtmEventHandler.rtmClientListener);
                if(rtmClient == null){
                    return null;
                }

            } catch (Exception e) {
                log.error(Log.getStackTraceString(e));
                throw new RuntimeException("Fatal Error,Need to check rtm sdk\n" + Log.getStackTraceString(e));
            }
            rtmClient.setParameters("{\"rtm.log_filter\": 65535}");

        }
        return rtmClient;
    }

}
