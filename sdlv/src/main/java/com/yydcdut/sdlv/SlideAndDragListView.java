package com.yydcdut.sdlv;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListAdapter;

/**
 * Created by yuyidong on 15/9/28.
 */
public class SlideAndDragListView<T> extends DragListView<T> implements OnAdapterSlideListenerProxy,
        OnAdapterButtonClickListenerProxy, AbsListView.OnScrollListener, Handler.Callback {
    /* item的btn的最大个数 */
    private static final int ITEM_BTN_NUMBER_MAX = 3;
    /* Handler 的 Message 信息 */
    private static final int MSG_WHAT_LONG_CLICK = 1;
    /* Handler 发送message需要延迟的时间 */
    private static final long CLICK_LONG_TRIGGER_TIME = 1000;//1s
    /* onTouch里面的状态 */
    private static final int STATE_NOTHING = -1;//抬起状态
    private static final int STATE_DOWN = 0;//按下状态
    private static final int STATE_LONG_CLICK = 1;//长点击状态
    private static final int STATE_SCROLL = 2;//SCROLL状态
    private static final int STATE_LONG_CLICK_FINISH = 3;//长点击已经触发完成
    private int mState = STATE_NOTHING;
    /* 振动 */
    private Vibrator mVibrator;
    /* handler */
    private Handler mHandler;
    /* 是否要触发itemClick */
    private boolean mIsWannaTriggerClick = true;
    /* 手指放下的坐标 */
    private int mXDown;
    private int mYDown;
    /* Attrs */
    private AttrsHolder mAttrsHolder;
    /* WrapperAdapter */
    private WrapperAdapter mWrapperAdapter;
    /* 监听器 */
    private OnSlideListener mOnSlideListener;
    private OnButtonClickListener mOnButtonClickListener;
    private OnListItemLongClickListener mOnListItemLongClickListener;
    private OnListItemClickListener mOnListItemClickListener;
    private OnScrollListenerProxy mOnScrollListenerProxy;

    public SlideAndDragListView(Context context) {
        this(context, null);
    }

    public SlideAndDragListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlideAndDragListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        //-------------------------- attrs --------------------------
        mAttrsHolder = new AttrsHolder();
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.sdlv, defStyleAttr, 0);
        mAttrsHolder.itemHeight = a.getDimension(R.styleable.sdlv_item_height, getContext().getResources().getDimension(R.dimen.slv_item_height));
        mAttrsHolder.itemBackGroundDrawable = a.getDrawable(R.styleable.sdlv_item_background);
        mAttrsHolder.btnWidth = a.getDimension(R.styleable.sdlv_item_btn_width, getContext().getResources().getDimension(R.dimen.slv_item_bg_btn_width));
        mAttrsHolder.btnNumber = a.getInt(R.styleable.sdlv_item_btn_number, 3);
        if (mAttrsHolder.btnNumber > ITEM_BTN_NUMBER_MAX || mAttrsHolder.btnNumber < 0) {
            throw new IllegalArgumentException("The number of Item buttons should be in between 0 and 3 !");
        }
        //如果btn的宽度超过了屏幕的宽度，则bug
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        if (screenWidth < mAttrsHolder.btnWidth * mAttrsHolder.btnNumber) {
            throw new IllegalArgumentException("The total width of Item buttons is longer than screen's width !");
        }
        mAttrsHolder.btn1Text = a.getString(R.styleable.sdlv_item_btn1_text);
        mAttrsHolder.btn2Text = a.getString(R.styleable.sdlv_item_btn2_text);
        mAttrsHolder.btn3Text = a.getString(R.styleable.sdlv_item_btn3_text);
        checkBtnText();
        mAttrsHolder.btn1Drawable = a.getDrawable(R.styleable.sdlv_item_btn1_background);
        mAttrsHolder.btn2Drawable = a.getDrawable(R.styleable.sdlv_item_btn2_background);
        mAttrsHolder.btn3Drawable = a.getDrawable(R.styleable.sdlv_item_btn3_background);
        mAttrsHolder.btnTextSize = a.getDimension(R.styleable.sdlv_item_btn_text_size, getContext().getResources().getDimension(R.dimen.txt_size));
        mAttrsHolder.btnTextColor = a.getColor(R.styleable.sdlv_item_btn_text_color, getContext().getResources().getColor(android.R.color.white));
        a.recycle();
        //-------------------------- attrs --------------------------
        mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        mHandler = new Handler(this);
        setOnScrollListener(this);
    }

    /**
     * 显示的text数据应该依次的，而不是跳级的
     */
    public void checkBtnText() {
        if (!TextUtils.isEmpty(mAttrsHolder.btn2Text) && TextUtils.isEmpty(mAttrsHolder.btn1Text)) {
            throw new IllegalArgumentException("The \'item_btn2_text\' has value, but \'item_btn1_text\' dose not have value!");
        }
        if (!TextUtils.isEmpty(mAttrsHolder.btn3Text) && TextUtils.isEmpty(mAttrsHolder.btn2Text)) {
            throw new IllegalArgumentException("The \'item_btn3_text\' has value, but \'item_btn2_text\' dose not have value!");
        }
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_WHAT_LONG_CLICK:
                if (mState == STATE_LONG_CLICK) {//如果得到msg的时候state状态是Long Click的话
                    //改为long click触发完成
                    mState = STATE_LONG_CLICK_FINISH;
                    //得到长点击的位置
                    int position = msg.arg1;
                    //找到那个位置的view
                    View view = getChildAt(position - getFirstVisiblePosition());
                    //如果设置了监听器的话，就触发
                    if (mOnListItemLongClickListener != null) {
                        mVibrator.vibrate(100);
                        mOnListItemLongClickListener.onListItemLongClick(view, position);
                    }
                    boolean canDrag = scrollBackByDrag(position);
                    if (canDrag) {
                        setDragPosition(position);
                    }
                }
                break;
        }
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                //获取出坐标来
                mXDown = (int) ev.getX();
                mYDown = (int) ev.getY();
                //当前state状态为按下
                mState = STATE_DOWN;
                break;
            case MotionEvent.ACTION_MOVE:
                if (fingerNotMove(ev) && mState != STATE_SCROLL) {//手指的范围在50以内
                    sendLongClickMessage(pointToPosition(mXDown, mYDown));
                    mState = STATE_LONG_CLICK;
                } else if (fingerLeftAndRightMove(ev)) {//上下范围在50，主要检测左右滑动
                    removeLongClickMessage();
                    mState = STATE_SCROLL;
                    //将当前想要滑动哪一个传递给wrapperAdapter
                    int position = pointToPosition(mXDown, mYDown);
                    if (position != AdapterView.INVALID_POSITION) {
                        mWrapperAdapter.setSlideItemPosition(position);
                    }
                    //将事件传递下去
                    return super.dispatchTouchEvent(ev);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (mState == STATE_DOWN || mState == STATE_LONG_CLICK) {
                    int position = pointToPosition(mXDown, mYDown);
                    //是否ScrollBack了，是的话就不去执行onListItemClick操作了
                    boolean bool = scrollBack(position);
                    if (bool) {
                        removeLongClickMessage();
                        mState = STATE_NOTHING;
                        break;
                    }
                    if (mOnListItemClickListener != null && mIsWannaTriggerClick) {
                        View v = getChildAt(position - getFirstVisiblePosition());
                        mOnListItemClickListener.onListItemClick(v, position);
                    }
                }
                removeLongClickMessage();
                mState = STATE_NOTHING;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 将滑开的item归位
     *
     * @param position
     * @return true--->有归位操作，false--->没有归位操作，也就是没有滑开的item
     */
    private boolean scrollBack(int position) {
        //是不是当前滑开的这个
        if (mWrapperAdapter.getSlideItemPosition() == position) {
            return true;
        } else if (mWrapperAdapter.getSlideItemPosition() != -1) {
            mWrapperAdapter.returnSlideItemPosition();
            return true;
        }
        return false;
    }

    /**
     * 用于drag的ScrollBack逻辑操作
     *
     * @param position
     * @return true--->可以drag false--->不能drag
     */
    private boolean scrollBackByDrag(int position) {
        //是不是当前滑开的这个
        if (mWrapperAdapter.getSlideItemPosition() == position) {
            return false;
        } else if (mWrapperAdapter.getSlideItemPosition() != -1) {
            mWrapperAdapter.returnSlideItemPosition();
            return true;
        }
        return true;
    }

    /**
     * remove掉message
     */
    private void removeLongClickMessage() {
        if (mHandler.hasMessages(MSG_WHAT_LONG_CLICK)) {
            mHandler.removeMessages(MSG_WHAT_LONG_CLICK);
        }
    }

    /**
     * sendMessage
     */
    private void sendLongClickMessage(int position) {
        if (!mHandler.hasMessages(MSG_WHAT_LONG_CLICK)) {
            Message message = new Message();
            message.what = MSG_WHAT_LONG_CLICK;
            message.arg1 = position;
            mHandler.sendMessageDelayed(message, CLICK_LONG_TRIGGER_TIME);
        }
    }

    /**
     * 上下左右不能超出50
     *
     * @param ev
     * @return
     */
    private boolean fingerNotMove(MotionEvent ev) {
        return (mXDown - ev.getX() < 25 && mXDown - ev.getX() > -25 &&
                mYDown - ev.getY() < 25 && mYDown - ev.getY() > -25);
    }

    /**
     * 左右得超出50，上下不能超出50
     *
     * @param ev
     * @return
     */
    private boolean fingerLeftAndRightMove(MotionEvent ev) {
        return ((ev.getX() - mXDown > 25 || ev.getX() - mXDown < -25) &&
                ev.getY() - mYDown < 25 && ev.getY() - mYDown > -25);
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        mWrapperAdapter = new WrapperAdapter(getContext(), this, adapter, mAttrsHolder);
        mWrapperAdapter.setOnAdapterSlideListenerProxy(this);
        mWrapperAdapter.setOnAdapterButtonClickListenerProxy(this);
        setRawAdapter(adapter);
        super.setAdapter(mWrapperAdapter);
    }

    /**
     * 设置item滑动监听器
     *
     * @param listener
     */
    public void setOnSlideListener(OnSlideListener listener) {
        mOnSlideListener = listener;
    }

    /**
     * item的滑动的监听器
     */
    public interface OnSlideListener {
        /**
         * 当滑动开的时候触发
         *
         * @param view
         * @param parentView
         * @param position
         */
        void onSlideOpen(View view, View parentView, int position);

        /**
         * 当滑动归位的时候触发
         *
         * @param view
         * @param parentView
         * @param position
         */
        void onSlideClose(View view, View parentView, int position);
    }

    @Override
    public void onSlideOpen(View view, int position) {
        if (mOnSlideListener != null) {
            mOnSlideListener.onSlideOpen(view, this, position);
        }
    }

    @Override
    public void onSlideClose(View view, int position) {
        if (mOnSlideListener != null) {
            mOnSlideListener.onSlideClose(view, this, position);
        }
    }

    /**
     * 设置item中的button点击事件的监听器
     *
     * @param onButtonClickListener
     */
    public void setOnButtonClickListener(OnButtonClickListener onButtonClickListener) {
        mOnButtonClickListener = onButtonClickListener;
    }

    /**
     * item中的button监听器
     */
    public interface OnButtonClickListener {
        /**
         * 点击事件
         *
         * @param v
         * @param itemPosition   第几个item
         * @param buttonPosition 第几个button
         */
        void onClick(View v, int itemPosition, int buttonPosition);
    }

    @Override
    public void onClick(View v, int itemPosition, int buttonPosition) {
        if (mOnButtonClickListener != null) {
            mOnButtonClickListener.onClick(v, itemPosition, buttonPosition);
        }
    }

    @Deprecated
    @Override
    public void setOnItemClickListener(OnItemClickListener listener) {
    }

    /**
     * 设置监听器
     *
     * @param listener
     */
    public void setOnListItemClickListener(OnListItemClickListener listener) {
        mOnListItemClickListener = listener;
    }

    /**
     * 自己的单击事件
     */
    public interface OnListItemClickListener {
        void onListItemClick(View v, int position);
    }

    @Deprecated
    @Override
    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
    }

    /**
     * 设置监听器
     *
     * @param listener
     */
    public void setOnListItemLongClickListener(OnListItemLongClickListener listener) {
        mOnListItemLongClickListener = listener;
    }

    /**
     * 自己写的长点击事件
     */
    public interface OnListItemLongClickListener {
        void onListItemLongClick(View view, int position);
    }

    /**
     * 设置滑动的监听器
     *
     * @param onScrollListenerProxy
     */
    public void setOnScrollListenerProxy(OnScrollListenerProxy onScrollListenerProxy) {
        mOnScrollListenerProxy = onScrollListenerProxy;
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (mOnScrollListenerProxy != null) {
            mOnScrollListenerProxy.onScrollStateChanged(view, scrollState);
        }
        if (scrollState == SCROLL_STATE_IDLE) {
            mIsWannaTriggerClick = true;
        } else {
            mIsWannaTriggerClick = false;
        }
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mOnScrollListenerProxy != null) {
            mOnScrollListenerProxy.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }
}
