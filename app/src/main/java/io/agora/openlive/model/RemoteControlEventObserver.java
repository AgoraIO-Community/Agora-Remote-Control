package io.agora.openlive.model;

import com.example.agora_remote_control.DataStatisticsUtils;
import com.example.agora_remote_control.IRemoteCtrlEventCallback;
import com.example.agora_remote_control.DelayDataStatistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agora.rtc2.RtcEngine;
import io.agora.rtm.RtmClient;
import io.agora.rtm.RtmMessage;
import io.agora.rtm.SendMessageOptions;

public class RemoteControlEventObserver implements IRemoteCtrlEventCallback {
    private final static Logger log = LoggerFactory.getLogger(RemoteControlEventObserver.class);
    private String peerId;
    private RtmClient m_RtmClient;
    private SendMessageOptions mSendMsgOptions;

    public RemoteControlEventObserver(String peerId, RtmClient rtmClient, SendMessageOptions sendMessageOptions) {
        this.peerId = peerId;
        this.m_RtmClient = rtmClient;
        this.mSendMsgOptions = sendMessageOptions;
    }

    @Override
    public void onCtrlMsgObtainCallback(byte[] buf) {
        log.debug("RemoteControlEventObserver onCtrlMsgObtainCallback!");
        if(m_RtmClient == null || peerId == null || mSendMsgOptions == null){
            log.error("RemoteControlEventObserver onCtrlMsgObtainCallback have null object");
        }

        RtmMessage msg = m_RtmClient.createMessage();
        msg.setRawMessage(buf);
        log.debug("RemoteControlEventObserver sendMessageToPeer peerId={} message type={}",peerId, msg.getMessageType());
        m_RtmClient.sendMessageToPeer(peerId,msg,mSendMsgOptions,new RtmResultEventHandler());
    }

    @Override
    public void onMsgTransmissionRTTDelay(DelayDataStatistics delayDataStatistics) {
        log.debug("RemoteControlEventObserver onMsgTransmissionRTTDelay minDelayMs={} maxDelayMs={} averageDelayMs={} count={}",
                delayDataStatistics.getMinValue() , delayDataStatistics.getMaxValue(), delayDataStatistics.getAverageValue(), delayDataStatistics.getCount());
    }

    @Override
    public void onRemoteScreenSizeChanged(int width, int height, int orientation) {
        log.debug("RemoteControlEventObserver onRemoteScreenSizeChanged width={} height={} orientation={}",width, height, orientation);
    }


}
