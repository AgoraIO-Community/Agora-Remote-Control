package io.agora.openlive.model;

import android.content.Context;

import io.agora.rtc2.IRtcEngineEventHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

public class MyEngineEventHandler {
    public MyEngineEventHandler(Context ctx, EngineConfig config) {
        this.mContext = ctx;
        this.mConfig = config;
    }

    private final EngineConfig mConfig;

    private final Context mContext;

    private final ConcurrentHashMap<AGEventHandler, Integer> mEventHandlerList = new ConcurrentHashMap<>();

    public void addEventHandler(AGEventHandler handler) {
        this.mEventHandlerList.put(handler, 0);
    }

    public void removeEventHandler(AGEventHandler handler) {
        this.mEventHandlerList.remove(handler);
    }

    final IRtcEngineEventHandler mRtcEventHandler = new OpenLiveEventHandler("default");
    public class OpenLiveEventHandler extends IRtcEngineEventHandler {
        private final Logger log = LoggerFactory.getLogger(this.getClass());
        private final String name;

        public OpenLiveEventHandler(String s) {
            name = s;
        }

        @Override
        public void onFirstRemoteVideoDecoded(int uid, int width, int height, int elapsed) {
            log.debug("{}:onFirstRemoteVideoDecoded:uid={} w={} h={} elapsed={} handler.size={}", name, uid, width, height, elapsed, mEventHandlerList.size());

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onFirstRemoteVideoDecoded(uid, width, height, elapsed);
            }
        }

        @Override
        public void onFirstRemoteVideoFrame(int uid, int width, int height, int elapsed) {
            log.debug("{}:onFirstRemoteVideoFrame:uid={} width={} height={} elapsed={} handler.size={}", name, uid, width, height, elapsed, mEventHandlerList.size());

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onFirstRemoteVideoFrame(uid, width, height, elapsed);
            }
        }

        @Override
        public void onFirstLocalVideoFrame(int width, int height, int elapsed) {
            log.debug("{}:onFirstLocalVideoFrame:w={} h={} elapsed={}", name, width, height, elapsed);
        }

        @Override
        public void onUserJoined(int uid, int elapsed) {
            log.debug("{}:onUserJoined:uid={} elapsed={} handler.size={}", name, uid, elapsed, mEventHandlerList.size());

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onUserJoined(uid, elapsed);
            }
        }

        @Override
        public void onUserOffline(int uid, int reason) {
            log.debug("{}:onUserOffline:uid={} reason={} handler.size={}", name, uid, reason, mEventHandlerList.size());
            // FIXME this callback may return times
            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onUserOffline(uid, reason);
            }
        }

        @Override
        public void onUserMuteVideo(int uid, boolean muted) {
            log.debug("{}:onUserMuteVideo:uid={} muted={}", name, uid, muted);
        }


        @Override
        public void onLeaveChannel(RtcStats stats) {
            log.debug("{}:onLeaveChannel:stats={}", name, stats);
        }

        @Override
        public void onJoinChannelSuccess(String channel, int uid, int elapsed) {
            log.debug("{}:onJoinChannelSuccess:channel={} uid={} elapsed={} handler.size={}", name, channel, uid, elapsed, mEventHandlerList.size());

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onJoinChannelSuccess(channel, uid, elapsed);
            }
        }

        public void onRejoinChannelSuccess(String channel, int uid, int elapsed) {
            log.debug("{}:onRejoinChannelSuccess:channel={} uid={} elapsed={}", name, channel, uid, elapsed);
        }

        public void onLocalVideoStats(IRtcEngineEventHandler.LocalVideoStats stats) {
            log.debug("{}:onLocalVideoStats:stats={} width={} height={}", name, stats, stats.encodedFrameWidth, stats.encodedFrameHeight);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onLocalVideoStats(stats);
            }
        }

        public void onRtcStats(IRtcEngineEventHandler.RtcStats stats) {
            log.debug("{}:onRtcStats:stats={} handler.size={}", name, stats, mEventHandlerList.size());

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onRtcStats(stats);
            }
        }

        public void onNetworkQuality(int uid, int txQuality, int rxQuality) {
            log.debug("{}:onNetworkQuality:uid={} txQuality={} rxQuality={} handler.size={}", name, uid, txQuality, rxQuality, mEventHandlerList.size());

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onNetworkQuality(uid, txQuality, rxQuality);
            }
        }

        public void onRemoteVideoStats(IRtcEngineEventHandler.RemoteVideoStats stats) {
            log.debug("{}:onRemoteVideoStats:stats={} width={} height={}", name, stats, stats.width, stats.height);

            Iterator<AGEventHandler> it = mEventHandlerList.keySet().iterator();
            while (it.hasNext()) {
                AGEventHandler handler = it.next();
                handler.onRemoteVideoStats(stats);
            }
        }

    };

}
