package com.floatview;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.support.annotation.Dimension;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.floatview.demo.R;

import java.util.Timer;
import java.util.TimerTask;


/**
 * 游戏悬浮窗视图
 */

public class FloatView extends FrameLayout implements MenuViewPopupWindow.MenuItemClick,MenuViewPopupWindow.IconOnClick,View.OnTouchListener{

    // 悬浮栏位置
    private final static int LEFT = 0;
    private final static int RIGHT = 1;
    private final static int TOP = 3;
    private final static int BUTTOM = 4;

    private int dpi;
    private int screenHeight;
    private int screenWidth;
    private WindowManager.LayoutParams wmParams;
    private WindowManager wm;
    private float eventDownX, eventDownY;
    private float eventMoveX, eventMoveY;
    private float eventDownStartX;
    private float eventDownStartY;
    private float eventUpEndX;
    private float eventUpEndY;
    private volatile boolean hasEventDown;//判断是否有ACTION_DOWN事件触发的flag
    private volatile boolean isScroll;//判断悬浮窗口是否移动的flag
    private volatile boolean isLongClick;//判断触摸事件是否长按的flag
    private Context mContext = null;
    private int[] location = new int[2];//悬浮窗口在屏幕的x、y位置
    private ImageView mFloatIcon;//悬浮窗图片icon
    private int mFloatX,mFloatY;
    private volatile AnimatorSet animatorSet = null;//组合动画

    private int speed = 10;//移动速度
    private int timeValue = 0; //移动计步
    private volatile boolean isRunSide = false;//（开始/停止）平移悬浮窗flag
    private volatile boolean isAutoHideRun = true;//（开始/停止）隐藏悬浮窗flag
    private Timer mTimer;//平移悬浮窗定时器
    private Timer autohideTimer = null;//隐藏悬浮窗定时器
    private Toast mToast = null;

    private static FloatView instance = null;//单例模式
    public static FloatView getInstance(final Context context){
        if(instance == null) {
            synchronized (FloatView.class) {
                if(instance == null) {
                    instance = new FloatView(context);
                }
            }
        }
        return instance;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public FloatView(Context context) {
        super(context);
        mContext = context;
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);
        //通过像素密度来设置按钮的大小
        dpi = dpi(dm.densityDpi);
         //屏幕宽度（包含虚拟键的整体屏幕宽度）
        screenWidth = ScreenUtils.getHasVirtualWidth(context);
        //屏幕高度（包含虚拟键的整体屏幕高度）
        screenHeight = ScreenUtils.getHasVirtualHight(context);
        //布局设置
        wmParams = new WindowManager.LayoutParams();
        // 设置window type
        wmParams.type = WindowManager.LayoutParams.TYPE_APPLICATION;
        wmParams.format = PixelFormat.RGBA_8888; // 设置图片格式，效果为背景透明
        wmParams.gravity = 51;
        // 设置Window flag   WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION：虚拟按键透明   WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS：标题栏透明
        wmParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
                        | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
        wmParams.width = LayoutParams.WRAP_CONTENT;
        wmParams.height = LayoutParams.WRAP_CONTENT;
        getStartPosition();
        wmParams.x = mFloatX;
        wmParams.y = mFloatY;
        addView(createView(context));
        setOnTouchListener(this);
    }

