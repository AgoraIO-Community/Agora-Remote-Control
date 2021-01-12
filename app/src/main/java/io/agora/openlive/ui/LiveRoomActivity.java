package io.agora.openlive.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.agora_remote_control.CtrlMsgObtainManager;
import com.example.agora_remote_control.ICtrlMsgObtainManager;
import com.smarx.notchlib.NotchScreenManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;

import io.agora.openlive.R;
import io.agora.openlive.model.AGEventHandler;
import io.agora.openlive.model.ConstantApp;
import io.agora.openlive.model.IRtmClientListenerHandler;
import io.agora.openlive.model.RemoteControlEventObserver;
import io.agora.openlive.model.RtmResultEventHandler;
import io.agora.openlive.model.VideoStatusData;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.ScreenCaptureParameters;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;
import io.agora.rtm.RtmMessage;
import io.agora.rtm.SendMessageOptions;

public class LiveRoomActivity extends BaseActivity implements AGEventHandler, IRtmClientListenerHandler {

    private final static Logger log = LoggerFactory.getLogger(LiveRoomActivity.class);
    private final static int REQUEST_CODE_SHARE_SCREEN = 1000;
    private GridVideoViewContainer mGridVideoViewContainer;

    private RelativeLayout mSmallVideoViewDock;
    private VideoEncoderConfiguration.VideoDimensions localVideoDimensions = null;
    private HashMap<Integer,VideoStatusData> mAllUserData =  new HashMap<>();
    private final HashMap<Integer, SurfaceView> mUidsList = new HashMap<>(); // uid = 0 || uid == EngineConfig.mUid
    private String roomName;
    private RemoteControlEventObserver controlEventObserver;
    private ICtrlMsgObtainManager ctrlMsgObtainManager;


    private final static String clientUserId = "2345";
    private final static String serverUserId = "7890";

    enum SCREEN_CAPTURE_STAT{
        STAT_INIT,
        STAT_PUBLISH,
        STAT_UNPUBLISH
    }
    private SCREEN_CAPTURE_STAT screen_capture_stat = SCREEN_CAPTURE_STAT.STAT_INIT;
    private ChannelMediaOptions screen_capture_options = new ChannelMediaOptions();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotchScreenManager.getInstance().setDisplayInNotch(this);

