//
//  ViewController.m
//  agora_remote_control_ios_socket
//
//  Created by LEB on 2020/12/15.
//

#import "MainViewController.h"
#import "EventMessage.pbobjc.h"
#import <Masonry/Masonry.h>
#import <AgoraRtcKit/AgoraRtcEngineKit.h>
#import <AgoraRtmKit/AgoraRtmKit.h>
#import <Toast/Toast.h>
#import <YYModel/YYModel.h>
#import <sys/time.h>

static NSString *kAPPID = @"aab8b8f5a8cd4469a63042fcfafe7063";
static NSString *kLoginChannelKey = @"LoginChannelKey";
static NSString *kLoginPeerIdKey = @"LoginPeerIdKey";
static NSString *kLoginAppIdKey = @"kLoginAppIdKey";

typedef NS_ENUM(int32_t, TOOL_TYPE) {
    TOOL_TYPE_FINGER = 1,
    TOOL_TYPE_MOUSE = 3,
    TOOL_TYPE_STYLUS = 2,
    TOOL_TYPE_UNKNOWN = 0,
};

@interface MainViewController () <AgoraRtcEngineDelegate>

@property (nonatomic, strong) UIView *contentView;
@property (nonatomic, strong) AgoraRtcEngineKit *rtcEngineKit;
@property (nonatomic, strong) AgoraRtcVideoCanvas *remoteVideCanvas;
@property (nonatomic, strong) AgoraRtmKit *rtmKit;
@property (nonatomic, copy) NSString *dstPeerId;
@property (nonatomic, assign) BOOL isLogin;
@property (nonatomic, strong) UIView *canvasView;
@property (nonatomic, assign) CGSize remoteResolution;

@end

@implementation MainViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    
    self.remoteResolution = UIScreen.mainScreen.bounds.size;
    self.isLogin = NO;
        
    self.contentView = [[UIView alloc] init];
    self.contentView.backgroundColor = [UIColor orangeColor];
    [self.view addSubview:self.contentView];
    [self.contentView mas_makeConstraints:^(MASConstraintMaker *make) {
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
        make.right.equalTo(self.view.mas_right).offset(-30);
        make.width.equalTo(@36);
        make.height.equalTo(@36);
    }];
    
    [self.contentView addSubview:[self canvasView]];
}

- (void)loadAgoraKit:(NSString *)appid {
    
    self.rtmKit = [[AgoraRtmKit alloc] initWithAppId:appid delegate:nil];
    self.rtcEngineKit = [AgoraRtcEngineKit sharedEngineWithAppId:appid delegate:self];
    [self.rtcEngineKit setChannelProfile:AgoraChannelProfileLiveBroadcasting];
    [self.rtcEngineKit setClientRole:AgoraClientRoleAudience];
    [self.rtcEngineKit enableVideo];
    [self.rtcEngineKit enableAudio];
    
}

