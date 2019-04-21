package com.wow.carlauncher.view.activity.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;

import com.wow.carlauncher.R;
import com.wow.carlauncher.common.CommonData;
import com.wow.carlauncher.common.util.SharedPreUtil;
import com.wow.carlauncher.common.util.ViewUtils;
import com.wow.carlauncher.ex.manage.ThemeManage;
import com.wow.carlauncher.ex.manage.location.event.MNewLocationEvent;
import com.wow.carlauncher.ex.manage.toast.ToastManage;
import com.wow.carlauncher.ex.plugin.console.ConsolePlugin;
import com.wow.carlauncher.ex.plugin.console.event.PConsoleEventCallState;
import com.wow.carlauncher.ex.plugin.fk.event.PFkEventAction;
import com.wow.carlauncher.ex.plugin.music.MusicPlugin;
import com.wow.carlauncher.view.activity.AppSelectActivity;
import com.wow.carlauncher.view.activity.launcher.event.LItemRefreshEvent;
import com.wow.carlauncher.view.activity.launcher.view.LDockView;
import com.wow.carlauncher.view.activity.launcher.view.LPageView;
import com.wow.carlauncher.view.consoleWindow.ConsoleWin;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.xutils.view.annotation.Event;
import org.xutils.view.annotation.ViewInject;
import org.xutils.x;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import per.goweii.anypermission.AnyPermission;
import per.goweii.anypermission.RequestListener;
import per.goweii.anypermission.RuntimeRequester;

import static com.wow.carlauncher.common.CommonData.IDATA_APP_MARK;
import static com.wow.carlauncher.common.CommonData.IDATA_APP_PACKAGE_NAME;
import static com.wow.carlauncher.common.CommonData.REQUEST_SELECT_APP_TO_DOCK1;
import static com.wow.carlauncher.common.CommonData.REQUEST_SELECT_APP_TO_DOCK2;
import static com.wow.carlauncher.common.CommonData.REQUEST_SELECT_APP_TO_DOCK3;
import static com.wow.carlauncher.common.CommonData.REQUEST_SELECT_APP_TO_DOCK4;
import static com.wow.carlauncher.common.CommonData.REQUEST_SELECT_APP_TO_DOCK5;
import static com.wow.carlauncher.common.CommonData.SDATA_DOCK1_CLASS;
import static com.wow.carlauncher.common.CommonData.SDATA_DOCK2_CLASS;
import static com.wow.carlauncher.common.CommonData.SDATA_DOCK3_CLASS;
import static com.wow.carlauncher.common.CommonData.SDATA_DOCK4_CLASS;
import static com.wow.carlauncher.common.CommonData.SDATA_DOCK5_CLASS;
import static com.wow.carlauncher.ex.plugin.fk.FangkongProtocolEnum.YLFK;
import static com.wow.carlauncher.ex.plugin.fk.protocol.YiLianProtocol.CENTER_CLICK;
import static com.wow.carlauncher.ex.plugin.fk.protocol.YiLianProtocol.CENTER_LONG_CLICK;
import static com.wow.carlauncher.ex.plugin.fk.protocol.YiLianProtocol.LEFT_BOTTOM_CLICK;
import static com.wow.carlauncher.ex.plugin.fk.protocol.YiLianProtocol.LEFT_TOP_CLICK;
import static com.wow.carlauncher.ex.plugin.fk.protocol.YiLianProtocol.LEFT_TOP_LONG_CLICK;
import static com.wow.carlauncher.ex.plugin.fk.protocol.YiLianProtocol.RIGHT_BOTTOM_CLICK;
import static com.wow.carlauncher.ex.plugin.fk.protocol.YiLianProtocol.RIGHT_TOP_CLICK;
import static com.wow.carlauncher.ex.plugin.fk.protocol.YiLianProtocol.RIGHT_TOP_LONG_CLICK;

public class LauncherActivity extends Activity implements ThemeManage.OnThemeChangeListener {

    public static LauncherActivity activity;

    @ViewInject(R.id.ll_dock)
    private LDockView ll_dock;

    @ViewInject(R.id.viewPager)
    private ViewPager viewPager;

    @ViewInject(R.id.postion)
    private LinearLayout postion;

    @ViewInject(R.id.fl_bg)
    private View fl_bg;

    @ViewInject(R.id.line1)
    private View line1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //超级大坑,必去全局设置才能用
        ThemeManage.self().refreshTheme();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(homeReceiver, intentFilter);
        initView();

        //主题处理
        onThemeChanged(ThemeManage.self());
        ThemeManage.self().registerThemeChangeListener(this);
        ThemeManage.self().refreshTheme();

