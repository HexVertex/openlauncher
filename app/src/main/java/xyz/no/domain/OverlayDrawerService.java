package xyz.no.domain;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Service;

import android.content.Context;
import android.content.Intent;
import android.gesture.GestureOverlayView;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.afollestad.materialdialogs.Theme;
import com.benny.openlauncher.R;
import com.benny.openlauncher.interfaces.AppUpdateListener;
import com.benny.openlauncher.manager.Setup;
import com.benny.openlauncher.model.App;
import com.benny.openlauncher.model.Item;
import com.benny.openlauncher.util.DragAction;
import com.benny.openlauncher.util.Tool;
import com.benny.openlauncher.viewutil.DrawerAppItem;
import com.benny.openlauncher.widget.AppDrawerVertical;
import com.benny.openlauncher.widget.AppItemView;
import com.mikepenz.fastadapter.IItemAdapter;
import com.mikepenz.fastadapter.commons.adapters.FastItemAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;
import com.turingtechnologies.materialscrollbar.AlphabetIndicator;
import com.turingtechnologies.materialscrollbar.DragScrollBar;
import com.turingtechnologies.materialscrollbar.INameableAdapter;

import java.util.ArrayList;
import java.util.List;

public class OverlayDrawerService extends Service implements View.OnTouchListener, View.OnClickListener {

    private boolean open = false;
    private RelativeLayout layout;
    private RecyclerView barFrame;
    private GridAppDrawerAdapter gridDrawerAdapter;
    private DragScrollBar scrollBar;
    private GridLayoutManager layoutManager;
    private static List<App> _apps;
    private static int heightPadding;
    private static int itemWidth;

    private Button overlayedButton;
    private Button background;
    private View topLeftView;
    private float offsetX;
    private float offsetY;
    private int originalXPos;
    private int originalYPos;
    private boolean moving;
    private WindowManager wm;

