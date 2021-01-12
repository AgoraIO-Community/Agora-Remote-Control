package io.agora.openlive.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import io.agora.rtm.ErrorInfo;
import io.agora.rtm.ResultCallback;
import io.agora.rtm.RtmClientListener;
import io.agora.rtm.RtmFileMessage;
import io.agora.rtm.RtmImageMessage;
import io.agora.rtm.RtmMediaOperationProgress;
import io.agora.rtm.RtmMessage;

public class MyRtmEventHandler {

    private List<IRtmClientListenerHandler> mListenerList = new ArrayList<>();

    public void addRtmEventListener(IRtmClientListenerHandler listener){mListenerList.add(listener);}
    public void removeRtmEventListener(IRtmClientListenerHandler listener){mListenerList.remove(listener);}

    final RtmClientListener rtmClientListener = new RtmClientListenerHandler();

    public class RtmClientListenerHandler implements RtmClientListener{
        private final Logger log = LoggerFactory.getLogger(RtmClientListenerHandler.class);
        @Override
        public void onConnectionStateChanged(int state, int reason) {
            log.debug("onConnectionStateChanged state={}, reason={}",state, reason);
        }

        @Override
        public void onMessageReceived(RtmMessage rtmMessage, String peerId) {
            log.debug("onMessageReceived rtmMessage type={} peerId={}",rtmMessage, peerId);
            Iterator<IRtmClientListenerHandler> it = mListenerList.iterator();
            while (it.hasNext()) {
                IRtmClientListenerHandler handler = it.next();
                handler.onMessageReceived(rtmMessage, peerId);
            }

        }

        @Override
        public void onImageMessageReceivedFromPeer(RtmImageMessage rtmImageMessage, String peerId) {
            log.debug("onImageMessageReceivedFromPeer rtmImageMessage size={} peerId={}", rtmImageMessage.getSize(), peerId);

        }

        @Override
        public void onFileMessageReceivedFromPeer(RtmFileMessage rtmFileMessage, String s) {
            log.debug("onFileMessageReceivedFromPeer rtmFileMessage size={}", rtmFileMessage.getSize());
        }

        @Override
        public void onMediaUploadingProgress(RtmMediaOperationProgress
                                                     rtmMediaOperationProgress, long l) {
            log.debug("onMediaUploadingProgress");
        }

        @Override
        public void onMediaDownloadingProgress(RtmMediaOperationProgress rtmMediaOperationProgress, long l) {
            log.debug("onMediaDownloadingProgress");
        }

        @Override
        public void onTokenExpired() {
            log.debug("onTokenExpired");
        }

        @Override
        public void onPeersOnlineStatusChanged(Map<String, Integer> map) {
            log.debug("onPeersOnlineStatusChanged");
        }
    }


}