- (void)login:(UIButton *)sender {
    
    UIAlertController *alertController = [UIAlertController alertControllerWithTitle:@"Login"
                                                                              message:@"join channel and rtm peer id"
                                                                       preferredStyle:UIAlertControllerStyleAlert];

    [alertController addTextFieldWithConfigurationHandler:^(UITextField * _Nonnull textField) {
        
        textField.placeholder = @"input rtc channel name";
        textField.autocorrectionType = UITextAutocorrectionTypeNo;
        textField.spellCheckingType = UITextSpellCheckingTypeNo;
        textField.text = [[NSUserDefaults standardUserDefaults] stringForKey:kLoginChannelKey];

    }];
    
    [alertController addTextFieldWithConfigurationHandler:^(UITextField * _Nonnull textField) {
        
        textField.placeholder = @"input dst peer id";
        textField.autocorrectionType = UITextAutocorrectionTypeNo;
        textField.spellCheckingType = UITextSpellCheckingTypeNo;
        textField.text = [[NSUserDefaults standardUserDefaults] stringForKey:kLoginPeerIdKey];
        
    }];
    
    if (!self.rtmKit || !self.rtcEngineKit) {
        
        [alertController addTextFieldWithConfigurationHandler:^(UITextField * _Nonnull textField) {
            
            textField.placeholder = @"input app id";
            textField.autocorrectionType = UITextAutocorrectionTypeNo;
            textField.spellCheckingType = UITextSpellCheckingTypeNo;
            textField.text = kAPPID;//[[NSUserDefaults standardUserDefaults] stringForKey:kLoginAppIdKey];
            
        }];
        
    }
    
    //确认按钮
    UIAlertAction *confirm = [UIAlertAction actionWithTitle:@"Join"
                                                     style:UIAlertActionStyleDefault
                                                   handler:^(UIAlertAction * _Nonnull action) {
        
        NSString *channel = alertController.textFields.firstObject.text;
        NSString *peerId = [alertController.textFields objectAtIndex:1].text;
        if (alertController.textFields.count > 2) {
            NSString *appid = alertController.textFields.lastObject.text;
            if (![appid length]) {
                [self.view makeToast:@"Please input appid !!!" duration:2.0f position:CSToastPositionCenter];
                return;
            }
            [self loadAgoraKit:appid];
            [[NSUserDefaults standardUserDefaults] setObject:appid forKey:kLoginAppIdKey];
        }
        
        if (![channel length] || ![peerId length]) {
            [self.view makeToast:@"Input channel and peerid !!!" duration:2.0f position:CSToastPositionCenter];
            return;
        }
        
        self.dstPeerId = peerId;
        
        [[NSUserDefaults standardUserDefaults] setObject:channel forKey:kLoginChannelKey];
        [[NSUserDefaults standardUserDefaults] setObject:peerId forKey:kLoginPeerIdKey];
        [[NSUserDefaults standardUserDefaults] synchronize];
        
        [self.rtcEngineKit joinChannelByToken:nil
                                    channelId:channel
                                         info:nil
                                          uid:0
                                  joinSuccess:^(NSString * _Nonnull channel, NSUInteger uid, NSInteger elapsed) {
            
            NSLog(@"join channel %@ uid:%@ success", channel, @(uid));
            [self.rtmKit loginByToken:nil
                                 user:@(uid).stringValue
                           completion:^(AgoraRtmLoginErrorCode errorCode) {
                
                if (AgoraRtmLoginErrorOk == errorCode) {
                    NSLog(@"[RTM] loginByUid：%@ success", peerId);
                    self.isLogin = YES;
                    [self updateSettingButton:sender];

                }else {
                    NSString *errMsg = [NSString stringWithFormat:@" login failed(%@)!!! ", @(errorCode)];
                    [self.view makeToast:errMsg duration:2.0f position:CSToastPositionCenter];

                }
                
            }];
            
        }];
        
    }];
       
    //取消按钮
    UIAlertAction* cancel = [UIAlertAction actionWithTitle:@"Cancel"
                                                    style:UIAlertActionStyleCancel
                                                  handler:^(UIAlertAction * _Nonnull action) {

    }];

    [alertController addAction:confirm];
    [alertController addAction:cancel];
    [self presentViewController:alertController animated:YES completion:^{
        
    }];
    
}

- (void)updateConstraints:(UIView *)canvasView {
    
    CGFloat srcWidth = self.remoteResolution.width;
    CGFloat srcheight = self.remoteResolution.height;
    
    CGFloat screenWidth = UIScreen.mainScreen.bounds.size.width;
    CGFloat screenHeight = UIScreen.mainScreen.bounds.size.height;
    
    CGFloat scaleX  = srcWidth/screenWidth;
    CGFloat scaleY  = srcheight/screenHeight;

    if( scaleX > scaleY ) {
        
        CGFloat scale = srcheight/srcWidth;
        CGFloat height = screenWidth * scale;
        [canvasView mas_remakeConstraints:^(MASConstraintMaker *make) {
            make.left.equalTo(self.contentView);
            make.right.equalTo(self.contentView);
            make.height.equalTo(@(height));
            make.centerY.equalTo(self.contentView);
        }];
        
    }else {
    
        CGFloat scale = srcWidth/srcheight;
        CGFloat height = screenHeight * scale;
        [canvasView mas_remakeConstraints:^(MASConstraintMaker *make) {
            make.top.equalTo(self.contentView);
            make.bottom.equalTo(self.contentView);
            make.width.equalTo(@(height));
            make.centerX.equalTo(self.contentView);
        }];
        
    }
    
}

