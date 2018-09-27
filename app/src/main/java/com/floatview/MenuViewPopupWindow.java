package com.floatview;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;

import com.floatview.demo.R;

/**
 * 菜单弹出窗
 */

public class MenuViewPopupWindow extends PopupWindow implements View.OnClickListener{
    private Context context;
    private View view;
    private ImageView iv_sdk_icon;
    private HorizontalListView listView;
    private int[] ImageIconList = new int[]{R.drawable.person, R.drawable.warn};
    private MenuItemClick menuItemClick = null;
    private IconOnClick iconOnClick = null;

    private static MenuViewPopupWindow instance = null;
    public static MenuViewPopupWindow getInstance(final Context context){
        if(instance == null){
            synchronized (MenuViewPopupWindow.class) {
                if(instance == null) {
                    instance = new MenuViewPopupWindow(context);
                }
            }
        }
        return instance;
    }

    public MenuViewPopupWindow addMenuItemClick(MenuItemClick itemClick){
        menuItemClick = itemClick;
        return this;
    }

    public MenuViewPopupWindow addIconOnClick(IconOnClick click){
        iconOnClick = click;
        return this;
    }

    public MenuViewPopupWindow removeMenuItemClick(){
        menuItemClick = null;
        return this;
    }

    public MenuViewPopupWindow removeIconOnClick(){
        iconOnClick = null;
        return this;
    }

    public MenuViewPopupWindow(Context context) {
        this(context, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public MenuViewPopupWindow(Context context, int with, int height) {
        this.context = context;
        setWidth(CommonUtils.dip2px(context, (ImageIconList.length + 1) * 58));
        setHeight(CommonUtils.dip2px(context, 58));
        //如果设置可以获得焦点,按返回键可以令菜单消失
        //setFocusable(true);
        //设置弹窗内可点击
        setTouchable(true);
        //设置弹窗外可点击
        setOutsideTouchable(true);
        //设置dismiss监听事件
        setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss() {
                instance = null;
            }
        });
        //允许弹出窗口超出屏幕范围。默认情况下，窗口被夹到屏幕边界。设置为false将允许Windows精确定位。
        if(Build.VERSION.SDK_INT >= 16) {
            setClippingEnabled(false);
        }
    }

    public MenuViewPopupWindow setMenuView(int viewLayout){
        view = LayoutInflater.from(context).inflate(viewLayout,null);
        setContentView(view);
        initData();
        return this;
    };

    private void initData() {
        iv_sdk_icon = (ImageView) view.findViewById(R.id.iv_sdk_icon);
        listView = (HorizontalListView) view.findViewById(R.id.lv_menu);
        ViewGroup.LayoutParams params = iv_sdk_icon.getLayoutParams();
        params.width = CommonUtils.dip2px(context,58);
        params.height = CommonUtils.dip2px(context,58);
        iv_sdk_icon.setLayoutParams(params);
        iv_sdk_icon.setImageResource(R.drawable.float_logo);
        //设置列表的适配器
        listView.setAdapter(listAdapter);
        iv_sdk_icon.setOnClickListener(this);
    }

    private BaseAdapter listAdapter = new BaseAdapter() {

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rootView = null;
            if (convertView == null) {
                rootView = View.inflate(context,R.layout.float_menu_view_list_item,null);
            } else {
                rootView = convertView;
            }
            RelativeLayout ll_item = (RelativeLayout) rootView.findViewById(R.id.ll_item);
            ImageView iv_item = (ImageView) rootView.findViewById(R.id.iv_item);
            ViewGroup.LayoutParams llParams = ll_item.getLayoutParams();
            llParams.width = CommonUtils.dip2px(context,58);
            llParams.height = CommonUtils.dip2px(context,58);
            ll_item.setLayoutParams(llParams);
            ViewGroup.LayoutParams ivParams = iv_item.getLayoutParams();
            ivParams.width = CommonUtils.dip2px(context,50);
            ivParams.height = CommonUtils.dip2px(context,50);
            iv_item.setLayoutParams(ivParams);
            iv_item.setImageResource(ImageIconList[position]);

            ll_item.setTag(position);
            //解决三星手机A8等部分手机在popupWindow里ListView不兼容点击事件，因此在ListView的item设置点击事件，而不是监听OnItemClickListener()
            ll_item.setOnClickListener(clickListener);

            return rootView;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return ImageIconList[position];
        }

        @Override
        public int getCount() {
            return ImageIconList.length;
        }
    };

    public MenuViewPopupWindow setViewAnimationStyle(String stype){
        //setAnimationStyle(ResourcesUtil.getStyleId(context,stype));
        return this;
    }

    public void Dismiss(){
        if(instance != null && isShowing())
            dismiss();
    }

    private View.OnClickListener clickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = (int) v.getTag();
            if(menuItemClick != null)
                menuItemClick.OnItemClick(position);
        }
    };

    @Override
    public void onClick(View v) {
        if(iconOnClick != null)
            iconOnClick.OnClick();
    }

    public interface MenuItemClick{
        void OnItemClick(int position);
    }

    public interface IconOnClick{
        void OnClick();
    }
}