import '../styles/index.scss';
import '../assets/common.css';
import AgoraRTC from 'agora-rtc-sdk';
import AgoraRTM from 'agora-rtm-sdk';
import {throttle} from 'lodash';
const messages = require('./EventMessage_pb');

if (process.env.NODE_ENV === 'development') {
  require('../index.html');
}
let appID = "";
const token = null;
let serverId = "";
const uid = 0;
const useTouch = 'ontouchstart' in document.documentElement;
let eventTime = 0;
let mousedown = false;
let remoteStreamWidth = 0, remoteStreamHeight = 0;
let rtcClient = AgoraRTC.createClient({
  mode: "live",
  codec: "vp8"
});
let rtmClient;// = AgoraRTM.createInstance(appID);

const calcLogicPos = (canvasWidth, canvasHeight, streamWidth, streamHeight, clientX, clientY) => {
  let originX = 0;
  let originY = 0;
  let displayHeight = canvasHeight;
  let displayWidth = canvasWidth;

  let wc = canvasWidth / canvasHeight;
  let ws = streamWidth / streamHeight;
  
  if(ws > wc){
    displayHeight = canvasWidth / ws;
    originY = (canvasHeight - displayHeight) / 2;
    originX = 0;
  }
  else{
    displayWidth = canvasHeight * ws;
    originX = (canvasWidth - displayWidth) /2;
    originY = 0;
  }

  let logicX = (clientX - originX) / displayWidth;
  let logicY = (clientY - originY) / displayHeight;
  return {logicX, logicY};
};

const bindControlEvents = () => {
  let downEvent = useTouch ? "touchstart" : "mousedown";
  let moveEvent = useTouch ? "touchmove" : "mousemove";
  let upEvent = useTouch ? "touchend" : "mouseup";
  $("#remote-video-container").off(downEvent).on(downEvent, async e => {
    if(remoteStreamWidth === 0 && remoteStreamHeight === 0) {
      return alert("stream not ready");
    }
    mousedown = true;

    let xstart = $("#remote-video-container>div").offset().left;;
    let ystart = $("#remote-video-container>div").offset().top;
    let width = $("#remote-video-container>div").width();
    let height = $("#remote-video-container>div").height();

    let {logicX, logicY} = calcLogicPos(width, height, remoteStreamWidth, remoteStreamHeight, e.pageX - xstart, e.pageY - ystart);

    if(logicX >= 0 && logicX <= 1 && logicY >= 0 && logicY <= 1) {
      let uplink = new messages.UplinkMessage();
      let header = new messages.EventHeader();
      let event = new messages.MotionEvent();
      let touch = new messages.TouchEvent();
      let time = new Date().getTime();
      eventTime = time;

      touch.setPointerindex(0);
      touch.setPointerid(0);
      touch.setLogicx(logicX);
      touch.setLogicy(logicY);

      event.setDowntime(time);
      event.setEventtime(eventTime - time);
      event.setCount(1);
      event.setAction(0);
      event.setEventsList([touch]);

      uplink.setEventheader(header);
      uplink.setMotionevent(event);

      let binary = uplink.serializeBinary();

      let message = rtmClient.createMessage({
        description:"control",
        rawMessage: binary,
        messageType: AgoraRTM.MessageType.RAW
      });
      await rtmClient.sendMessageToPeer(message, serverId);
      //console.log(`mousedown sent ${logicX} ${logicY} ${binary} ${JSON.stringify(uplink.toObject())}`);
    } else {
      console.log("out of screen");
    }
  });

  $("#remote-video-container").off(moveEvent).on(moveEvent, throttle(async e => {
    if(!mousedown) {
      return;
    }

    let xstart = $("#remote-video-container>div").offset().left;;
    let ystart = $("#remote-video-container>div").offset().top;
    let width = $("#remote-video-container>div").width();
    let height = $("#remote-video-container>div").height();

    let {logicX, logicY} = calcLogicPos(width, height, remoteStreamWidth, remoteStreamHeight, e.pageX - xstart, e.pageY - ystart);

    if(logicX >= 0 && logicX <= 1 && logicY >= 0 && logicY <= 1) {
      let uplink = new messages.UplinkMessage();
      let header = new messages.EventHeader();
      let event = new messages.MotionEvent();
      let touch = new messages.TouchEvent();
      let time = new Date().getTime();

      touch.setPointerindex(0);
      touch.setPointerid(0);
      touch.setLogicx(logicX);
      touch.setLogicy(logicY);

      event.setDowntime(time);
      event.setEventtime(time - eventTime);
      event.setCount(1);
      event.setAction(2);
      event.setEventsList([touch]);

      uplink.setEventheader(header);
      uplink.setMotionevent(event);

      let binary = uplink.serializeBinary();

      let message = rtmClient.createMessage({
        description:"control",
        rawMessage: binary,
        messageType: AgoraRTM.MessageType.RAW
      });
      await rtmClient.sendMessageToPeer(message, serverId);
      //console.log(`mousemove sent ${logicX} ${logicY} ${binary} ${JSON.stringify(uplink.toObject())}`);
    } else {
      console.log("out of screen");
    }
  }, 10));

  $("#remote-video-container").off(upEvent).on(upEvent, async e => {
    if(remoteStreamWidth === 0 && remoteStreamHeight === 0) {
      return alert("stream not ready");
    }
    mousedown = false;

    let xstart = $("#remote-video-container>div").offset().left;;
    let ystart = $("#remote-video-container>div").offset().top;
    let width = $("#remote-video-container>div").width();
    let height = $("#remote-video-container>div").height();

    let {logicX, logicY} = calcLogicPos(width, height, remoteStreamWidth, remoteStreamHeight, e.pageX -xstart, e.pageY-ystart);

    if(logicX >= 0 && logicX <= 1 && logicY >= 0 && logicY <= 1) {
      let uplink = new messages.UplinkMessage();
      let header = new messages.EventHeader();
      let event = new messages.MotionEvent();
      let touch = new messages.TouchEvent();
      let time = new Date().getTime();

      touch.setPointerindex(0);
      touch.setPointerid(0);
      touch.setLogicx(logicX);
      touch.setLogicy(logicY);

      event.setDowntime(time);
      event.setEventtime(time - eventTime);
      event.setCount(1);
      event.setAction(1);
      event.setEventsList([touch]);

      uplink.setEventheader(header);
      uplink.setMotionevent(event);

      let binary = uplink.serializeBinary();

      let message = rtmClient.createMessage({
        description:"control",
        rawMessage: binary,
        messageType: AgoraRTM.MessageType.RAW
      });
      await rtmClient.sendMessageToPeer(message, serverId);
      //console.log(`mouseup sent ${logicX} ${logicY} ${binary} ${JSON.stringify(uplink.toObject())}`);
    } else {
      console.log("out of screen");
    }
  });
};