- (UIView *)getCanvasView {
    
    UIView *canvasView = [[UIView alloc] init];
    canvasView.backgroundColor = [UIColor blueColor];
    [self.contentView addSubview:canvasView];
    [self updateConstraints:canvasView];
    
    return canvasView;
    
}

- (void)logout:(UIButton *)sender {
    
    UIAlertController *alertController = [UIAlertController alertControllerWithTitle:@"Logout"
                                                                              message:@"leave"
                                                                       preferredStyle:UIAlertControllerStyleAlert];
    //确认按钮
    UIAlertAction *confirm = [UIAlertAction actionWithTitle:@"Leave"
                                                     style:UIAlertActionStyleDefault
                                                   handler:^(UIAlertAction * _Nonnull action) {
       
        [self clearRemoteCanvas];
        [self.rtcEngineKit leaveChannel:^(AgoraChannelStats * _Nonnull stat) {
        
            [self.rtmKit logoutWithCompletion:^(AgoraRtmLogoutErrorCode errorCode) {
                if (AgoraRtmLoginErrorOk == errorCode) {
                    NSLog(@"[RTM] logout success");
                    self.isLogin = NO;
                    [self updateSettingButton:sender];
                }else {
                    NSString *errMsg = [NSString stringWithFormat:@" logout failed(%@)!!! ", @(errorCode)];
                    [self.view makeToast:errMsg duration:2.0f position:CSToastPositionCenter];
                }
            }];
            
        }];
        
    }];
       
    //取消按钮
    UIAlertAction* cancel = [UIAlertAction actionWithTitle:@"Cancel"
                                                    style:UIAlertActionStyleCancel
                                                  handler:^(UIAlertAction * _Nonnull action) {

    }];

    [alertController addAction:confirm];
    [alertController addAction:cancel];
    [self presentViewController:alertController animated:YES completion:^{
        
    }];
}

- (void)clearRemoteCanvas {
    [self.canvasView removeFromSuperview];
    self.canvasView = nil;
    self.remoteVideCanvas = nil;
}

- (void)updateSettingButton:(UIButton *)sender {
    
    if (self.isLogin) {
        sender.backgroundColor = [UIColor greenColor];
        sender.selected = YES;
    }else {
        sender.backgroundColor = [UIColor redColor];
        sender.selected = NO;
    }
}

- (void)settingClicked:(UIButton *)sender {
    sender.isSelected ? [self logout:sender]:[self login:sender];
}

uint64_t CFCurrentMillisecond(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    long long time = ((long long)tv.tv_sec) * NSEC_PER_USEC + ((long long) tv.tv_usec) / NSEC_PER_USEC;
    return time;
}

- (UITouch *)findMinByTimestamp:(NSArray *)touchs {
    
    UITouch *touch = touchs.firstObject;
    for (NSUInteger index = 1; index < touchs.count; index++) {
        UITouch *tmp = [touchs objectAtIndex:index];
        if (tmp.timestamp < touch.timestamp) {
            touch = tmp;
        }
    }
    return touch;
    
}

- (UITouch *)findMaxByTimestamp:(NSArray *)touchs {
    
    UITouch *touch = touchs.firstObject;
    for (NSUInteger index = 1; index < touchs.count; index++) {
        UITouch *tmp = [touchs objectAtIndex:index];
        if (tmp.timestamp > touch.timestamp) {
            touch = tmp;
        }
    }
    return touch;
    
}

