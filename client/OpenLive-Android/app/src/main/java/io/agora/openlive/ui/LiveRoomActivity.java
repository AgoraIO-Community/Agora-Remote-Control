package io.agora.openlive.ui;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewStub;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.smarx.notchlib.NotchScreenManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

import io.agora.cloud.common.utils.RTMConfig;
import io.agora.cloud.service.client.CloudClient;
import io.agora.openlive.R;
import io.agora.openlive.model.AGEventHandler;
import io.agora.openlive.model.ConstantApp;
import io.agora.openlive.model.VideoStatusData;
import io.agora.rtc2.ChannelMediaOptions;
import io.agora.rtc2.Constants;
import io.agora.rtc2.IRtcEngineEventHandler;
import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.RtcEngine;
import io.agora.rtc2.video.VideoCanvas;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public class LiveRoomActivity extends BaseActivity implements AGEventHandler {

    private final static Logger log = LoggerFactory.getLogger(LiveRoomActivity.class);
    private GridVideoViewContainer mGridVideoViewContainer;

    private RelativeLayout mSmallVideoViewDock;
    private VideoEncoderConfiguration.VideoDimensions localVideoDimensions = null;
    private HashMap<Integer,VideoStatusData> mAllUserData =  new HashMap<>();
    private final HashMap<Integer, SurfaceView> mUidsList = new HashMap<>(); // uid = 0 || uid == RtcEngineConfig.mUid
    private String roomName;
    private String peerId;
    private CloudClient mCloudClient;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotchScreenManager.getInstance().setDisplayInNotch(this);

        setContentView(R.layout.activity_live_room);

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


        Intent i = getIntent();
        int cRole = i.getIntExtra(ConstantApp.ACTION_KEY_CROLE, 0);

        if (cRole == 0) {
            throw new RuntimeException("Should not reach here");
        }

        roomName = i.getStringExtra(ConstantApp.ACTION_KEY_ROOM_NAME);

        peerId = i.getStringExtra(ConstantApp.ACTION_KEY_RTM_PEERID);
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

            if (mCloudClient == null) {
                mCloudClient = new CloudClient();
            }

        } else if(isBroadcaster(cRole)) {
        }


        TextView textRoomName = (TextView) findViewById(R.id.room_name);
        textRoomName.setText(roomName);
    }

    @Override
    protected void deInitUIandEvent() {
        doLeaveChannel();
        event().removeEventHandler(this);
        if (mCloudClient != null) {
            mCloudClient.leave();
        }
        mUidsList.clear();
    }

    private void doLeaveChannel() {

        worker().leaveChannel(config().mChannel);
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
                    if (config().mUid == uid) { //local video

                    } else {
                        rtcEngine().setupRemoteVideo(new VideoCanvas(surfaceV, VideoCanvas.RENDER_MODE_FIT, uid));
                    }
                }
                if (!isBroadcaster()) {
                    SurfaceView v = mUidsList.get(uid);
                    if (v != null) {
                        log.info("doRenderRemoteUi uid={} update surfaceView v={}",uid, v);
                        if(mCloudClient != null) {
                            String appId = worker().getAppId();
                            RTMConfig rtmConfig = new RTMConfig();
                            rtmConfig.appId = appId;
                            rtmConfig.peerId1 = peerId;
                            mCloudClient.enter(getApplicationContext(), rtmConfig);
                            mCloudClient.updateView(v);
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


                    worker().getRtcEngineConfig().mUid = uid;

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
//                if(mSmallVideoViewAdapter!=null){
//                    mSmallVideoViewAdapter.notifyDataChange(mAllUserData);
//                }
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
                if (config().mUid == uid) {  //local video

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
}