const initRTC = () => {
  return new Promise((resolve, reject) => {
    // init rtcClient
    rtcClient.init(appID, () => {
      console.log('init success');


      rtcClient.on("stream-added", e => {
        rtcClient.subscribe(e.stream);
      });

      rtcClient.on("stream-subscribed", e => {
        let stream = e.stream;
        let uid = stream.getId();

        stream.on('player-status-change', e => {
          console.log('remote player ' + uid + ' status change', e);
          if(e.mediaType === "video" && e.status === "play") {
            let elements = $("#remote-video-container video");
            let width = elements[0].videoWidth;
            let height = elements[0].videoHeight;
            remoteStreamWidth = width;
            remoteStreamHeight = height;
          }
        });
        stream.play("remote-video-container", {fit:"contain"});
      });

      // rtcClient.on("stream-removed", function onStreamRemoved(e) {
      //   var stream = e.stream
      //   var uid = stream.getId()
      //   $("#remote-" + uid).remove()
      //   // clear stream from map
      //   delete remoteStreams[uid]
      // })

      // rtcClient.on("peer-leave", function onStreamRemoved(e) {
      //   var uid = e.uid
      //   $("#remote-" + uid).remove()
      //   // clear stream from map
      //   delete remoteStreams[uid]
      // })

      /**
      * Joins an AgoraRTC server
      * This method joins an AgoraRTC server.
      * Parameters
      * tokenOrKey: string | null
      *    Low security requirements: Pass null as the parameter value.
      *    High security requirements: Pass the string of the Token or server Key as the parameter value. See Use Security Keys for details.
      *  server: string
      *    A string that provides a unique server name for the Agora session. The length must be within 64 bytes. Supported character scopes:
      *    26 lowercase English letters a-z
      *    26 uppercase English letters A-Z
      *    10 numbers 0-9
      *    Space
      *    "!", "#", "$", "%", "&", "(", ")", "+", "-", ":", ";", "<", "=", ".", ">", "?", "@", "[", "]", "^", "_", "{", "}", "|", "~", ","
      *  uid: number | null
      *    The user ID, an integer. Ensure this ID is unique. If you set the uid to null, the server assigns one and returns it in the onSuccess callback.
      *   Note:
      *      All users in the same serverID should have the same type (number) of uid.
      *      If you use a number as the user ID, it should be a 32-bit unsigned integer with a value ranging from 0 to (232-1).
      **/
      rtcClient.join(token ? token : null, serverId, uid ? uid : null, uid => {
        console.log('join server: ' + serverId + ' success, uid: ' + uid);
        resolve();
      }, err => {
        reject(err);
      });
    }, err => {
      reject(err);
    });
  });
};

const leaveRtc = () =>{
  rtcClient.leave();
};

const leaveRtm = () =>{
  rtmClient.logout();
};

const initRTM = async () => {
  await rtmClient.login({uid:`${new Date().getTime()}`});
};

function validator(formData, fields) {
  var keys = Object.keys(formData);
  for (let key of keys) {
    if (fields.indexOf(key) != -1) {
      if (!formData[key]) {
        console.log("Please Enter " + key);
        return false;
      }
    }
  }
  return true;
}

function serializeformData() {
  var formData = $("#form").serializeArray();
  var obj = {};
  for (var item of formData) {
    var key = item.name;
    var val = item.value;
    obj[key] = val;
  }
  return obj;
}
var fields = ["appID", "serverId"];

$("#join").on("click", function (e) {
  console.log("join");
  
  e.preventDefault();
  var params = serializeformData(); // Data is feteched and serilized from the form element.
  if (validator(params, fields)) {
    console.log("input error");
  }
  appID = params["appID"];
  serverId = params["serverId"];
  rtmClient = AgoraRTM.createInstance(appID);
  Promise.all([initRTC(), initRTM()]).then(() => {
    bindControlEvents();
  }).catch(e => {
    alert(e);
  });
});
// Leeaves the chanenl if someone clicks the leave button
$("#leave").on("click", function (e) {
  console.log("leave");
  e.preventDefault();
  var params = serializeformData();
  if (validator(params, fields)) {
    leaveRtc();
    leaveRtm();
  }
});