- (void)sendMotionEvent:(NSArray *)touchs
               downtime:(uint64_t)eventTime
                 action:(ACTION)action {
    
    MotionEvent *mevent = [MotionEvent new];
    mevent.action = action;
    mevent.downTime = 0;//event.timestamp;
    mevent.eventTime = eventTime;
    mevent.count = (int32_t)touchs.count;
    
    NSMutableArray *touchEventArray = [NSMutableArray arrayWithCapacity:mevent.count];
    int32_t index = 0;
    for (UITouch * touch in touchs) {
        
        TouchEvent *tevent = [TouchEvent new];
        tevent.pressure = touch.force;
        tevent.pointerIndex = index;
        CGPoint pt = [touch locationInView:self.canvasView];
        tevent.logicX = pt.x/self.canvasView.frame.size.width;
        tevent.logicY = pt.y/self.canvasView.frame.size.height;
        tevent.pointerId = 0;
        tevent.size = touch.majorRadiusTolerance;
        [touchEventArray addObject:tevent];
        index++;
        
    }
    
    mevent.eventsArray = touchEventArray;
    
    EventHeader *msgHeader = [EventHeader new];
    msgHeader.version = @"1.0";
    msgHeader.sequence = CFCurrentMillisecond();
    
    UplinkMessage *uploadMessage = [UplinkMessage new];
//    uploadMessage.hasEventHeader = YES;
    uploadMessage.eventHeader = msgHeader;
    
//    uploadMessage.hasMotionEvent = YES;
    uploadMessage.motionEvent = mevent;
    
    [self printMessage:msgHeader eventMessage:mevent];
    
    if ([self.dstPeerId length]) {
        
        AgoraRtmRawMessage *rtmMessage = [[AgoraRtmRawMessage alloc] initWithRawData:[uploadMessage data] description:@""];
        [self.rtmKit sendMessage:rtmMessage
                          toPeer:self.dstPeerId
                      completion:^(AgoraRtmSendPeerMessageErrorCode errorCode) {
            if (AgoraRtmLoginErrorOk != errorCode) {
                NSString *errMsg = [NSString stringWithFormat:@" sendMessage failed(%@)!!! ", @(errorCode)];
                NSLog(@"%@", errMsg);
            }
        }];
        
    }else {
        NSInteger msgid = 0;
        int ret = [self.rtcEngineKit createDataStream:&msgid reliable:false ordered:false];
        if (0 != ret) {
            NSLog(@" createDataStream failed %@", @(ret));
            return;
        }
            
        [self.rtcEngineKit sendStreamMessage:msgid data:[uploadMessage data]];
    }
    
}

- (void)event:(UIEvent *)event action:(ACTION)action {
    
    if (!self.isLogin) {
        return;
    }
    
    static uint64_t s_downTime = 0;
    if (ACTION_ActionDown == action) {
        s_downTime = event.timestamp;
    }
    
    [self sendMotionEvent:[event.allTouches allObjects]
                 downtime:event.timestamp - s_downTime
                   action:action];
    
//    NSMutableArray *mutaTouchs = [NSMutableArray arrayWithArray:[event.allTouches allObjects]];
//    if (ACTION_ActionDown == action) {
//
//        UITouch *touch = [self findMinByTimestamp:mutaTouchs];
//        [self sendMotionEvent:@[touch]
//                     downtime:event.timestamp - s_downTime
//                       action:ACTION_ActionDown];
//
//        if (mutaTouchs.count > 1) {
//
//            [mutaTouchs removeObject:touch];
//            [self sendMotionEvent:mutaTouchs
//                         downtime:event.timestamp - s_downTime
//                           action:ACTION_ActionPointerDown];
//        }
//
//    }else if (ACTION_ActionUp == action) {
//
//        UITouch *touch = [self findMaxByTimestamp:mutaTouchs];
//        if (mutaTouchs.count > 1) {
//
//            [mutaTouchs removeObject:touch];
//            [self sendMotionEvent:mutaTouchs
//                         downtime:event.timestamp - s_downTime
//                           action:ACTION_ActionPointerUp];
//        }else {
//            [self sendMotionEvent:@[touch]
//                         downtime:event.timestamp - s_downTime
//                           action:ACTION_ActionUp];
//        }
//
//    }else {
//        [self sendMotionEvent:mutaTouchs
//                     downtime:event.timestamp - s_downTime
//                       action:action];
//    }
    
    
    
}