    @Override
    public void onCreate() {
        super.onCreate();

        heightPadding = Tool.dp2px(10, this);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        //barFrame = new FrameLayout(this);
        overlayedButton = new Button(this);
        overlayedButton.setText("OPEN!");
        overlayedButton.setTextSize(0.5f);
        overlayedButton.setOnTouchListener(this);
        overlayedButton.setBackgroundColor(Color.WHITE);
        overlayedButton.getBackground().setAlpha(80);
        overlayedButton.setOnClickListener(this);
        overlayedButton.setZ(1);

        background = new Button(this);
        background.setText("");
        background.setOnTouchListener(this);
        background.setBackgroundColor(Color.BLACK);
        background.getBackground().setAlpha(80);
        background.setOnClickListener(this);
        background.setEnabled(false);
        background.setZ(0);
        background.setVisibility(View.GONE);

        int LAYOUT_FLAG;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params2 = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, LAYOUT_FLAG, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        wm.addView(background, params2);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, LAYOUT_FLAG, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        params.width = 150;
        params.height = 150;
        params.x = 32;
        params.y = 32;
        wm.addView(overlayedButton, params);

        topLeftView = new View(this);
        topLeftView.setBackgroundColor(Color.BLACK);
        topLeftView.setAlpha(0.5F);
        WindowManager.LayoutParams topLeftParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, LAYOUT_FLAG, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        topLeftParams.gravity = Gravity.RIGHT | Gravity.TOP;
        topLeftParams.x = 0;
        topLeftParams.y = 0;
        topLeftParams.width = 0;
        topLeftParams.height = 0;
        wm.addView(topLeftView, topLeftParams);

        layout = new RelativeLayout(this);
        layout.setBackgroundColor(Setup.appSettings().getDrawerBackgroundColor());
        WindowManager.LayoutParams rLayoutParams = new WindowManager.LayoutParams(200, RelativeLayout.LayoutParams.MATCH_PARENT, LAYOUT_FLAG, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        rLayoutParams.gravity = Gravity.RIGHT;
        rLayoutParams.x = -rLayoutParams.width;
        rLayoutParams.y = 0;

        itemWidth = layout.getWidth();

        barFrame = new RecyclerView(this);
        //barFrame.setAlpha(0.5f);
        RelativeLayout.LayoutParams barFrameParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        barFrameParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        layout.addView(barFrame, barFrameParams);

        /*scrollBar = new DragScrollBar(this, barFrame,false);
        scrollBar.setHandleColor(ResourcesCompat.getColor(getResources(), R.color.colorAccent, null));
        RelativeLayout.LayoutParams scrollBarParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        scrollBarParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        scrollBarParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        scrollBar.setIndicator(new AlphabetIndicator(this), true);
        scrollBar.setClipToPadding(true);
        scrollBar.setDraggableFromAnywhere(true);*/

        layoutManager = new GridLayoutManager(this, 1);
        gridDrawerAdapter = new GridAppDrawerAdapter();
        barFrame.setAdapter(gridDrawerAdapter);
        barFrame.setLayoutManager(layoutManager);
        barFrame.setDrawingCacheEnabled(true);

        List<App> allApps = Setup.appLoader().getAllApps(this, false);

        if (allApps.size() != 0) {
            _apps = allApps;
            ArrayList<AppItem> items = new ArrayList<>();
            for (int i = 0; i < _apps.size(); i++) {
                items.add(new AppItem(_apps.get(i)));
            }
            gridDrawerAdapter.set(items);
        }
        Setup.appLoader().addUpdateListener(new AppUpdateListener() {
            @Override
            public boolean onAppUpdated(List<App> apps) {
                OverlayDrawerService._apps = apps;
                ArrayList<AppItem> items = new ArrayList<>();
                for (int i = 0; i < apps.size(); i++) {
                    items.add(new AppItem(apps.get(i)));
                }
                gridDrawerAdapter.set(items);

                return false;
            }
        });
        //layout.addView(scrollBar, scrollBarParams);
        wm.addView(layout, rLayoutParams);

        //Log.d("Overlay", "So... it's added... where tf is it...");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayedButton != null) {
            wm.removeView(overlayedButton);
            wm.removeView(topLeftView);
            overlayedButton = null;
            topLeftView = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /* We want this service to continue running until it is explicitly
         * stopped, so return sticky.

        return START_STICKY;
    }*/

    @Override
    public void onClick(View v) {
        if(!open) {
            showBar();
        } else {
            hideBar();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        Log.d("Overlay", "Event:" + event.toString());
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            /*float x = event.getRawX();
            float y = event.getRawY();

            moving = false;

            int[] location = new int[2];
            overlayedButton.getLocationOnScreen(location);

            originalXPos = location[0];
            originalYPos = location[1];

            offsetX = originalXPos - x + overlayedButton.getWidth();
            offsetY = originalYPos - y;

            /*Log.d("Overlay", "X shit:");
            Log.d("Overlay", "offset:"+offsetX);
            Log.d("Overlay", "raw:"+x);
            Log.d("Overlay", "raw:"+originalXPos);*/

        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            /*int[] topLeftLocationOnScreen = new int[2];
            topLeftView.getLocationOnScreen(topLeftLocationOnScreen);

            //System.out.println("topLeftX="+topLeftLocationOnScreen[0]);
            //System.out.println("originalX="+originalXPos);
            //System.out.println("topLeftY="+topLeftLocationOnScreen[1]);
            //System.out.println("originalY="+originalYPos);

            float x = event.getRawX();
            float y = event.getRawY();

            WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlayedButton.getLayoutParams();

            int newX = (int) (offsetX + x);
            int newY = (int) (offsetY + y);

            /*Log.d("Overlay", "X shit:");
            Log.d("Overlay", "offset:"+offsetX);
            Log.d("Overlay", "raw:"+x);
            Log.d("Overlay", "original:"+originalXPos);
            Log.d("Overlay", "topleft:"+topLeftLocationOnScreen[0]);
            Log.d("Overlay", "newX:"+newX);*/

            /*if (Math.abs(newX - originalXPos) < 1 && Math.abs(newY - originalYPos) < 1 && !moving) {
                return false;
            }

            params.x = (topLeftLocationOnScreen[0]) - newX;
            params.y = newY - (topLeftLocationOnScreen[1]);

            wm.updateViewLayout(overlayedButton, params);
            WindowManager.LayoutParams barparams = (WindowManager.LayoutParams) layout.getLayoutParams();
            barparams.x = params.x - barparams.width;
            wm.updateViewLayout(layout, barparams);
            moving = true;*/
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            /*if (moving) {
                return true;
            }*/
        }
        return false;
    }

    private void showBar() {
        long animationLifeTime = 300;
        overlayedButton.setVisibility(View.GONE);
        background.setEnabled(true);
        background.setVisibility(View.VISIBLE);
        new CountDownTimer(animationLifeTime, 10) {

            public void onTick(long millisUntilFinished) {

                Log.i("animation remaining: ", Long.toString(millisUntilFinished / 10));
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) layout.getLayoutParams();
                params.x = (int)(-layout.getWidth() * ((float)millisUntilFinished / (float)animationLifeTime));
                background.getBackground().setAlpha((int)(80 * (1.0f - (float)millisUntilFinished / (float)animationLifeTime)));
                //WindowManager.LayoutParams buttonparams = (WindowManager.LayoutParams) overlayedButton.getLayoutParams();
                //buttonparams.x = params.x + layout.getWidth();
                //wm.updateViewLayout(overlayedButton, buttonparams);
                wm.updateViewLayout(layout, params);
            }

            public void onFinish() {
                Log.i("hide animation: ", "DONE");
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) layout.getLayoutParams();
                params.x = 0;
                background.getBackground().setAlpha(80);
                //WindowManager.LayoutParams buttonparams = (WindowManager.LayoutParams) overlayedButton.getLayoutParams();
                //buttonparams.x = params.x + layout.getWidth();
                //wm.updateViewLayout(overlayedButton, buttonparams);
                wm.updateViewLayout(layout, params);
                open = true;
            }
        }.start();
    }