        LauncherActivity.activity = this;
    }

    public void initView() {
        setContentView(R.layout.activity_lanncher);
        x.view().inject(this);

        EventBus.getDefault().register(this);
        initItem();
        x.task().postDelayed(new Runnable() {
            @Override
            public void run() {
                requestRuntime();
            }
        }, 2000);
    }

    private void initItem() {
        //计算排序
        List<ItemModel> items = new ArrayList<>();
        for (ItemEnum item : CommonData.LAUNCHER_ITEMS) {
            if (SharedPreUtil.getSharedPreBoolean(CommonData.SDATA_LAUNCHER_ITEM_OPEN_ + item.getId(), true)) {
                items.add(new ItemModel(item,
                        SharedPreUtil.getSharedPreInteger(CommonData.SDATA_LAUNCHER_ITEM_SORT_ + item.getId(), item.getId()),
                        SharedPreUtil.getSharedPreBoolean(CommonData.SDATA_LAUNCHER_ITEM_OPEN_ + item.getId(), true)
                ));
            }
        }

        Collections.sort(items, (o1, o2) -> o1.index - o2.index);
        int psize = SharedPreUtil.getSharedPreInteger(CommonData.SDATA_LAUNCHER_ITEM_NUM, 3);
        if (psize != 3) {
            psize = 4;
        }
        int pnum = items.size() / psize;
        if (items.size() % psize != 0) {
            pnum++;
        }

        LPageView[] pageViews = new LPageView[pnum];

        int npage = 0;
        LPageView pageView = null;
        View[] pcell = new View[psize];
        for (int i = 0; i < items.size(); i++) {
            if (i % psize == 0) {
                pageViews[npage] = new LPageView(this);
                pcell = new View[psize];
                pageView = pageViews[npage];
                npage++;
            }
            pcell[i % psize] = ItemEnum.createView(this, items.get(i).info);
            //如果是一组的最后一个,或者是列表最后一个
            if (((i % psize == psize - 1) || (i == items.size() - 1)) && pageView != null) {
                pageView.setItem(pcell);
            }
        }
        viewPager.setAdapter(new ViewAdapter(pageViews));
        viewPager.clearOnPageChangeListeners();
        postion.removeAllViews();

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewUtils.dip2px(getApplicationContext(), 8), ViewUtils.dip2px(getApplicationContext(), 8));
        //设置小圆点左右之间的间隔
        params.setMargins(10, 0, 10, 0);

        //根据主题处理小圆点
        final View[] posts = new View[pageViews.length];
        for (int i = 0; i < posts.length; i++) {
            posts[i] = new View(getApplicationContext());
            if (i == 0) {
                posts[i].setBackgroundResource(R.drawable.n_l_postion);
            } else {
                posts[i].setBackgroundResource(R.drawable.n_l_postion_n);
            }
            postion.addView(posts[i], params);
        }
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i1) {

            }

            @Override
            public void onPageSelected(int i) {
                for (View post : posts) {
                    post.setBackgroundResource(R.drawable.n_l_postion_n);
                }
                posts[i].setBackgroundResource(R.drawable.n_l_postion);
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });
    }

    @Override
    public void onThemeChanged(ThemeManage manage) {
        Context context = getApplicationContext();
        fl_bg.setBackgroundResource(manage.getCurrentThemeRes(context, R.drawable.n_desk_bg));
        line1.setBackgroundResource(manage.getCurrentThemeRes(context, R.color.line));
    }

    @Event(value = {R.id.ll_dock1, R.id.ll_dock2, R.id.ll_dock3, R.id.ll_dock4}, type = View.OnLongClickListener.class)
    private boolean longClickEvent(View view) {
        switch (view.getId()) {
            case R.id.ll_dock1: {
                startActivityForResult(new Intent(this, AppSelectActivity.class), REQUEST_SELECT_APP_TO_DOCK1);
                break;
            }
            case R.id.ll_dock2: {
                startActivityForResult(new Intent(this, AppSelectActivity.class), REQUEST_SELECT_APP_TO_DOCK2);
                break;
            }
            case R.id.ll_dock3: {
                startActivityForResult(new Intent(this, AppSelectActivity.class), REQUEST_SELECT_APP_TO_DOCK3);
                break;
            }
            case R.id.ll_dock4: {
                startActivityForResult(new Intent(this, AppSelectActivity.class), REQUEST_SELECT_APP_TO_DOCK4);
                break;
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mRuntimeRequester != null) {
            mRuntimeRequester.onActivityResult(requestCode);
        }
        if (requestCode == REQUEST_SELECT_APP_TO_DOCK1) {
            if (resultCode == RESULT_OK) {
                String packName = data.getStringExtra(IDATA_APP_PACKAGE_NAME);
                int mark = data.getIntExtra(IDATA_APP_MARK, -1);
                SharedPreUtil.saveSharedPreString(SDATA_DOCK1_CLASS, mark + CommonData.CONSTANT_APP_PACKAGE_SEPARATE + packName);
                ll_dock.loadDock();
            }
        }
        if (requestCode == REQUEST_SELECT_APP_TO_DOCK2) {
            if (resultCode == RESULT_OK) {
                String packName = data.getStringExtra(IDATA_APP_PACKAGE_NAME);
                int mark = data.getIntExtra(IDATA_APP_MARK, -1);
                SharedPreUtil.saveSharedPreString(SDATA_DOCK2_CLASS, mark + CommonData.CONSTANT_APP_PACKAGE_SEPARATE + packName);
                ll_dock.loadDock();
            }
        }
        if (requestCode == REQUEST_SELECT_APP_TO_DOCK3) {
            if (resultCode == RESULT_OK) {
                String packName = data.getStringExtra(IDATA_APP_PACKAGE_NAME);
                int mark = data.getIntExtra(IDATA_APP_MARK, -1);
                SharedPreUtil.saveSharedPreString(SDATA_DOCK3_CLASS, mark + CommonData.CONSTANT_APP_PACKAGE_SEPARATE + packName);
                ll_dock.loadDock();
            }
        }
        if (requestCode == REQUEST_SELECT_APP_TO_DOCK4) {
            if (resultCode == RESULT_OK) {
                String packName = data.getStringExtra(IDATA_APP_PACKAGE_NAME);
                int mark = data.getIntExtra(IDATA_APP_MARK, -1);
                SharedPreUtil.saveSharedPreString(SDATA_DOCK4_CLASS, mark + CommonData.CONSTANT_APP_PACKAGE_SEPARATE + packName);
                ll_dock.loadDock();
            }
        }
        if (requestCode == REQUEST_SELECT_APP_TO_DOCK5) {
            if (resultCode == RESULT_OK) {
                String packName = data.getStringExtra(IDATA_APP_PACKAGE_NAME);
                int mark = data.getIntExtra(IDATA_APP_MARK, -1);
                SharedPreUtil.saveSharedPreString(SDATA_DOCK5_CLASS, mark + CommonData.CONSTANT_APP_PACKAGE_SEPARATE + packName);
                ll_dock.loadDock();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(homeReceiver);
        EventBus.getDefault().unregister(this);
        ThemeManage.self().unregisterThemeChangeListener(this);
        LauncherActivity.activity = null;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(LItemRefreshEvent event) {
        initItem();
    }

    @Override
    public void onBackPressed() {

    }

    private String lastadCode;

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(MNewLocationEvent event) {
        if (!event.getAdCode().equals(lastadCode)) {
            lastadCode = event.getAdCode();
            ToastManage.self().show("定位成功:" + event.getCity());
        }
    }

    private boolean isCalling = false;

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void onEvent(final PConsoleEventCallState event) {
        isCalling = event.isCalling();
    }

    @Subscribe
    public void onEvent(PFkEventAction event) {
        if (YLFK.equals(event.getFangkongProtocol())) {
            switch (event.getAction()) {
                case LEFT_TOP_CLICK: {
                    ConsolePlugin.self().decVolume();
                    break;
                }
                case LEFT_TOP_LONG_CLICK: {
                    MusicPlugin.self().pre();
                    break;
                }
                case RIGHT_TOP_CLICK: {
                    ConsolePlugin.self().incVolume();
                    break;
                }
                case RIGHT_TOP_LONG_CLICK: {
                    MusicPlugin.self().next();
                    break;
                }
                case CENTER_CLICK: {
                    Intent home = new Intent(Intent.ACTION_MAIN);
                    home.addCategory(Intent.CATEGORY_HOME);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(home);
                    break;
                }
                case CENTER_LONG_CLICK: {
                    AnyPermission.with(getApplicationContext()).overlay()
                            .onWithoutPermission((data, executor) -> {
                                new AlertDialog.Builder(getApplicationContext()).setTitle("警告!")
                                        .setNegativeButton("取消", (dialog, which) -> executor.cancel())
                                        .setPositiveButton("确定", (dialog2, which2) -> executor.execute())
                                        .setMessage("请给与车机助手悬浮窗权限,否则无法使用这个功能").show();
                            })
                            .request(new RequestListener() {
                                @Override
                                public void onSuccess() {
                                    ConsoleWin.self().show();
                                }

                                @Override
                                public void onFailed() {
                                    ToastManage.self().show("没有悬浮窗权限!");
                                }
                            });
                    break;
                }
                case LEFT_BOTTOM_CLICK: {
                    if (isCalling) {
                        ConsolePlugin.self().callAnswer();
                    } else {
                        ConsolePlugin.self().mute();
                    }
                    break;
                }
                case RIGHT_BOTTOM_CLICK:
                    if (isCalling) {
                        ConsolePlugin.self().callHangup();
                    } else {
                        //切换页面?
                        if (viewPager != null && viewPager.getAdapter() != null) {
                            if (viewPager.getAdapter().getCount() == viewPager.getCurrentItem() + 1) {
                                viewPager.setCurrentItem(0, true);
                            } else {
                                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1, true);
                            }
                        }
                    }
                    break;
            }
        }
    }

    private RuntimeRequester mRuntimeRequester;

    private void requestRuntime() {
        final AnyPermission anyPermission = AnyPermission.with(this);
        mRuntimeRequester = AnyPermission.with(this).runtime(1)
                .permissions(
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        Manifest.permission.ACCESS_WIFI_STATE,
                        Manifest.permission.CHANGE_WIFI_STATE,
                        Manifest.permission.INTERNET,
                        Manifest.permission.GET_TASKS,
                        Manifest.permission.WAKE_LOCK,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.RECEIVE_BOOT_COMPLETED,
                        Manifest.permission.KILL_BACKGROUND_PROCESSES,
                        Manifest.permission.ACCESS_NETWORK_STATE
                )
                .onBeforeRequest((permission, executor) -> {
                    System.out.println("!!!!!!!!!onBeforeRequest--" + permission);
                    new AlertDialog.Builder(LauncherActivity.this).setTitle("警告!")
                            .setNegativeButton("取消", (dialog2, which2) -> executor.cancel())
                            .setPositiveButton("确定", (dialog2, which2) -> executor.execute())
                            .setMessage("车机助手正在申请:" + anyPermission.name(permission) + " 权限").show();
                })
                .onBeenDenied((permission, executor) -> {
                    System.out.println("!!!!!!!!!onBeenDenied--" + permission);
                    new AlertDialog.Builder(LauncherActivity.this).setTitle("警告!")
                            .setNegativeButton("不授权", (dialog2, which2) -> executor.cancel())
                            .setPositiveButton("重新授权", (dialog2, which2) -> executor.execute())
                            .setMessage("车机助手需要:" + anyPermission.name(permission) + " 权限才能正常运行!").show();
                })
                .onGoSetting((permission, executor) -> {
                    System.out.println("!!!!!!!!!onGoSetting--" + permission);
                    new AlertDialog.Builder(LauncherActivity.this).setTitle("警告!")
                            .setNegativeButton("不授权", (dialog2, which2) -> executor.cancel())
                            .setPositiveButton("重新授权", (dialog2, which2) -> executor.execute())
                            .setMessage("车机助手需要:" + anyPermission.name(permission) + " 权限才能正常运行,前往设置界面设置!").show();
                })
                .request(new RequestListener() {
                    @Override
                    public void onSuccess() {
                        requestOverlay();
                    }

                    @Override
                    public void onFailed() {
                        ToastManage.self().show("授权失败,某些权限不足可能导致功能不可用!");
                    }
                });
    }

    private void requestOverlay() {
        AnyPermission.with(this).overlay()
                .onWithoutPermission((data, executor) -> {
                    new AlertDialog.Builder(LauncherActivity.this).setTitle("警告!")
                            .setNegativeButton("不授权", (dialog2, which2) -> executor.cancel())
                            .setPositiveButton("重新授权", (dialog2, which2) -> executor.execute())
                            .setMessage("车机助手需要悬浮窗权限才能正常运行!").show();
                })
                .request(new RequestListener() {
                    @Override
                    public void onSuccess() {
                        ToastManage.self().show("权限检查成功");
                    }

                    @Override
                    public void onFailed() {
                        ToastManage.self().show("授权失败,某些权限不足可能导致功能不可用!");
                    }
                });
    }

    class ViewAdapter extends PagerAdapter {
        private View[] datas;

        public ViewAdapter(View[] list) {
            datas = list;
        }

        @Override
        public int getCount() {
            return datas.length;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = datas[position];
            container.addView(datas[position]);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView(datas[position]);
        }
    }

    private BroadcastReceiver homeReceiver = new BroadcastReceiver() {
        String SYSTEM_REASON = "reason";
        String SYSTEM_HOME_KEY = "homekey";

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
                String reason = intent.getStringExtra(SYSTEM_REASON);
                if (TextUtils.equals(reason, SYSTEM_HOME_KEY)) {
                    //表示按了home键,程序到了后台
                    Intent i = new Intent(Intent.ACTION_MAIN, null);
                    i.addCategory(Intent.CATEGORY_HOME);
                    context.startActivity(i);
                }
            }
        }
    };
}
