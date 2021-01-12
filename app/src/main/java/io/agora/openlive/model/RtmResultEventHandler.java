package io.agora.openlive.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agora.rtm.ErrorInfo;
import io.agora.rtm.ResultCallback;

public class RtmResultEventHandler  implements ResultCallback<Void> {
        private final Logger log = LoggerFactory.getLogger(RtmResultEventHandler.class);
        @Override
        public void onSuccess(Void aVoid) {
            log.debug("RtmResultCallback onSuccess");
        }

        @Override
        public void onFailure(ErrorInfo errorInfo) {
            log.debug("RtmResultCallback onFailure errorInfo={}", errorInfo.toString());

        }
    }