        setContentView(R.layout.activity_live_room);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return false;
    }

    private boolean isBroadcaster(int cRole) {
        return cRole == Constants.CLIENT_ROLE_BROADCASTER;
    }

    private boolean isBroadcaster() {
        return isBroadcaster(config().mClientRole);
    }

    @Override
    protected void initUIandEvent() {
        event().addEventHandler(this);
        rtmEvent().addRtmEventListener(this);


        Intent i = getIntent();
        int cRole = i.getIntExtra(ConstantApp.ACTION_KEY_CROLE, 0);

        if (cRole == 0) {
            throw new RuntimeException("Should not reach here");
        }

        roomName = i.getStringExtra(ConstantApp.ACTION_KEY_ROOM_NAME);

        doConfigEngine(cRole);

        mGridVideoViewContainer = (GridVideoViewContainer) findViewById(R.id.grid_video_view_container);


        if (!isBroadcaster(cRole)) {
            ChannelMediaOptions options = new ChannelMediaOptions();
            options.publishCameraTrack = false;
            options.publishAudioTrack = false;
            options.clientRoleType = cRole;

            options.autoSubscribeAudio = true;
            options.autoSubscribeVideo = true;
            options.channelProfile = Constants.CHANNEL_PROFILE_CLOUD_GAMING;

            worker().joinChannel(roomName, config().mUid, options);
            worker().rtmLogin(clientUserId,new RtmResultEventHandler());
        } else if(isBroadcaster(cRole)) {

        }

        TextView textRoomName = (TextView) findViewById(R.id.room_name);
        textRoomName.setText(roomName);
    }

    private void doConfigEngine(int cRole) {
        VideoEncoderConfiguration.VideoDimensions dimension = new VideoEncoderConfiguration.VideoDimensions(720, 1280);
        localVideoDimensions = dimension;
        worker().configEngine(cRole, dimension);
    }

    @Override
    protected void deInitUIandEvent() {
        doLeaveChannel();
        doRtmLogout();
        event().removeEventHandler(this);
        rtmEvent().removeRtmEventListener(this);

        mUidsList.clear();
    }

    private void doLeaveChannel() {

        worker().leaveChannel(config().mChannel);

    }

    private void doRtmLogout() {
        if(!isBroadcaster()) {
            if(ctrlMsgObtainManager != null) {
                ctrlMsgObtainManager.destroyCtrlMsgObtain();
                ctrlMsgObtainManager = null;
            }
        }
        worker().rtmLogout();
    }

    public void onClickClose(View view) {
        finish();
    }

    public void onShowHideClicked(View view) {
        boolean toHide = true;
        if (view.getTag() != null && (boolean) view.getTag()) {
            toHide = false;
        }
        view.setTag(toHide);

        doShowButtons(toHide);
    }

    private void doShowButtons(boolean hide) {
        View topArea = findViewById(R.id.top_area);
        topArea.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);

    }


    @Override
    public void onFirstRemoteVideoDecoded(int uid, int width, int height, int elapsed) {
    }

    @Override
    public void onFirstRemoteVideoFrame(int uid, int width, int height, int elapsed) {
        log.debug("onFirstRemoteVideoFrame  uid={} width={} height={} elapsed={}", uid, width, height, elapsed);
        doRenderRemoteUi(uid);
    }

    private void doSwitchToBroadcaster(boolean broadcaster) {
        final int currentHostCount = mUidsList.size();
        final int uid = config().mUid;
        log.debug("doSwitchToBroadcaster " + currentHostCount + " " + (uid & 0XFFFFFFFFL) + " " + broadcaster);

        if (broadcaster) {
            doConfigEngine(Constants.CLIENT_ROLE_BROADCASTER);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    doRenderRemoteUi(uid);
                    doShowButtons(false);
                }
            }, 1000); // wait for reconfig engine
        } else {
            stopInteraction(currentHostCount, uid);
        }
    }

    private void stopInteraction(final int currentHostCount, final int uid) {
        doConfigEngine(Constants.CLIENT_ROLE_AUDIENCE);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                doRemoveRemoteUi(uid);

                doShowButtons(false);
            }
        }, 1000); // wait for reconfig engine
    }

    private void doRenderRemoteUi(final int uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                if (mUidsList.get(uid) == null) {
                    SurfaceView surfaceV = RtcEngine.CreateRendererView(getApplicationContext());
                    mUidsList.put(uid, surfaceV);
                    mAllUserData.put(uid, new VideoStatusData());
                    if (config().mUid == uid) {

                    } else {
                        rtcEngine().setupRemoteVideo(new VideoCanvas(surfaceV, VideoCanvas.RENDER_MODE_FIT, uid));
                    }
                }
                if (!isBroadcaster()) {
                    SurfaceView v = mUidsList.get(uid);
                    if (v != null) {
                        updateCtrlRect();
                        if(ctrlMsgObtainManager == null) {
                            log.debug("doRenderRemoteUi new CtrlMsgObtainManager");
                            ctrlMsgObtainManager = new CtrlMsgObtainManager();
                        }
                        if (ctrlMsgObtainManager != null) {
                            if(controlEventObserver == null){
                                SendMessageOptions sendMessageOptions = new SendMessageOptions();
                                controlEventObserver = new RemoteControlEventObserver(serverUserId,worker().getRtmClient(),sendMessageOptions);
                            }
                            ctrlMsgObtainManager.prepareCtrlMsgObtain(v, controlEventObserver);
                        }
                    }
                }

                if (mViewType == VIEW_TYPE_DEFAULT) {
                    log.debug("doRenderRemoteUi VIEW_TYPE_DEFAULT" + " " + (uid & 0xFFFFFFFFL));
                    switchToDefaultVideoView();
                } else {
                    int bigBgUid = mSmallVideoViewAdapter.getExceptedUid();
                    log.debug("doRenderRemoteUi VIEW_TYPE_SMALL" + " " + (uid & 0xFFFFFFFFL) + " " + (bigBgUid & 0xFFFFFFFFL));
                    switchToSmallVideoView(bigBgUid);
                }
            }
        });
    }

    @Override
    public void onJoinChannelSuccess(final String channel, final int uid, final int elapsed) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                if (mUidsList.containsKey(uid)) {
                    log.debug("already added to UI, ignore it " + (uid & 0xFFFFFFFFL) + " " + mUidsList.get(uid));
                    return;
                }

                final boolean isBroadcaster = isBroadcaster();
                log.debug("onJoinChannelSuccess " + channel + " " + uid + " " + elapsed + " " + isBroadcaster);


                    worker().getEngineConfig().mUid = uid;

                if(!isBroadcaster()){
                    if(controlEventObserver == null){
                        SendMessageOptions sendMessageOptions = new SendMessageOptions();
                        controlEventObserver = new RemoteControlEventObserver(serverUserId,worker().getRtmClient(),sendMessageOptions);
                    }
                }else if(isBroadcaster()){

                }

            }
        });
    }

    @Override
    public void onUserOffline(int uid, int reason) {
        log.debug("onUserOffline " + (uid & 0xFFFFFFFFL) + " " + reason);
        doRemoveRemoteUi(uid);
        if (uid != config().mUid) rtcEngine().setupRemoteVideo(new VideoCanvas(null, VideoCanvas.RENDER_MODE_FIT, uid));
    }

    @Override
    public void onUserJoined(int uid, int elapsed) {
        log.debug("onUserJoined:uid={} elapsed={}", uid, elapsed);
    }

    @Override
    public void onLocalVideoStats(final IRtcEngineEventHandler.LocalVideoStats stats) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                VideoStatusData videoLocalStatusData = getSecialUserDataInfo(config().mUid);
                if(videoLocalStatusData!=null){
                    videoLocalStatusData.setLocalResolutionInfo(localVideoDimensions.width,localVideoDimensions.height,stats.sentFrameRate);
                }

            }
        });
    }

    @Override
    public void onRtcStats(final IRtcEngineEventHandler.RtcStats stats) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                VideoStatusData videoLocalStatusData = getSecialUserDataInfo(config().mUid);
                if (videoLocalStatusData != null) {
                    videoLocalStatusData.setLocalVideoSendRecvInfo(stats.txVideoKBitRate, stats.rxVideoKBitRate);
                    videoLocalStatusData.setLocalAudioSendRecvInfo(stats.txAudioKBitRate, stats.rxAudioKBitRate);
                    videoLocalStatusData.setLocalLastmileDelayInfo(stats.lastmileDelay);
                    videoLocalStatusData.setLocalCpuAppTotalInfo(stats.cpuAppUsage, stats.cpuTotalUsage);
                }
            }
        });
    }

    @Override
    public void onNetworkQuality(final int uid, final int txQuality, final int rxQuality) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                VideoStatusData videoStatusData = null;
                if(uid == 0){
                    videoStatusData = VideoViewAdapterUtil.getLocalUserData(mAllUserData);
                }else{
                    videoStatusData = getSecialUserDataInfo(uid);
                }

                if(videoStatusData!=null) {
                    videoStatusData.setSendRecvQualityInfo(txQuality, rxQuality);
                }
                if(mGridVideoViewContainer!=null){
                    mGridVideoViewContainer.notifyDataChange(mAllUserData);
                }

            }
        });
    }

    @Override
    public void onRecorderStateChanged(final int state, final int code) {
        super.onRecorderStateChanged(state, code);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showLongToast("onRecorderStageChanged state: " + state + " code: " + code);
            }
        });
    }

    @Override
    public void onRemoteVideoStats(final IRtcEngineEventHandler.RemoteVideoStats stats) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }
                VideoStatusData videoRemoteStatusData = getSecialUserDataInfo(stats.uid);
                if(videoRemoteStatusData!=null) {
                    videoRemoteStatusData.setRemoteResolutionInfo(stats.width, stats.height, stats.decoderOutputFrameRate);
                    videoRemoteStatusData.setRemoteVideoDelayInfo(stats.delay);
                }
            }
        });

    }

    @Override
    public void onMessageReceived(RtmMessage rtmMessage, String peerId) {
        log.debug("rtm onMessageReceived:rtmMessage type={} peerId={}", rtmMessage.getMessageType(), peerId);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                if (isFinishing()) {
//                    return;
//                }
                if(!isBroadcaster()){
                    if(ctrlMsgObtainManager == null){
                        ctrlMsgObtainManager = new CtrlMsgObtainManager();
                    }
                    if(ctrlMsgObtainManager != null){
                        log.debug("LiveRoomActivity onMessageReceived receive peerReturnMsg length={}",rtmMessage.getRawMessage().length);
                        ctrlMsgObtainManager.peerReturnMsgAnalysis(rtmMessage.getRawMessage());
                    }
                }

            }
        });
    }

    public VideoStatusData getSecialUserDataInfo(int uid){
        return mAllUserData.get(uid);
    }

    private void requestRemoteStreamType(final int currentHostCount) {
        log.debug("requestRemoteStreamType " + currentHostCount);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                HashMap.Entry<Integer, SurfaceView> highest = null;
                for (HashMap.Entry<Integer, SurfaceView> pair : mUidsList.entrySet()) {
                    log.debug("requestRemoteStreamType " + currentHostCount + " local " + (config().mUid & 0xFFFFFFFFL) + " " + (pair.getKey() & 0xFFFFFFFFL) + " " + pair.getValue().getHeight() + " " + pair.getValue().getWidth());
                    if (pair.getKey() != config().mUid && (highest == null || highest.getValue().getHeight() < pair.getValue().getHeight())) {
                        if (highest != null) {
                            rtcEngine().setRemoteVideoStreamType(highest.getKey(), Constants.VIDEO_STREAM_LOW);
                            log.debug("setRemoteVideoStreamType switch highest VIDEO_STREAM_LOW " + currentHostCount + " " + (highest.getKey() & 0xFFFFFFFFL) + " " + highest.getValue().getWidth() + " " + highest.getValue().getHeight());
                        }
                        highest = pair;
                    } else if (pair.getKey() != config().mUid && (highest != null && highest.getValue().getHeight() >= pair.getValue().getHeight())) {
                        rtcEngine().setRemoteVideoStreamType(pair.getKey(), Constants.VIDEO_STREAM_LOW);
                        log.debug("setRemoteVideoStreamType VIDEO_STREAM_LOW " + currentHostCount + " " + (pair.getKey() & 0xFFFFFFFFL) + " " + pair.getValue().getWidth() + " " + pair.getValue().getHeight());
                    }
                }
                if (highest != null && highest.getKey() != 0) {
                    rtcEngine().setRemoteVideoStreamType(highest.getKey(), Constants.VIDEO_STREAM_HIGH);
                    log.debug("setRemoteVideoStreamType VIDEO_STREAM_HIGH " + currentHostCount + " " + (highest.getKey() & 0xFFFFFFFFL) + " " + highest.getValue().getWidth() + " " + highest.getValue().getHeight());
                }
            }
        }, 500);
    }

    private void doRemoveRemoteUi(final int uid) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) {
                    return;
                }

                mUidsList.remove(uid);
                mAllUserData.remove(uid);
                int bigBgUid = -1;
                if (mSmallVideoViewAdapter != null) {
                    bigBgUid = mSmallVideoViewAdapter.getExceptedUid();
                }

                log.debug("doRemoveRemoteUi " + (uid & 0xFFFFFFFFL) + " " + (bigBgUid & 0xFFFFFFFFL));

                if (mViewType == VIEW_TYPE_DEFAULT || uid == bigBgUid) {
                    switchToDefaultVideoView();
                } else {
                    switchToSmallVideoView(bigBgUid);
                }
                if (config().mUid == uid) {

                }
            }
        });
    }

    private SmallVideoViewAdapter mSmallVideoViewAdapter;

    private void switchToDefaultVideoView() {
        if (mSmallVideoViewDock != null)
            mSmallVideoViewDock.setVisibility(View.GONE);
        mGridVideoViewContainer.initViewContainer(getApplicationContext(), config().mUid, mUidsList);

        mViewType = VIEW_TYPE_DEFAULT;

        int sizeLimit = mUidsList.size();
        if (sizeLimit > ConstantApp.MAX_PEER_COUNT + 1) {
            sizeLimit = ConstantApp.MAX_PEER_COUNT + 1;
        }
        for (int i = 0; i < sizeLimit; i++) {
            int uid = mGridVideoViewContainer.getItem(i).mUid;
            if (config().mUid != uid) {
                rtcEngine().setRemoteVideoStreamType(uid, Constants.VIDEO_STREAM_HIGH);
                log.debug("setRemoteVideoStreamType VIDEO_STREAM_HIGH " + mUidsList.size() + " " + (uid & 0xFFFFFFFFL));
            }
        }
        boolean setRemoteUserPriorityFlag = false;
        for (int i = 0; i < sizeLimit; i++) {
            int uid = mGridVideoViewContainer.getItem(i).mUid;
            if (config().mUid != uid) {
                if (!setRemoteUserPriorityFlag) {
                    setRemoteUserPriorityFlag = true;
                    rtcEngine().setRemoteUserPriority(uid, Constants.USER_PRIORITY_HIGH);
                    log.debug("setRemoteUserPriority USER_PRIORITY_HIGH " + mUidsList.size() + " " + (uid & 0xFFFFFFFFL));
                } else {
                    rtcEngine().setRemoteUserPriority(uid, Constants.USER_PRIORITY_NORANL);
                    log.debug("setRemoteUserPriority USER_PRIORITY_NORANL " + mUidsList.size() + " " + (uid & 0xFFFFFFFFL));
                }
            }
        }
    }

    private void switchToSmallVideoView(int uid) {
        HashMap<Integer, SurfaceView> slice = new HashMap<>(1);
        slice.put(uid, mUidsList.get(uid));
        mGridVideoViewContainer.initViewContainer(getApplicationContext(), uid, slice);

        bindToSmallVideoView(uid);

        mViewType = VIEW_TYPE_SMALL;

        requestRemoteStreamType(mUidsList.size());
    }

    public int mViewType = VIEW_TYPE_DEFAULT;

    public static final int VIEW_TYPE_DEFAULT = 0;

    public static final int VIEW_TYPE_SMALL = 1;

    private void bindToSmallVideoView(int exceptUid) {
        if (mSmallVideoViewDock == null) {
            ViewStub stub = (ViewStub) findViewById(R.id.small_video_view_dock);
            mSmallVideoViewDock = (RelativeLayout) stub.inflate();
        }

        RecyclerView recycler = (RecyclerView) findViewById(R.id.small_video_view_container);

        boolean create = false;

        if (mSmallVideoViewAdapter == null) {
            create = true;
            mSmallVideoViewAdapter = new SmallVideoViewAdapter(this, exceptUid, mUidsList);
            mSmallVideoViewAdapter.setHasStableIds(true);
        }
        recycler.setHasFixedSize(true);

        recycler.setLayoutManager(new GridLayoutManager(this, 3, GridLayoutManager.VERTICAL, false));
        recycler.setAdapter(mSmallVideoViewAdapter);

        recycler.setDrawingCacheEnabled(true);
        recycler.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_AUTO);

        if (!create) {
            mSmallVideoViewAdapter.notifyUiChanged(mUidsList, exceptUid, null, null);
        }
        for (Integer tempUid : mUidsList.keySet()) {
            if (config().mUid != tempUid) {
                if (tempUid == exceptUid) {
                    rtcEngine().setRemoteUserPriority(tempUid, Constants.USER_PRIORITY_HIGH);
                    log.debug("setRemoteUserPriority USER_PRIORITY_HIGH " + mUidsList.size() + " " + (tempUid & 0xFFFFFFFFL));
                } else {
                    rtcEngine().setRemoteUserPriority(tempUid, Constants.USER_PRIORITY_NORANL);
                    log.debug("setRemoteUserPriority USER_PRIORITY_NORANL " + mUidsList.size() + " " + (tempUid & 0xFFFFFFFFL));
                }
            }
        }


        recycler.setVisibility(View.VISIBLE);
        mSmallVideoViewDock.setVisibility(View.VISIBLE);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        log.debug("onConfigurationChanged " + newConfig.orientation + " " + newConfig.screenWidthDp
                + " " + newConfig.screenHeightDp + " " + mUidsList.size());
        switch(this.getWindowManager().getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_0:
                log.debug("onConfigurationChanged Surface orientation ROTATION_0");
                break;
            case Surface.ROTATION_90:
                log.debug("onConfigurationChanged Surface orientation ROTATION_90");
                break;
            case Surface.ROTATION_180:
                log.debug("onConfigurationChanged Surface orientation ROTATION_180");
                break;
            case Surface.ROTATION_270:
                log.debug("onConfigurationChanged Surface orientation ROTATION_270");
                break;
        }
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) mGridVideoViewContainer.getLayoutParams();
        params.setMargins(0, 0, 0, 0);
        mGridVideoViewContainer.setLayoutParams(params);
        switchToDefaultVideoView();
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        }

        super.onConfigurationChanged(newConfig);

    }


    private void updateCtrlRect() {
        Rect r = new Rect();
        if(ctrlMsgObtainManager == null) {
            ctrlMsgObtainManager = new CtrlMsgObtainManager();
        }
        if(ctrlMsgObtainManager != null) {
            ctrlMsgObtainManager.setCtrlPanelRect(r);
        }
    }
}