- (void)printMessage:(EventHeader *)header eventMessage:(MotionEvent *)message {
    
    NSString *top = @"!!!=======================begin===========================!!!\n";
    NSString *footer = @"!!!========================end===========================!!!\n";
    
    NSString *headerStr = [NSString stringWithFormat:@"[Header] version:%@ sequence:%@ \n",header.version, @(header.sequence)];
    
    NSLog(@"%@%@[MESSAGE] %@\n%@", top, headerStr, [message yy_modelToJSONObject], footer);

}

#pragma mark - AgoraRtcEngineDelegate

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine
    didOccurError:(AgoraErrorCode)errorCode {
    
    NSLog(@"AgoraRtcEngineKit didOccurError %@", @(errorCode));
    
}

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine
didApiCallExecute:(NSInteger)error
              api:(NSString * _Nonnull)api
           result:(NSString * _Nonnull)result {
    
}

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine
   didJoinedOfUid:(NSUInteger)uid
          elapsed:(NSInteger)elapsed {
        
    if (self.remoteVideCanvas) {
        return;
    }
    
    self.canvasView = [self getCanvasView];
    AgoraRtcVideoCanvas *canvas = [[AgoraRtcVideoCanvas alloc] init];
    canvas.view = self.canvasView;
    canvas.uid = uid;
    canvas.renderMode = AgoraVideoRenderModeFit;
    self.remoteVideCanvas = canvas;
    int ret = [self.rtcEngineKit setupRemoteVideo:canvas];
    if (0 != ret) {
         NSLog(@"Error setupRemoteVideo errcode：%@",@(ret));
    }
    
}

- (void)videoSizeChanged:(NSUInteger)uid size:(CGSize)size {
    if (self.remoteVideCanvas.uid == uid && !CGSizeEqualToSize(size, self.remoteResolution)) {
        self.remoteResolution = size;
        [self updateConstraints:self.remoteVideCanvas.view];
    }
}

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine firstRemoteVideoDecodedOfUid:(NSUInteger)uid size:(CGSize)size elapsed:(NSInteger)elapsed {
    NSLog(@"uid:%@ size:%@ elapsed:%@", @(uid), NSStringFromCGSize(size), @(elapsed));
    [self videoSizeChanged:uid size:size];
}

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine videoSizeChangedOfUid:(NSUInteger)uid size:(CGSize)size rotation:(NSInteger)rotation {
    
    NSLog(@"uid:%@ size:%@ rotation:%@", @(uid), NSStringFromCGSize(size), @(rotation));
    [self videoSizeChanged:uid size:size];
    
}

- (void)rtcEngine:(AgoraRtcEngineKit * _Nonnull)engine
  didOfflineOfUid:(NSUInteger)uid
           reason:(AgoraUserOfflineReason)reason {

    if (self.remoteVideCanvas.uid == uid) {
        
        int ret = [self.rtcEngineKit setupRemoteVideo:nil];
        if (0 != ret) {
             NSLog(@"Error setupRemoteVideo errcode：%@",@(ret));
        }
        [self clearRemoteCanvas];
    }
    
}

#pragma mark - AgoraRtmDelegate

- (void)rtmKit:(AgoraRtmKit * _Nonnull)kit connectionStateChanged:(AgoraRtmConnectionState)state reason:(AgoraRtmConnectionChangeReason)reason {
    
}

#pragma mark - touch event
- (void)touchesBegan:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event {
    [self event:event action:ACTION_ActionDown];
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches withEvent:(nullable UIEvent *)event {
    [self event:event action:ACTION_ActionMove];
}

- (void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(nullable UIEvent *)event {
    [self event:event action:ACTION_ActionUp];
}

- (void)touchesCancelled:(NSSet<UITouch *> *)touches withEvent:(nullable UIEvent *)event {
    [self event:event action:ACTION_ActionCancel];
}

@end