    /**
     * 获取上次保存的最后位置作为开始位置显示
     */
    private void getStartPosition(){
        try{
            SharedPreferences sharedPreferences = mContext.getSharedPreferences("location", Context.MODE_PRIVATE);
            String strPoint = sharedPreferences.getString("float_location", "");
            mFloatX = 0;
            mFloatY = screenHeight / 2;
            if(!strPoint.equals("")) {
                String[] po = strPoint.split("\\|");
                try {
                    mFloatX = Integer.parseInt(po[0]);
                    mFloatY = Integer.parseInt(po[1]);
                }catch (NumberFormatException e){
                    mFloatX = 0;
                    mFloatY = screenHeight / 2;
                }
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
    }

    /**
     * 创建Float view
     * @param context
     * @return
     */
    private View createView(final Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        // 从布局文件获取浮动窗口视图
        View rootFloatView = inflater.inflate(R.layout.float_view, null);
        mFloatIcon = (ImageView) rootFloatView.findViewById(R.id.iv_floatview);
        //设置icon
        mFloatIcon.setImageResource(R.drawable.float_logo);
        ViewGroup.LayoutParams params = mFloatIcon.getLayoutParams();
        params.width = CommonUtils.dip2px(context, 58);
        params.height = CommonUtils.dip2px(context, 58);
        mFloatIcon.setLayoutParams(params);
        rootFloatView.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return rootFloatView;
    }

    /**
     * 根据密度选择控件大小
     */
    private int dpi(int densityDpi) {
        if (densityDpi <= 120) {
            return 36;
        } else if (densityDpi <= 160) {
            return 48;
        } else if (densityDpi <= 240) {
            return 72;
        } else if (densityDpi <= 320) {
            return 96;
        }
        return 108;
    }

    /**
     * 显示悬浮窗
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void show() {
        if(SupportAndroidVersion()) {
            if (isShown()) {
                return;
            }
            if(wm != null)
                wm.addView(this, wmParams);
            //通过屏幕的宽度和高度的大小比较判别
            if(screenWidth < screenHeight) {//竖屏
                if (wmParams.x < screenWidth / 2 - getMeasuredWidth() / 2) {
                    autoHideViewToSide(LEFT, 3000);
                } else {
                    autoHideViewToSide(RIGHT, 3000);
                }
            }else {//横屏
                if (wmParams.x <= screenWidth / 4 || (wmParams.y >= screenHeight / 2 && wmParams.x <= screenWidth / 2)) {
                    autoHideViewToSide(LEFT, 3000);
                } else if (wmParams.x >= screenWidth * 3 / 4 || (wmParams.y >= screenHeight / 2 && wmParams.x > screenWidth / 2)) {
                    autoHideViewToSide(RIGHT, 3000);
                } else {
                    autoHideViewToSide(TOP, 3000);
                }
            }
        }
    }

    /**
     * 销毁悬浮窗
     */
    public void destory() {
        if(isShown()) {
            if (wm != null)
                wm.removeViewImmediate(this);
        }
        instance = null;
    }

    /**
     * 判断当前设备android版本是否符合要求
     * @return boolean
     */
    public boolean SupportAndroidVersion() {
        int curApiVersion = Build.VERSION.SDK_INT;
        // This work only for android 4.0+
        if (curApiVersion >= 14) {
            return true;
        }
        return false;
    }

    /**
     * 悬浮窗Touch事件
     * @param v
     * @param event
     * @return
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        // 获取相对屏幕的坐标， 以屏幕左上角为原点
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                hasEventDown = true;
                /*
                 * 触发ACTION_DOWN把所有的作用及效果全部取消并复原
                 */
                if(mTimer != null){
                    isRunSide = false;
                    mTimer.cancel();
                    mTimer = null;
                }
                if(autohideTimer != null){
                    isAutoHideRun = false;
                    autohideTimer.cancel();
                    autohideTimer = null;
                }
                eventDownX = event.getX();
                eventDownY = event.getY();
                eventDownStartX = event.getRawX();
                eventDownStartY = event.getRawY();

                if(animatorSet != null && (animatorSet.isStarted() || animatorSet.isRunning())) {
                    animatorSet.end();
                    animatorSet.cancel();
                    animatorSet = null;
                }
                mFloatIcon.setAlpha(1.0f);
                ObjectAnimator translationAnimator = null;
                //得到view在屏幕中的位置
                v.getLocationOnScreen(location);
                //判断位于屏幕的位置
                if(screenWidth < screenHeight) {//竖屏
                    if (location[0] < screenWidth / 2 - getMeasuredWidth() / 2) {
                        translationAnimator = new ObjectAnimator().ofFloat(mFloatIcon,"translationX",-v.getMeasuredWidth()*2/3,0);//左侧
                    } else {
                        translationAnimator = new ObjectAnimator().ofFloat(mFloatIcon,"translationX",v.getMeasuredWidth()*2/3,0);//右侧
                    }
                }else {//横屏
                    if (location[0] <= screenWidth / 4 || (location[1] >= screenHeight / 2 && location[0] <= screenWidth / 2)) {
                        translationAnimator = new ObjectAnimator().ofFloat(mFloatIcon,"translationX",-v.getMeasuredWidth()*2/3,0);//左侧
                    } else if (location[0] >= screenWidth * 3 / 4 || (location[1] >= screenHeight / 2 && location[0] > screenWidth / 2)) {
                        translationAnimator = new ObjectAnimator().ofFloat(mFloatIcon,"translationX",v.getMeasuredWidth()*2/3,0);//右侧
                    } else {
                        translationAnimator = new ObjectAnimator().ofFloat(mFloatIcon,"translationY",-v.getMeasuredWidth()*2/3,0);//上侧
                    }
                }
                animatorSet = new AnimatorSet();  //组合动画
                animatorSet.playTogether(translationAnimator); //设置动画
                animatorSet.setDuration(0);  //设置动画时间
                animatorSet.start(); //启动
                break;
            case MotionEvent.ACTION_MOVE:
                if(!hasEventDown)
                    return false;
                eventMoveX = event.getRawX();
                eventMoveY = event.getRawY();
                if(!isLongClick) {
                    isLongClick = isLongPressed(event.getDownTime(), event.getEventTime(), 1000);
                }
                if(isScroll || isLongClick || (Math.abs(eventMoveX - eventDownStartX) + Math.abs(eventMoveY - eventDownStartY)) >= (v.getMeasuredWidth()+v.getMeasuredHeight())/3) {
                    isScroll = true;
                    updateViewPosition();
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(!hasEventDown)
                    return false;
                eventUpEndX = event.getRawX();
                eventUpEndY = event.getRawY();
                v.getLocationOnScreen(location);
                if(!isScroll && ((Math.abs(eventUpEndX - eventDownStartX) + Math.abs(eventUpEndY - eventDownStartY)) < (v.getMeasuredWidth()+v.getMeasuredHeight())/3)){
                    v.getLocationOnScreen(location);
                    //判断位于屏幕的位置
                    if(screenWidth < screenHeight) {//竖屏
                        if((location[0] == 0 || location[0] == (screenWidth - getMeasuredWidth()))) {
                            if (location[0] < screenWidth / 2 - getMeasuredWidth() / 2) {//左侧
                                MenuViewPopupWindow.getInstance(mContext).setMenuView(R.layout.float_left_menu_view).setViewAnimationStyle("").
                                        addIconOnClick(this).addMenuItemClick(this).showAtLocation(v, Gravity.NO_GRAVITY, v.getLeft(), v.getTop());
                            } else {//右侧
                                MenuViewPopupWindow.getInstance(mContext).setMenuView(R.layout.float_right_menu_view).setViewAnimationStyle("").
                                        addIconOnClick(this).addMenuItemClick(this).showAtLocation(v, Gravity.NO_GRAVITY, v.getRight() - MenuViewPopupWindow.getInstance(mContext).getWidth(), v.getTop());
                            }
                        }
                    }else {//横屏
                        if ((location[0] == 0 || location[0] == (screenWidth - getMeasuredWidth())) && (location[0] <= screenWidth / 4 || (location[1] >= screenHeight / 2 && location[0] <= screenWidth / 2))) {//左侧
                            MenuViewPopupWindow.getInstance(mContext).setMenuView(R.layout.float_left_menu_view).setViewAnimationStyle("").
                                    addIconOnClick(this).addMenuItemClick(this).showAtLocation(v, Gravity.NO_GRAVITY, v.getLeft(), v.getTop());
                        } else if ((location[0] == 0 || location[0] == (screenWidth - getMeasuredWidth())) && (location[0] >= screenWidth * 3 / 4 || (location[1] >= screenHeight / 2 && location[0] > screenWidth / 2))) {//右侧
                            MenuViewPopupWindow.getInstance(mContext).setMenuView(R.layout.float_right_menu_view).setViewAnimationStyle("").
                                    addIconOnClick(this).addMenuItemClick(this).showAtLocation(v, Gravity.NO_GRAVITY, v.getRight() - MenuViewPopupWindow.getInstance(mContext).getWidth(), v.getTop());
                        } else {//上侧
                            if(location[1] == 0) {
                                if (location[0] <= screenWidth / 2) {//从右侧显示菜单
                                    MenuViewPopupWindow.getInstance(mContext).setMenuView(R.layout.float_left_menu_view).setViewAnimationStyle("").
                                            addIconOnClick(this).addMenuItemClick(this).showAtLocation(v, Gravity.NO_GRAVITY, v.getLeft(), v.getTop());
                                } else {//从左侧显示菜单
                                    MenuViewPopupWindow.getInstance(mContext).setMenuView(R.layout.float_right_menu_view).setViewAnimationStyle("").
                                            addIconOnClick(this).addMenuItemClick(this).showAtLocation(v, Gravity.NO_GRAVITY, v.getRight() - MenuViewPopupWindow.getInstance(mContext).getWidth(), v.getTop());
                                }
                            }
                        }
                    }
                    autoViewRunToSide(v,5000);
                }else{
                    autoViewRunToSide(v,3000);
                }
                isScroll = false;
                isLongClick = false;
                hasEventDown = false;
                break;
        }
        return false;
    }

    /**
     * 判断是否有长按动作发生
     * @param lastDownTime 按下时间
     * @param thisEventTime 移动时间
     * @param longPressTime 判断长按时间的阀值
    */
    private boolean isLongPressed(long lastDownTime, long thisEventTime, long longPressTime){
        long intervalTime = thisEventTime - lastDownTime;
        if(intervalTime >= longPressTime){
            return true;
        }
        return false;
    }

    /**
     * 更新悬浮窗窗口位置参数
     */
    private void updateViewPosition() {
        wmParams.x = (int) (eventMoveX - eventDownX );
        wmParams.y = (int) (eventMoveY - eventDownY );
        wm.updateViewLayout(this, wmParams);
    }

    /**
     * 自动移动位置
     */
    private void autoViewRunToSide(View v, int autohideTime) {
        if(v == null)
            return;
        //得到view在屏幕中的位置
        v.getLocationOnScreen(location);
        //判断位于屏幕的位置
        if(screenWidth < screenHeight) {//竖屏
            if (location[0] < screenWidth / 2 - v.getMeasuredWidth() / 2) {
                RunToSide(LEFT,autohideTime);//左侧
            } else {
                RunToSide(RIGHT,autohideTime);//右侧
            }
        }else {//横屏
            if (location[0] <= screenWidth / 4 || (location[1] >= screenHeight / 2 && location[0] <= screenWidth / 2)) {//左侧
                RunToSide(LEFT, autohideTime);
            } else if (location[0] >= screenWidth * 3 / 4 || (location[1] >= screenHeight / 2 && location[0] > screenWidth / 2)) {//右侧
                RunToSide(RIGHT, autohideTime);
            } else {//上侧
                RunToSide(TOP, autohideTime);
            }
        }
    }

    /**
     * 自动隐藏悬浮窗
     * @param side
     * @param time
     */
    private void autoHideViewToSide(final int side,int time){
        TimerTask autohideTimerTask = new TimerTask() {
            @Override
            public void run() {
                if(!isAutoHideRun) {
                    if(autohideTimer != null){
                        autohideTimer.cancel();
                        autohideTimer = null;
                    }
                    this.cancel();
                    return;
                }
                ((Activity)mContext).runOnUiThread(new Runnable() {
                    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                    @Override
                    public void run() {
                        if(!isAutoHideRun)
                            return;
                        mFloatIcon.setAlpha(0.7f);
                        if(animatorSet != null && (animatorSet.isStarted() || animatorSet.isRunning())) {
                            animatorSet.end();
                            animatorSet.cancel();
                            animatorSet = null;
                        }
                        ObjectAnimator animatorTranslation = null;
                        ObjectAnimator animatorRotation = null;
                        switch (side){
                            case LEFT:
                                animatorTranslation = new ObjectAnimator().ofFloat(mFloatIcon,"translationX",0,-mFloatIcon.getMeasuredWidth()*2/3);
                                animatorRotation = new ObjectAnimator().ofFloat(mFloatIcon,"rotation",0,-360);
                                break;
                            case RIGHT:
                                animatorTranslation = new ObjectAnimator().ofFloat(mFloatIcon,"translationX",0,mFloatIcon.getMeasuredWidth()*2/3);
                                animatorRotation = new ObjectAnimator().ofFloat(mFloatIcon,"rotation",0,360);
                                break;
                            case TOP:
                                animatorTranslation = new ObjectAnimator().ofFloat(mFloatIcon,"translationY",0,-mFloatIcon.getMeasuredWidth()*2/3);
                                animatorRotation = new ObjectAnimator().ofFloat(mFloatIcon,"rotation",0,-360);
                                break;
                            case BUTTOM:
                                break;
                        }
                        animatorSet = new AnimatorSet();  //组合动画
                        animatorSet.playTogether(animatorTranslation,animatorRotation); //设置动画
                        animatorSet.setDuration(1000);  //设置动画时间
                        animatorSet.start(); //启动
                    }
                });
            }
        };
        isAutoHideRun = true;
        autohideTimer = new Timer();
        autohideTimer.schedule(autohideTimerTask,time);//隐藏浮标：延迟时间为time（秒）
    }

    /**
     * 手指释放更新悬浮窗位置
     */
    public void RunToSide(final int side,final int autohideTime){
        if(side != TOP && screenWidth > screenHeight)//横屏且非上侧
            speed = 15;
        else
            speed = 10;
        int runLen = 0;
        switch (side){
            case LEFT:
                runLen = location[0];
                break;
            case RIGHT:
                runLen = screenWidth - location[0];
                break;
            case TOP:
                runLen = location[1];
                break;
            case BUTTOM:
                break;
        }
        timeValue = runLen/speed;

        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if(!isRunSide) {
                    if(mTimer != null){
                        mTimer.cancel();
                        mTimer = null;
                    }
                    this.cancel();
                    return;
                }
                timeValue--;
                if (timeValue >= 0) {
                    switch (side){
                        case LEFT:
                            wmParams.x = timeValue * speed;
                            break;
                        case RIGHT:
                            wmParams.x = screenWidth - timeValue*speed;
                            break;
                        case TOP:
                            wmParams.y = timeValue * speed;
                            break;
                        case BUTTOM:
                            break;
                    }
                    ((Activity)mContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(!isRunSide)
                                return;
                            wm.updateViewLayout(FloatView.this,wmParams);
                        }
                    });
                }else{
                    isRunSide = false;
                    if(mTimer!=null) {
                        mTimer.cancel();
                    }
                    switch (side){
                        case LEFT:
                            wmParams.x = 0;
                            break;
                        case RIGHT:
                            wmParams.x = screenWidth - getMeasuredWidth();
                            break;
                        case TOP:
                            wmParams.y = 0;
                            break;
                        case BUTTOM:
                            break;
                    }
                    ((Activity)mContext).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            wm.updateViewLayout(FloatView.this,wmParams);
                            SharedPreferences sharedPreferences = mContext.getSharedPreferences("location", Context.MODE_PRIVATE);
                            SharedPreferences.Editor edit = sharedPreferences.edit();
                            edit.putString("float_location", wmParams.x + "|" + wmParams.y);
                            edit.commit();
                        }
                    });
                    TimerTask autohideTimerTask = new TimerTask() {
                        @Override
                        public void run() {
                            if(!isAutoHideRun) {
                                if(autohideTimer != null){
                                    autohideTimer.cancel();
                                    autohideTimer = null;
                                }
                                this.cancel();
                                return;
                            }
                            ((Activity)mContext).runOnUiThread(new Runnable() {
                                @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                                @Override
                                public void run() {
                                    if(!isAutoHideRun)
                                        return;
                                    mFloatIcon.setAlpha(0.7f);
                                    MenuViewPopupWindow.getInstance(mContext).removeMenuItemClick().removeIconOnClick().Dismiss();
                                    if(animatorSet != null && (animatorSet.isStarted() || animatorSet.isRunning())) {
                                        animatorSet.end();
                                        animatorSet.cancel();
                                    }
                                    ObjectAnimator animatorTranslation = null;
                                    ObjectAnimator animatorRotation = null;
                                    switch (side){
                                        case LEFT:
                                            animatorTranslation = new ObjectAnimator().ofFloat(mFloatIcon,"translationX",0,-mFloatIcon.getMeasuredWidth()*2/3);
                                            animatorRotation = new ObjectAnimator().ofFloat(mFloatIcon,"rotation",0,-360);
                                            break;
                                        case RIGHT:
                                            animatorTranslation = new ObjectAnimator().ofFloat(mFloatIcon,"translationX",0,mFloatIcon.getMeasuredWidth()*2/3);
                                            animatorRotation = new ObjectAnimator().ofFloat(mFloatIcon,"rotation",0,360);
                                            break;
                                        case TOP:
                                            animatorTranslation = new ObjectAnimator().ofFloat(mFloatIcon,"translationY",0,-mFloatIcon.getMeasuredWidth()*2/3);
                                            animatorRotation = new ObjectAnimator().ofFloat(mFloatIcon,"rotation",0,-360);
                                            break;
                                        case BUTTOM:
                                            break;
                                    }
                                    animatorSet = new AnimatorSet();  //组合动画
                                    animatorSet.playTogether(animatorTranslation,animatorRotation); //设置动画
                                    animatorSet.setDuration(1000);  //设置动画时间
                                    animatorSet.start(); //启动
                                }
                            });
                        }
                    };
                    isAutoHideRun = true;
                    autohideTimer = new Timer();
                    autohideTimer.schedule(autohideTimerTask,autohideTime);//隐藏浮标：延迟autohideTime（秒）
                }
                ((Activity)mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FloatView.this.invalidate();
                    }
                });
            }
        };
        isRunSide = true;
        mTimer = new Timer();
        mTimer.schedule(task, 0, 15); // 平移动画效果：隔15毫秒执行一次
    }

    /**
     * 菜单展示后，点击icon事件回调，implement回调接口MenuViewPopupWindow.SDKIconOnClick
     */
    @Override
    public void OnClick() {
        if(mTimer != null){
            isRunSide = false;
            mTimer.cancel();
            mTimer = null;
        }
        if(autohideTimer != null){
            isAutoHideRun = false;
            autohideTimer.cancel();
            autohideTimer = null;
        }
        MenuViewPopupWindow.getInstance(mContext).removeMenuItemClick().removeIconOnClick().Dismiss();
        getLocationOnScreen(location);
        //判断位于屏幕的位置
        if(screenWidth < screenHeight) {//竖屏
            if (location[0] < screenWidth / 2 - getMeasuredWidth() / 2) {
                autoHideViewToSide(LEFT,3000);
            }else{
                autoHideViewToSide(RIGHT,3000);
            }
        }else {//横屏
            if (location[0] <= screenWidth / 4 || (location[1] >= screenHeight / 2 && location[0] <= screenWidth / 2)) {
                autoHideViewToSide(LEFT, 3000);
            } else if (location[0] >= screenWidth * 3 / 4 || (location[1] >= screenHeight / 2 && location[0] > screenWidth / 2)) {
                autoHideViewToSide(RIGHT, 3000);
            } else {
                autoHideViewToSide(TOP, 3000);
            }
        }
    }

    /**
     * 菜单选项item的选择回调，implement回调接口MenuViewPopupWindow.SDKMenuItemClick
     * @param position
     */
    @Override
    public void OnItemClick(final int position) {
        if(mTimer != null){
            isRunSide = false;
            mTimer.cancel();
            mTimer = null;
        }
        if(autohideTimer != null){
            isAutoHideRun = false;
            autohideTimer.cancel();
            autohideTimer = null;
        }
        MenuViewPopupWindow.getInstance(mContext).removeMenuItemClick().removeIconOnClick().Dismiss();
        getLocationOnScreen(location);
        //判断位于屏幕的位置
        if (screenWidth < screenHeight) {//竖屏
            if (location[0] < screenWidth / 2 - getMeasuredWidth() / 2) {
                autoHideViewToSide(LEFT, 0);
            } else {
                autoHideViewToSide(RIGHT, 0);
            }
        } else {//横屏
            if (location[0] <= screenWidth / 4 || (location[1] >= screenHeight / 2 && location[0] <= screenWidth / 2)) {
                autoHideViewToSide(LEFT, 0);
            } else if (location[0] >= screenWidth * 3 / 4 || (location[1] >= screenHeight / 2 && location[0] > screenWidth / 2)) {
                autoHideViewToSide(RIGHT, 0);
            } else {
                autoHideViewToSide(TOP, 0);
            }
        }

        try {
            ((Activity) mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    switch (position){
                        case 0:
                            showToast(mContext, "选项一", Gravity.CENTER, 3500);
                            break;
                        case 1:
                            showToast(mContext, "选项二", Gravity.CENTER, 3500);
                            break;
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 关闭悬浮窗口
     */
    private void closeFloatView(){
        destory();
    }

    /**
     * 提示信息
     * @param context
     * @param msg
     * @param gravity
     * @param time    Toast.LENGTH_SHORT(2000ms) <=  time <= Toast.LENGTH_LONG(3500ms)
     */
    private void showToast(Context context, String msg, int gravity, int time){
        RelativeLayout rl = new RelativeLayout(context);
        TextView tv_toast = new TextView(context);
        tv_toast.setText(msg);
        tv_toast.setTextColor(Color.WHITE);
        tv_toast.setTextSize(Dimension.SP, 18.0f);
        tv_toast.setPadding(CommonUtils.dip2px(context, 10), CommonUtils.dip2px(context, 10), CommonUtils.dip2px(context, 10), CommonUtils.dip2px(context, 10));
        rl.addView(tv_toast);
        rl.setBackgroundColor(Color.parseColor("#b0000000"));
        mToast = new Toast(context);
        mToast.setGravity(gravity, 0, 0);
        mToast.setDuration(time);
        mToast.setView(rl);
        if(mToast != null)
            mToast.show();
    }
}
