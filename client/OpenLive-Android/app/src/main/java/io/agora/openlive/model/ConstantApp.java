package io.agora.openlive.model;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import io.agora.rtc2.Constants;
import io.agora.rtc2.video.VideoEncoderConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConstantApp {
    private final static Logger log = LoggerFactory.getLogger(ConstantApp.class);

    public static final int BASE_VALUE_PERMISSION = 0X0001;
    public static final int PERMISSION_REQ_ID_RECORD_AUDIO = BASE_VALUE_PERMISSION + 1;
    public static final int PERMISSION_REQ_ID_CAMERA = BASE_VALUE_PERMISSION + 2;
    public static final int PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = BASE_VALUE_PERMISSION + 3;

    public static final int MAX_PEER_COUNT = 3;

    public static class PrefManager {
        public static final String PREF_PROPERTY_UID = "pOCXx_uid";

    }

    public static final String ACTION_KEY_CROLE = "C_Role";
    public static final String ACTION_KEY_SERVER_ID = "ecServerId";

}
