//
//  ViewController.m
//  agora_remote_control_ios_socket
//
//  Created by LEB on 2020/12/15.
//

#import "MainViewController.h"
#import "RemoteCtrlMessage.pbobjc.h"
#import "TouchEventMessage.pbobjc.h"
#import <Masonry/Masonry.h>
#import <AgoraRtcKit/AgoraRtcEngineKit.h>
#import <AgoraRtmKit/AgoraRtmKit.h>
#import <Toast/Toast.h>

static NSString *kAPPID = @"";

typedef NS_ENUM(int32_t, PEventAction) {
    ACTION_CANCEL = 3,
    ACTION_DOWN = 0,
    ACTION_UP = 1,
    ACTION_MOVE = 2,
    ACTION_POINTER_DOWN = 5,
    ACTION_POINTER_UP = 6,
};

typedef NS_ENUM(int32_t, TOOL_TYPE) {
    TOOL_TYPE_FINGER = 1,
    TOOL_TYPE_MOUSE = 3,
    TOOL_TYPE_STYLUS = 2,
    TOOL_TYPE_UNKNOWN = 0,
};

@interface MainViewController () <AgoraRtcEngineDelegate>

@property (nonatomic, strong) UIView *canvasView;
@property (nonatomic, strong) AgoraRtcEngineKit *rtcEngineKit;
@property (nonatomic, strong) AgoraRtcVideoCanvas *remoteVideCanvas;
@property (nonatomic, strong) AgoraRtmKit *rtmKit;
@property (nonatomic, copy) NSString *peerId;

@end

@implementation MainViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    
    self.rtmKit = [[AgoraRtmKit alloc] initWithAppId:kAPPID delegate:nil];
    
    self.rtcEngineKit = [AgoraRtcEngineKit sharedEngineWithAppId:kAPPID delegate:self];
    [self.rtcEngineKit setChannelProfile:AgoraChannelProfileLiveBroadcasting];
    [self.rtcEngineKit setClientRole:AgoraClientRoleAudience];
    [self.rtcEngineKit enableVideo];
    [self.rtcEngineKit enableAudio];
    
    self.canvasView = [[UIView alloc] init];
    [self.view addSubview:self.canvasView];
    [self.canvasView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.edges.equalTo(self.view);
    }];
    
    UIButton *settingButton = [UIButton buttonWithType:UIButtonTypeCustom];
    settingButton.backgroundColor = [UIColor redColor];
    settingButton.clipsToBounds = YES;
    settingButton.layer.cornerRadius = 18;
    [settingButton addTarget:self action:@selector(settingClicked:) forControlEvents:UIControlEventTouchUpInside];
    [self.view addSubview:settingButton];
    [settingButton mas_makeConstraints:^(MASConstraintMaker *make) {
        make.top.equalTo(self.view.mas_top).offset(60);
        make.right.equalTo(self.view.mas_right).offset(-20);
        make.width.equalTo(@36);
        make.height.equalTo(@36);
    }];
}

- (void)settingClicked:(UIButton *)sender {
    
    if (sender.isSelected) {
                
    }else {
        
        UIAlertController *alertController = [UIAlertController alertControllerWithTitle:@"Setting"
                                                                                  message:@"join channel and rtm peer id"
                                                                           preferredStyle:UIAlertControllerStyleAlert];

        [alertController addTextFieldWithConfigurationHandler:^(UITextField * _Nonnull textField) {
            textField.placeholder = @"input rtc channel name";
            textField.autocorrectionType = UITextAutocorrectionTypeNo;
            textField.spellCheckingType = UITextSpellCheckingTypeNo;

        }];
        [alertController addTextFieldWithConfigurationHandler:^(UITextField * _Nonnull textField) {
            textField.placeholder = @"input rtm peer id";
            textField.keyboardType = UIKeyboardTypeNumberPad;
            textField.autocorrectionType = UITextAutocorrectionTypeNo;
            textField.spellCheckingType = UITextSpellCheckingTypeNo;
        }];

        //确认按钮
        UIAlertAction *confirm = [UIAlertAction actionWithTitle:@"Join"
                                                         style:UIAlertActionStyleDefault
                                                       handler:^(UIAlertAction * _Nonnull action) {
           
            NSString *channel = alertController.textFields.firstObject.text;
            NSString *peerId = alertController.textFields.lastObject.text;
            if (![channel length] || ![peerId length]) {
                [self.view makeToast:@"Input channel and peerid !!!" duration:2.0f position:CSToastPositionCenter];
                return;
            }
            
            self.peerId = peerId;
            
            [self.rtcEngineKit joinChannelByToken:nil
                                        channelId:channel
                                             info:nil
                                              uid:0
                                      joinSuccess:^(NSString * _Nonnull channel, NSUInteger uid, NSInteger elapsed) {
                NSLog(@"join channel %@ uid:%@ success", channel, @(uid));
                sender.backgroundColor = [UIColor greenColor];
                sender.selected = YES;
            }];
            
            [self.rtmKit loginByToken:nil
                                 user:peerId
                           completion:^(AgoraRtmLoginErrorCode errorCode) {
                if (AgoraRtmLoginErrorOk == errorCode) {
                    NSLog(@"[RTM] loginByUid：%@ success", peerId);
                }else {
                    NSLog(@"[RTM] loginByUid：%@ failed(%@)", peerId, @(errorCode));
                }
            }];
            
        }];
           
        //取消按钮
        UIAlertAction* cancel = [UIAlertAction actionWithTitle:@"Canel"
                                                        style:UIAlertActionStyleCancel
                                                      handler:^(UIAlertAction * _Nonnull action) {

        }];

        [alertController addAction:confirm];
        [alertController addAction:cancel];
        [self presentViewController:alertController animated:YES completion:^{
            
        }];
        
    }
    
    sender.selected = !sender.isSelected;
    sender.backgroundColor = sender.isSelected ? [UIColor greenColor]:[UIColor redColor];
}

