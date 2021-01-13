package io.agora.openlive.model;


public class RtcEngineConfig {
    public int mClientRole;

    public int mUid;

    public String mChannel;

    public void reset() {
        mChannel = null;
    }

    RtcEngineConfig() {
    }
}