    private void hideBar() {
        long animationLifeTime = 300;
        new CountDownTimer(animationLifeTime, 10) {

            public void onTick(long millisUntilFinished) {

                Log.i("animation remaining: ", Long.toString(millisUntilFinished / 10));
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) layout.getLayoutParams();
                params.x = (int)(-layout.getWidth() * (1.0f - (float)millisUntilFinished / (float)animationLifeTime));
                background.getBackground().setAlpha((int)(80 * ((float)millisUntilFinished / (float)animationLifeTime)));
                //WindowManager.LayoutParams buttonparams = (WindowManager.LayoutParams) overlayedButton.getLayoutParams();
                //buttonparams.x = params.x + layout.getWidth();
                //wm.updateViewLayout(overlayedButton, buttonparams);
                wm.updateViewLayout(layout, params);
            }

            public void onFinish() {
                Log.i("hide animation: ", "DONE");
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) layout.getLayoutParams();
                params.x = -layout.getWidth();
                background.setEnabled(false);
                background.setVisibility(View.GONE);
                overlayedButton.setVisibility(View.VISIBLE);
                //WindowManager.LayoutParams buttonparams = (WindowManager.LayoutParams) overlayedButton.getLayoutParams();
                //buttonparams.x = params.x + layout.getWidth();
                //wm.updateViewLayout(overlayedButton, buttonparams);
                wm.updateViewLayout(layout, params);
                open = false;
            }
        }.start();
    }

    private static class GridAppDrawerAdapter extends FastItemAdapter<AppItem> implements INameableAdapter {
        GridAppDrawerAdapter() {
            getItemFilter().withFilterPredicate(new IItemAdapter.Predicate<AppItem>() {
                @Override
                public boolean filter(AppItem item, CharSequence constraint) {
                    return !item.getApp().getLabel().toLowerCase().contains(constraint.toString().toLowerCase());
                }
            });
        }

        @Override
        public Character getCharacterForElement(int element) {
            if (_apps != null && element < _apps.size() && _apps.get(element) != null && _apps.get(element).getLabel().length() > 0)
                return _apps.get(element).getLabel().charAt(0);
            else return '#';
        }
    }

    private static class AppItem extends AbstractItem<AppItem, AppItem.ViewHolder> {
        private App _app;
        private AppItemView.LongPressCallBack _onLongClickCallback;

        public AppItem(App app) {
            _app = app;
            _onLongClickCallback = new AppItemView.LongPressCallBack() {
                @Override
                public boolean readyForDrag(View view) {
                    return true;
                }

                @Override
                public void afterDrag(View view) {
                    //This will be handled by the Drag N Drop listener in the Home
                    //Home.Companion.getLauncher().closeAppDrawer();
                }
            };
        }

        @Override
        public int getType() {
            return R.id.id_adapter_drawer_app_item;
        }

        @Override
        public int getLayoutRes() {
            return R.layout.item_app;
        }

        @Override
        public AppItem.ViewHolder getViewHolder(View v) {
            return new AppItem.ViewHolder(v);
        }

        public App getApp() {
            return _app;
        }

        @Override
        public void bindView(AppItem.ViewHolder holder, List payloads) {
            holder.builder
                    .setAppItem(_app)
                    .withOnLongClick(_app, DragAction.Action.APP_DRAWER, _onLongClickCallback)
                    .withOnTouchGetPosition(Item.newAppItem(_app), Setup.itemGestureCallback());
            super.bindView(holder, payloads);
        }

        @Override
        public void unbindView(AppItem.ViewHolder holder) {
            super.unbindView(holder);
            holder.appItemView.reset();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            AppItemView appItemView;
            AppItemView.Builder builder;

            ViewHolder(View itemView) {
                super(itemView);
                appItemView = (AppItemView) itemView;
                appItemView.setTargetedWidth(itemWidth);
                appItemView.setTargetedHeightPadding(heightPadding);

                builder = new AppItemView.Builder(appItemView, Setup.appSettings().getDrawerIconSize())
                        .setLabelVisibility(Setup.appSettings().isDrawerShowLabel())
                        .setTextColor(Setup.appSettings().getDrawerLabelColor());
            }
        }
    }

}
