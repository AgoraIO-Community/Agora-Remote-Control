package io.agora.openlive.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import io.agora.openlive.R;
import io.agora.openlive.model.ConstantApp;
import io.agora.openlive.model.VideoStatusData;


public class GridVideoViewContainerAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final static Logger log = LoggerFactory.getLogger(GridVideoViewContainerAdapter.class);

    protected final LayoutInflater mInflater;
    protected final Context mContext;
    private HashMap<Integer, VideoStatusData> mAllUserData;

    private ArrayList<VideoStatusData> mUsers;

    public GridVideoViewContainerAdapter(Context context, int localUid, HashMap<Integer, SurfaceView> uids) {
        mContext = context;
        mInflater = ((Activity) context).getLayoutInflater();

        mUsers = new ArrayList<>();

        init(uids, localUid, false);
    }

    protected int mItemWidth;
    protected int mItemHeight;

    private int mLocalUid;

    public void setLocalUid(int uid) {
        mLocalUid = uid;
    }

    public int getLocalUid() {
        return mLocalUid;
    }

    private int getRealDisplayMetrics(Context context, DisplayMetrics displayMetrics) {
        if(context == null){
            return -1;
        }
        WindowManager windowManager  = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            display.getRealMetrics(displayMetrics);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            try {
                displayMetrics.widthPixels = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
                displayMetrics.heightPixels = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
            } catch (Exception e) {
                display.getMetrics(displayMetrics);
            }
        }
        return 0;
    }


    public void init(HashMap<Integer, SurfaceView> uids, int localUid, boolean force) {
        for (HashMap.Entry<Integer, SurfaceView> entry : uids.entrySet()) {
            if (entry.getKey() == 0 || entry.getKey() == mLocalUid) {
                boolean found = false;
                for (VideoStatusData status : mUsers) {
                    if ((status.mUid == entry.getKey() && status.mUid == 0) || status.mUid == mLocalUid) { // first time
                        status.mUid = mLocalUid;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mUsers.add(0, new VideoStatusData(mLocalUid, entry.getValue(), VideoStatusData.DEFAULT_STATUS, VideoStatusData.DEFAULT_VOLUME));
                }
            } else {
                boolean found = false;
                for (VideoStatusData status : mUsers) {
                    if (status.mUid == entry.getKey()) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    mUsers.add(new VideoStatusData(entry.getKey(), entry.getValue(), VideoStatusData.DEFAULT_STATUS, VideoStatusData.DEFAULT_VOLUME));
                }
            }
        }

        Iterator<VideoStatusData> it = mUsers.iterator();
        while (it.hasNext()) {
            VideoStatusData status = it.next();

            if (uids.get(status.mUid) == null) {
                log.warn("after_changed remove not exited members " + (status.mUid & 0xFFFFFFFFL) + " " + status.mView);
                it.remove();
            }
        }

        if (force || mItemWidth == 0 || mItemHeight == 0) {
            DisplayMetrics outMetrics = new DisplayMetrics();
            getRealDisplayMetrics(mContext, outMetrics);
            log.debug("videoViewContainer outMetrics width={} height={}",outMetrics.widthPixels, outMetrics.heightPixels);

            int count = uids.size();
            int DividerX = 1;
            int DividerY = 1;
            if (count == 2) {
                DividerY = 2;
            } else if (count >= 3) {
                DividerX = 2;
                DividerY = 2;
            }

            mItemWidth = outMetrics.widthPixels / DividerX;
            mItemHeight = outMetrics.heightPixels / DividerY;
            log.debug("my videoViewContainer mItemWidth={} mItemHeight={}",mItemWidth,mItemHeight);
        }
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = mInflater.inflate(R.layout.video_view_container, parent, false);
        v.getLayoutParams().width = mItemWidth;
        v.getLayoutParams().height = mItemHeight;
        log.debug("videoViewContainer videoViewContainer v.getLayoutParams().width={} v.getLayoutParams().height={}", v.getLayoutParams().width, v.getLayoutParams().height);
        return new VideoUserStatusHolder(v);
    }

    protected final void stripSurfaceView(SurfaceView view) {
        ViewParent parent = view.getParent();
        if (parent != null) {
            ((FrameLayout) parent).removeView(view);
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        VideoUserStatusHolder myHolder = ((VideoUserStatusHolder) holder);

        final VideoStatusData user = mUsers.get(position);

        log.debug("onBindViewHolder " + position + " " + user + " " + myHolder + " " + myHolder.itemView);

        FrameLayout holderView = (FrameLayout) myHolder.itemView;

        if(!myHolder.getIsUsed()){
            SurfaceView target = user.mView;
            target.setZOrderMediaOverlay(true);
            stripSurfaceView(target);
            holderView.addView(target,0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            myHolder.setIsUsed(true);
        }
        VideoViewAdapterUtil.updateUIData(myHolder,user,mAllUserData);
    }

    @Override
    public int getItemCount() {
        int sizeLimit = mUsers.size();
        if (sizeLimit >= ConstantApp.MAX_PEER_COUNT + 1) {
            sizeLimit = ConstantApp.MAX_PEER_COUNT + 1;
        }
        return sizeLimit;
    }

    public VideoStatusData getItem(int position) {
        return mUsers.get(position);
    }

    @Override
    public long getItemId(int position) {
        VideoStatusData user = mUsers.get(position);

        SurfaceView view = user.mView;
        if (view == null) {
            throw new NullPointerException("SurfaceView destroyed for user " + user.mUid + " " + user.mStatus + " " + user.mVolume);
        }

        return (String.valueOf(user.mUid) + System.identityHashCode(view)).hashCode();
    }


    public void notifyDataChange(HashMap<Integer,VideoStatusData> mAllUserData){
        this.mAllUserData = mAllUserData;
        this.notifyDataSetChanged();
    }
}
