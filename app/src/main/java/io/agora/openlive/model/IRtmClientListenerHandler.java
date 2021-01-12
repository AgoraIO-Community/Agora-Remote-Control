package io.agora.openlive.model;

import io.agora.rtm.RtmMessage;

public interface IRtmClientListenerHandler {

    void onMessageReceived(RtmMessage rtmMessage, String peerId);
}

