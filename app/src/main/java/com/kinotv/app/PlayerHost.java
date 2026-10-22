package com.kinotv.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.ViewTreeObserver;
import android.webkit.WebChromeClient;
import android.widget.FrameLayout;

/** Hosts Chromium's real HTML5 fullscreen CustomView without synthetic input. */
final class PlayerHost {
    interface Listener { void onFullscreenChanged(boolean fullscreen); }

    private final Activity activity;
    private final Listener listener;
    private FullscreenContainer container;
    private View customView;
    private WebChromeClient.CustomViewCallback callback;
    private ViewTreeObserver.OnGlobalLayoutListener cursorLayoutListener;

    PlayerHost(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    boolean isFullscreen() { return customView != null; }

    void show(View view, WebChromeClient.CustomViewCallback customViewCallback) {
        if (view == null) {
            if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            return;
        }
        if (customView != null) {
            if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            return;
        }

        customView = view;
        callback = customViewCallback;
        container = new FullscreenContainer(activity);
        container.setBackgroundColor(Color.BLACK);

        ViewGroup oldParent = view.getParent() instanceof ViewGroup
                ? (ViewGroup) view.getParent() : null;
        if (oldParent != null) oldParent.removeView(view);
        container.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.addContentView(container, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Chromium may add/replace the internal render view after onShowCustomView().
        // Refresh the system pointer after hierarchy/layout changes without consuming
        // hover/mouse events or replacing Chromium's own hierarchy listeners.
        cursorLayoutListener = () -> {
            if (container != null) forceArrowCursor(container);
            if (customView != null) forceArrowCursor(customView);
        };
        try {
            container.getViewTreeObserver().addOnGlobalLayoutListener(cursorLayoutListener);
        } catch (Throwable ignored) { }

        forceArrowCursor(container);
        forceArrowCursor(view);
        // Chromium иногда создаёт внутренний render-view после onShowCustomView.
        // Повторяем установку курсора после первого layout, не перехватывая hover/click.
        container.post(() -> {
            if (container != null) forceArrowCursor(container);
            if (customView != null) forceArrowCursor(customView);
        });
        container.postDelayed(() -> {
            if (container != null) forceArrowCursor(container);
            if (customView != null) forceArrowCursor(customView);
        }, 250L);
        container.postDelayed(() -> {
            if (container != null) forceArrowCursor(container);
            if (customView != null) forceArrowCursor(customView);
        }, 1000L);

        hideSystemBars();
        Log.i("SidiPlayer", "native fullscreen shown class=" + view.getClass().getName());
        if (listener != null) listener.onFullscreenChanged(true);
    }

    void hideFromWebView() { hide(true); }
    void hideFromApp() { hide(true); }
    void destroy() { hide(false); }

    private void hide(boolean notifyCallback) {
        if (customView == null && container == null) return;
        View oldView = customView;
        FullscreenContainer oldContainer = container;
        WebChromeClient.CustomViewCallback oldCallback = callback;
        customView = null;
        container = null;
        callback = null;

        if (oldContainer != null && cursorLayoutListener != null) {
            try {
                ViewTreeObserver observer = oldContainer.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnGlobalLayoutListener(cursorLayoutListener);
            } catch (Throwable ignored) { }
        }
        cursorLayoutListener = null;

        if (oldContainer != null) {
            if (oldView != null && oldView.getParent() == oldContainer) oldContainer.removeView(oldView);
            ViewGroup parent = oldContainer.getParent() instanceof ViewGroup
                    ? (ViewGroup) oldContainer.getParent() : null;
            if (parent != null) parent.removeView(oldContainer);
        }
        if (listener != null) listener.onFullscreenChanged(false);
        if (notifyCallback && oldCallback != null) {
            try { oldCallback.onCustomViewHidden(); }
            catch (Throwable error) { Log.w("SidiPlayer", "fullscreen callback failed", error); }
        }
        Log.i("SidiPlayer", "native fullscreen hidden");
    }

    private void forceArrowCursor(View view) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        forceArrowCursorSelf(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) forceArrowCursor(group.getChildAt(i));
        }
    }

    private void forceArrowCursorSelf(View view) {
        if (view == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            view.setPointerIcon(PointerIcon.getSystemIcon(activity, PointerIcon.TYPE_ARROW));
        } catch (Throwable ignored) { }
    }

    private final class FullscreenContainer extends FrameLayout {
        FullscreenContainer(Activity context) {
            super(context);
            setFocusable(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW));
            }
        }

        @Override
        public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && event != null
                    && (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) {
                return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_ARROW);
            }
            return super.onResolvePointerIcon(event, pointerIndex);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            boolean mouse = event != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                    && (event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
            if (mouse) {
                forceArrowCursorSelf(this);
                if (customView != null) forceArrowCursorSelf(customView);
            }
            boolean handled = super.dispatchGenericMotionEvent(event);
            // Chromium может поменять icon во время обработки hover; возвращаем
            // системную стрелку уже после передачи события дочернему render-view.
            if (mouse) {
                forceArrowCursorSelf(this);
                if (customView != null) forceArrowCursorSelf(customView);
            }
            return handled;
        }
    }

    private void hideSystemBars() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.WindowInsetsController controller = activity.getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                activity.getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) { }
    }
}
