package io.agora.openlive.model;

import io.agora.rtc2.RtcConnection;
import io.agora.rtc2.video.VideoEncoderConfiguration;

public class EngineConfig {
    public int mClientRole;

    public VideoEncoderConfiguration.VideoDimensions mVideoDimension;;

    public int mUid;

    public String mChannel;

    public RtcConnection mConnection;


    public void reset() {
        mChannel = null;
    }

    EngineConfig() {
    }
}