#pragma mark - AgoraRtcEngineDelegate

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine
    didOccurError:(AgoraErrorCode)errorCode {
    
    NSLog(@"AgoraRtcEngineKit didOccurError %@", @(errorCode));
    
}

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine
   didJoinedOfUid:(NSUInteger)uid
          elapsed:(NSInteger)elapsed {
    
    if (self.remoteVideCanvas) {
        return;
    }
    
    AgoraRtcVideoCanvas *canvas = [[AgoraRtcVideoCanvas alloc] init];
    canvas.view = self.canvasView;
    canvas.uid = uid;
    canvas.renderMode = AgoraVideoRenderModeHidden;
    self.remoteVideCanvas = canvas;
    int ret = [self.rtcEngineKit setupRemoteVideo:canvas];
    if (0 != ret) {
         NSLog(@"Error setupRemoteVideo errcode：%@",@(ret));
    }
    
}

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine
  didOfflineOfUid:(NSUInteger)uid
           reason:(AgoraUserOfflineReason)reason {

    if (self.remoteVideCanvas.uid == uid) {
        int ret = [self.rtcEngineKit setupRemoteVideo:nil];
        if (0 != ret) {
             NSLog(@"Error setupRemoteVideo errcode：%@",@(ret));
        }
        self.remoteVideCanvas = nil;
    }
    
}

#pragma mark - touch event
- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    [self event:event action:ACTION_DOWN];
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(nullable UIEvent *)event {
    [self event:event action:ACTION_MOVE];
}

- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(nullable UIEvent *)event {
    [self event:event action:ACTION_UP];
}

- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(nullable UIEvent *)event {
    [self event:event action:ACTION_CANCEL];
}

- (void)event:(UIEvent *)event action:(PEventAction)action {
    
    PRemoteCtrlMessageHeader *msgHeader = [PRemoteCtrlMessageHeader new];
    msgHeader.srcSystemType = PScrSystemType_IphoneIosSystemPlatform;
    msgHeader.msgType = PCtrlMessageType_ScreenTouchEventType;
    msgHeader.version = 1.0;
    msgHeader.timestamp = event.timestamp;
    msgHeader.msgType = PCtrlMessageType_ScreenTouchEventType;
    if (@available(iOS 13.4, *)) {
        if (UIEventTypeScroll == event.type) {
            msgHeader.msgType = PCtrlMessageType_ScreenTouchEventType;
        }
    }

    PTouchEventMessage *eventMessage = [PTouchEventMessage new];
    eventMessage.eventCounter = (int32_t)event.allTouches.count;
    eventMessage.downTime = event.timestamp;
    eventMessage.eventTime = event.timestamp;
    eventMessage.action = action;
    eventMessage.pointerCount = eventMessage.eventCounter;
    eventMessage.metaState = 0;
    if (@available(iOS 13.4, *)) {
        eventMessage.buttonState = (int32_t)event.buttonMask + 1;
    }else {
        eventMessage.buttonState = 1;
    }
    eventMessage.deviceId = 0;//[UIDevice currentDevice].identifierForVendor;
    eventMessage.edgeFlags = 0;
    eventMessage.source = 4098;
    eventMessage.flags = 0;
    
    NSMutableArray *pointerPropertys = [NSMutableArray arrayWithCapacity:eventMessage.pointerCount];
    NSMutableArray *pointerCoords = [NSMutableArray arrayWithCapacity:eventMessage.pointerCount];

    NSEnumerator * enumerator = [event.allTouches objectEnumerator];
    UITouch * touch;
    while (touch = [enumerator nextObject]) {
        NSLog(@"value %@",touch);
        
        PPointerProperties *pointerProperty = [PPointerProperties new];
        pointerProperty.id_p = 0;
        pointerProperty.toolType = TOOL_TYPE_FINGER;
        [pointerPropertys addObject:pointerProperty];
        
        CGPoint pt = [touch locationInView:self.view];
        PPointerCoords *pointerCoord = [PPointerCoords new];
        pointerCoord.x = pt.x;
        pointerCoord.y = pt.y;
        pointerCoord.size = touch.majorRadius;
        pointerCoord.touchMajor = touch.majorRadius;
        pointerCoord.touchMinor = touch.majorRadiusTolerance;
        pointerCoord.pressure = touch.force;
        if (@available(iOS 9.1, *)) {
            pointerCoord.orientation = (float)touch.altitudeAngle;
        }
        [pointerCoords addObject:pointerCoord];

    }
    eventMessage.pointerPropertiesArray = pointerPropertys;
    eventMessage.pointerCoordsArray = pointerCoords;

    PRemoteCtrlMessage *message = [PRemoteCtrlMessage new];
    message.ctrlMessageHeader = msgHeader;
    message.payload = [eventMessage data];
    
    NSInteger msgid = 0;
    int ret = [self.rtcEngineKit createDataStream:&msgid reliable:false ordered:false];
    if (0 != ret) {
        NSLog(@" createDataStream failed %@", @(ret));
        return;
    }
        
    [self.rtcEngineKit sendStreamMessage:msgid data:[message data]];
    
}

@end
