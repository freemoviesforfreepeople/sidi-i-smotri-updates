package com.kinotv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.util.LruCache;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends Activity {
    private static final int MAX_TABS = 6;
    private static final String UI_PREFS = "sidi_ui_settings";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String DESKTOP_WINDOWS_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36";
    private int BG;
    private int PANEL;
    private int PANEL_2;
    private int BORDER;
    private int TEXT;
    private int MUTED;
    private int ACCENT;
    private int ACCENT_DARK;
    private int HEADER_BG;
    private int FOCUS_BG;
    private boolean lightTheme;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService imagePool = Executors.newFixedThreadPool(3);
    private final ExecutorService ioPool = Executors.newSingleThreadExecutor();
    private final List<BrowserTab> tabs = new ArrayList<>();
    private final Map<Long, String> posterFingerprintOwners = new ConcurrentHashMap<>();
    private final Map<String, byte[]> legacyScriptCache = new ConcurrentHashMap<>();
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value == null ? 0 : value.getByteCount();
        }
    };

    private MovieStore store;
    private AdBlocker adBlocker;
    private PlayerUrlRules playerUrlRules;
    private AdRemovalStore adRemovalStore;
    private boolean adRemovalMode;
    private boolean adPickerPromptOpen;
    /** Always-on: keep sweeping popups/banners even when «Убрать рекламу» is off. */
    private final Runnable alwaysOnAdPollRunnable = new Runnable() {
        @Override public void run() {
            BrowserTab tab = activeTab;
            if (tab != null && tab.webView != null) {
                autoHideKnownAdPopups(tab.webView, "always-poll");
            }
            handler.postDelayed(this, 650L);
        }
    };
    /** While «Убрать рекламу» is on: refresh yellow frame + localized hit-catchers over ad iframes. */
    private final Runnable adPickerPollRunnable = new Runnable() {
        @Override public void run() {
            if (!adRemovalMode) return;
            BrowserTab tab = activeTab;
            if (tab != null && tab.webView != null) {
                autoHideKnownAdPopups(tab.webView, "poll");
                activateAdPickerRuntime(tab.webView, "poll");
            }
            handler.postDelayed(this, 700L);
        }
    };
    private final Map<String, Integer> adPickerDecisions = new ConcurrentHashMap<>();
    private boolean compact;
    private int gridColumns;

    private LinearLayout appRoot;
    private View appHeader;
    private View appTabStrip;
    private HorizontalScrollView tabScrollView;
    private TextView tabOverflowLeft;
    private TextView tabOverflowRight;
    private LinearLayout tabButtons;
    private FrameLayout content;
    private ScrollView searchScroll;
    private LinearLayout continueList;
    private LinearLayout recentList;
    private LinearLayout favoritesList;
    private LinearLayout resultsList;
    private EditText queryInput;
    private TextView statusText;
    private TextView adblockStatus;
    private ProgressBar searchProgress;
    private WebView searchWebView;
    private BrowserTab activeTab;
    private DiagnosticsManager.Result activeDiagnosticsResult;
    private DataTransferManager.Result activeTransferResult;
    private View updateNotificationDot;
    private boolean updateAvailable;

    // 12.4: fullscreen — только штатный Chromium CustomView. Старые iframe/JS/focus
    // хаки больше не участвуют в рабочем player-path.
    private boolean embeddedFullScreen;
    private PlayerHost playerHost;

    // Нормализация аппаратной Play/Pause: Button Mapper/прошивка приставки могут
    // присылать auto-repeat и второй синтетический цикл одной физической кнопки.
    private static final long MEDIA_SYNTHETIC_DUPLICATE_WINDOW_MS = 1200L;
    private static final long MEDIA_HARDWARE_BOUNCE_WINDOW_MS = 250L;
    private static final boolean VERBOSE_INPUT_LOGS = false;
    private long lastAcceptedMediaDownAt = -10_000L;
    private int lastAcceptedMediaSource = 0;
    private long acceptedMediaDownTime = -1L;
    private long suppressedMediaDownTime = -1L;
    private long sleepTimerEndsAt;
    private boolean sleepTimerWarningShown;
    private boolean foreground;
    private boolean windowFocused;
    private boolean foregroundWorkStarted;
    private int searchGeneration;

    // 11.7.12-diag: observability only. These listeners never consume input.
    private ViewTreeObserver.OnGlobalFocusChangeListener inputFocusListener;
    private ViewTreeObserver.OnTouchModeChangeListener inputTouchModeListener;
    private long lastInputHoverLogAt;

    private final Runnable playerStateTicker = new Runnable() {
        @Override
        public void run() {
            if (!foreground || !windowFocused) {
                foregroundWorkStarted = false;
                return;
            }
            if (!embeddedFullScreen && activeTab != null) capturePlayerState(activeTab);
            updateAdblockStatus();
            handler.postDelayed(this, 15000L);
        }
    };

    private final Runnable playerControlsTicker = new Runnable() {
        @Override
        public void run() {
            if (!foreground || !windowFocused) {
                foregroundWorkStarted = false;
                return;
            }
            // Панель скрыта в fullscreen, а постоянный DOM-probe там лишь создаёт
            // лишнюю работу на старом WebView и может совпасть с обработкой ввода.
            if (!embeddedFullScreen && activeTab != null) refreshPlayerControls(activeTab, false);
            handler.postDelayed(this, 8000L);
        }
    };

    private final Runnable sleepTimerTicker = new Runnable() {
        @Override
        public void run() {
            if (sleepTimerEndsAt <= 0L) return;
            long remaining = sleepTimerEndsAt - SystemClock.elapsedRealtime();
            if (remaining <= 0L) {
                expireSleepTimer();
                return;
            }
            if (remaining <= 60_000L && !sleepTimerWarningShown) {
                sleepTimerWarningShown = true;
                showSleepTimerWarning();
            }
            handler.postDelayed(this, Math.min(30_000L, Math.max(1000L, remaining)));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(shouldUseLightTheme() ? R.style.AppThemeLight : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // Инспекция WebView открывает cookies и сессии всех вкладок любому с adb.
        // Включаем её только в отладочной сборке, не в release.
        try {
            WebView.setWebContentsDebuggingEnabled(false);
        } catch (Throwable ignored) {}
        applyThemePalette();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (lightTheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enableImmersiveMode();

        compact = getResources().getConfiguration().screenWidthDp < 600;
        int widthDp = getResources().getConfiguration().screenWidthDp;
        gridColumns = compact ? 2 : Math.max(4, Math.min(6, widthDp / 210));
        store = new MovieStore(this);
        adBlocker = new AdBlocker(this);
        playerUrlRules = new PlayerUrlRules(this);
        adRemovalStore = new AdRemovalStore(this);
        buildUi();
        playerHost = new PlayerHost(this, this::onPlayerFullscreenChanged);
        handler.postDelayed(this::checkModernWebView, 700L);
        setUpdateAvailable(UpdateManager.hasAvailableUpdate(this));
        refreshHome();
        try {
            restoreTabs();
            String activeKey = store.loadActiveTabKey();
            BrowserTab restored = findTab(activeKey);
            if (restored != null) showTab(restored); else showSearch();
        } catch (Throwable startupError) {
            for (BrowserTab tab : new ArrayList<>(tabs)) tab.destroy();
            tabs.clear();
            store.saveTabs(new ArrayList<>(), "");
            showSearch();
            Toast.makeText(this, "Открыт безопасный режим", Toast.LENGTH_LONG).show();
        }
        handler.postDelayed(() -> UpdateManager.check(this, false, this::setUpdateAvailable), 4500L);
    }

    private void setUpdateAvailable(boolean available) {
        updateAvailable = available;
        if (updateNotificationDot != null) {
            updateNotificationDot.setVisibility(available ? View.VISIBLE : View.GONE);
        }
    }

    private void configureServiceWorkerBlocking() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;
        try {
            ServiceWorkerController controller = ServiceWorkerController.getInstance();
            controller.getServiceWorkerWebSettings().setBlockNetworkLoads(false);
            controller.setServiceWorkerClient(new ServiceWorkerClient() {
                @Override
                public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                    adBlocker.observeRequest("service-worker", request);
                    WebResourceResponse neutralized = adBlocker.interceptKnownVideoAd(request);
                    if (neutralized != null) return neutralized;
                    return null;
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void buildUi() {
        appRoot = new LinearLayout(this);
        appRoot.setOrientation(LinearLayout.VERTICAL);
        appRoot.setBackgroundColor(BG);
        setContentView(appRoot);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(BG);
        appRoot.addView(main, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int side = dp(compact ? 14 : 24);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(side, 0, side, 0);
        header.setBackgroundColor(HEADER_BG);
        appHeader = header;

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_brand_sofa);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(logo, new LinearLayout.LayoutParams(dp(compact ? 34 : 38), dp(compact ? 34 : 38)));

        LinearLayout brandBox = new LinearLayout(this);
        brandBox.setOrientation(LinearLayout.HORIZONTAL);
        brandBox.setGravity(Gravity.CENTER_VERTICAL);
        brandBox.setPadding(dp(10), 0, 0, 0);
        TextView brand = text("Сиди ", compact ? 19 : 24, TEXT);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandBox.addView(brand);
        TextView blue = text("и", compact ? 19 : 24, ACCENT);
        blue.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandBox.addView(blue);
        TextView brandEnd = text(" смотри", compact ? 19 : 24, TEXT);
        brandEnd.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brandBox.addView(brandEnd);
        header.addView(brandBox, new LinearLayout.LayoutParams(0, dp(46), 1f));

        TextView versionBadge = text("v" + shortAppVersion(), compact ? 9 : 10, TEXT);
        versionBadge.setGravity(Gravity.CENTER);
        versionBadge.setSingleLine(true);
        versionBadge.setBackground(roundedStroke(PANEL_2, ACCENT_DARK, 12, 1));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(dp(compact ? 48 : 56), dp(30));
        vp.setMargins(0, 0, dp(10), 0);
        header.addView(versionBadge, vp);

        FrameLayout settingsWrap = new FrameLayout(this);
        ImageButton settingsButton = imageButton(R.drawable.ic_settings, false);
        settingsButton.setContentDescription("Настройки");
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        FrameLayout.LayoutParams settingsParams = new FrameLayout.LayoutParams(dp(42), dp(42), Gravity.CENTER);
        settingsWrap.addView(settingsButton, settingsParams);

        updateNotificationDot = new View(this);
        GradientDrawable updateDotBackground = new GradientDrawable();
        updateDotBackground.setShape(GradientDrawable.OVAL);
        updateDotBackground.setColor(Color.rgb(235, 55, 55));
        updateNotificationDot.setBackground(updateDotBackground);
        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.TOP | Gravity.END);
        dotParams.setMargins(0, dp(3), dp(2), 0);
        settingsWrap.addView(updateNotificationDot, dotParams);
        updateNotificationDot.setVisibility(updateAvailable ? View.VISIBLE : View.GONE);
        header.addView(settingsWrap, new LinearLayout.LayoutParams(dp(48), dp(44)));
        main.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 52 : 58)));

        // Вкладки могут быть шире экрана. Явно показываем это стрелками по краям,
        // а не надеемся, что пользователь догадается о горизонтальном скролле.
        FrameLayout tabStrip = new FrameLayout(this);
        tabStrip.setBackgroundColor(HEADER_BG);
        appTabStrip = tabStrip;

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        tabScrollView = tabScroll;
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setFillViewport(false);
        tabScroll.setClipToPadding(false);
        tabScroll.setPadding(side + dp(26), dp(2), side + dp(26), dp(4));
        tabScroll.setBackgroundColor(Color.TRANSPARENT);
        tabButtons = new LinearLayout(this);
        tabButtons.setOrientation(LinearLayout.HORIZONTAL);
        tabButtons.setGravity(Gravity.CENTER_VERTICAL);
        tabScroll.addView(tabButtons, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        tabStrip.addView(tabScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        tabOverflowLeft = createTabOverflowHint("‹", false);
        FrameLayout.LayoutParams leftHintParams = new FrameLayout.LayoutParams(
                dp(34), dp(40), Gravity.START | Gravity.CENTER_VERTICAL);
        leftHintParams.setMargins(dp(4), 0, 0, 0);
        tabStrip.addView(tabOverflowLeft, leftHintParams);

        tabOverflowRight = createTabOverflowHint("›", true);
        FrameLayout.LayoutParams rightHintParams = new FrameLayout.LayoutParams(
                dp(34), dp(40), Gravity.END | Gravity.CENTER_VERTICAL);
        rightHintParams.setMargins(0, 0, dp(4), 0);
        tabStrip.addView(tabOverflowRight, rightHintParams);

        tabScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                updateTabOverflowHints());
        main.addView(tabStrip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        content = new FrameLayout(this);
        content.setBackgroundColor(BG);
        main.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buildSearchPanel();
        rebuildTabButtons();
    }

    private void buildSearchPanel() {
        searchScroll = new ScrollView(this);
        searchScroll.setFillViewport(true);
        searchScroll.setClipToPadding(false);
        searchScroll.setBackgroundColor(BG);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int side = compact ? dp(14) : dp(34);
        panel.setPadding(side, dp(compact ? 14 : 18), side, dp(58));
        searchScroll.addView(panel, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Что будем смотреть", compact ? 26 : 32, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(2), 0, 0, dp(compact ? 14 : 18));
        panel.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(8), dp(5), dp(5), dp(5));
        searchRow.setBackground(roundedStroke(PANEL_2, BORDER, 15, 1));

        ImageView searchIcon = new ImageView(this);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setColorFilter(lightTheme ? ACCENT : TEXT);
        searchIcon.setAlpha(0.72f);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(42), dp(50)));

        queryInput = new EditText(this);
        queryInput.setSingleLine(true);
        queryInput.setTextColor(TEXT);
        queryInput.setHintTextColor(MUTED);
        queryInput.setHint("Поиск фильма или сериала");
        queryInput.setTextSize(compact ? 16 : 18);
        queryInput.setPadding(dp(2), 0, dp(10), 0);
        queryInput.setBackgroundColor(Color.TRANSPARENT);
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setOnFocusChangeListener((v, focused) -> searchRow.setBackground(roundedStroke(
                focused ? FOCUS_BG : PANEL_2,
                focused ? ACCENT : BORDER, 15, focused ? 2 : 1)));
        queryInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                startSearch();
                return true;
            }
            return false;
        });
        searchRow.addView(queryInput, new LinearLayout.LayoutParams(0, dp(54), 1f));

        ImageButton searchButton = imageButton(R.drawable.ic_search, true);
        searchButton.setContentDescription("Найти");
        searchButton.setOnClickListener(v -> startSearch());
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(dp(52), dp(48));
        sbp.setMargins(dp(4), 0, 0, 0);
        searchRow.addView(searchButton, sbp);
        panel.addView(searchRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(60)));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(dp(3), dp(7), 0, 0);
        searchProgress = new ProgressBar(this);
        searchProgress.setVisibility(View.GONE);
        statusRow.addView(searchProgress, new LinearLayout.LayoutParams(dp(18), dp(18)));
        statusText = text("", compact ? 11 : 12, MUTED);
        statusText.setPadding(dp(8), 0, 0, 0);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, dp(25), 1f));
        panel.addView(statusRow);

        LinearLayout resultsHeader = (LinearLayout) sectionTitle("Результаты поиска", "");
        resultsHeader.setPadding(0, dp(compact ? 14 : 20), 0, dp(9));
        panel.addView(resultsHeader);
        resultsList = new LinearLayout(this);
        resultsList.setOrientation(LinearLayout.VERTICAL);
        resultsList.setClipToPadding(false);
        resultsList.setPadding(0, 0, 0, dp(3));
        panel.addView(resultsList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        panel.addView(sectionTitle("Недавние", ""));
        HorizontalScrollView recentScroll = new HorizontalScrollView(this);
        recentScroll.setHorizontalScrollBarEnabled(false);
        recentScroll.setClipToPadding(false);
        recentList = new LinearLayout(this);
        recentList.setOrientation(LinearLayout.HORIZONTAL);
        recentList.setPadding(0, 0, dp(8), dp(4));
        recentScroll.addView(recentList, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(recentScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        panel.addView(sectionTitle("Избранное", ""));
        HorizontalScrollView favScroll = new HorizontalScrollView(this);
        favScroll.setHorizontalScrollBarEnabled(false);
        favScroll.setClipToPadding(false);
        favoritesList = new LinearLayout(this);
        favoritesList.setOrientation(LinearLayout.HORIZONTAL);
        favoritesList.setPadding(0, 0, dp(8), dp(4));
        favScroll.addView(favoritesList, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(favScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View sectionTitle(String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(compact ? 24 : 28), 0, dp(10));
        TextView heading = text(title.toUpperCase(Locale.ROOT), compact ? 13 : 14, Color.rgb(132, 196, 255));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setLetterSpacing(0.06f);
        row.addView(heading);
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView meta = text(subtitle, compact ? 10 : 11, MUTED);
            meta.setPadding(dp(10), dp(2), 0, 0);
            row.addView(meta);
        }
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(25, 38, 54));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(1), 1f);
        lp.setMargins(dp(14), dp(3), 0, 0);
        row.addView(line, lp);
        return row;
    }

    private void refreshHome() {
        renderRecents();
        renderFavorites();
    }

    private void renderContinue() {
        continueList.removeAllViews();
        int count = 0;
        for (MovieRecord record : store.recents()) {
            if (record.positionSeconds <= 0 && record.season <= 0 && record.episode <= 0 &&
                    (record.translation == null || record.translation.trim().isEmpty())) continue;
            continueList.addView(continueCard(record));
            if (++count >= 12) break;
        }
        if (count == 0) continueList.addView(emptyState("Здесь появится то, что ты начал смотреть."));
    }

    private void renderFavorites() {
        favoritesList.removeAllViews();
        List<MovieRecord> favorites = store.favorites();
        if (favorites.isEmpty()) {
            favoritesList.addView(emptyState("Здесь появятся фильмы со звёздочкой."));
            return;
        }
        for (MovieRecord record : favorites) favoritesList.addView(movieCard(record, false));
    }

    private void renderRecents() {
        recentList.removeAllViews();
        List<MovieRecord> recents = store.recents();
        if (recents.isEmpty()) {
            recentList.addView(emptyState("Открой фильм — он останется здесь."));
            return;
        }
        for (MovieRecord record : recents) recentList.addView(movieCard(record, true));
    }

    private View emptyState(String message) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(14), dp(20), dp(14));
        card.setBackground(roundedStroke(PANEL_2, BORDER, 16, 1));
        TextView title = text("Здесь пока ничего нет", compact ? 15 : 16, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        TextView body = text(message, compact ? 12 : 13, MUTED);
        body.setMaxLines(2);
        card.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(compact ? 290 : 370), dp(96));
        p.setMargins(0, 0, dp(12), 0);
        card.setLayoutParams(p);
        return card;
    }

    private View continueCard(MovieRecord record) {
        int width = dp(compact ? 236 : 292);
        int imageHeight = dp(compact ? 132 : 164);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(8));
        card.setBackground(roundedStroke(Color.rgb(16, 18, 23), Color.rgb(39, 41, 48), 15, 1));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> openRecord(record));
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f).setDuration(120).start();
            v.setBackground(roundedStroke(
                    focused ? Color.rgb(16, 35, 55) : Color.rgb(16, 18, 23),
                    focused ? ACCENT : Color.rgb(39, 41, 48), 15, focused ? 2 : 1));
        });

        FrameLayout art = posterBox(record, width - dp(12), imageHeight);
        card.addView(art, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, imageHeight));

        TextView name = text(record.title, compact ? 14 : 16, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(1);
        name.setPadding(dp(4), dp(8), dp(4), 0);
        card.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(31)));

        String progress = record.progressLabel();
        TextView progressText = text(progress.isEmpty() ? "Продолжить" : progress,
                compact ? 11 : 12, Color.rgb(126, 196, 255));
        progressText.setMaxLines(1);
        progressText.setPadding(dp(4), 0, dp(4), 0);
        card.addView(progressText, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(25)));

        if (record.durationSeconds > 0 && record.positionSeconds > 0) {
            FrameLayout track = new FrameLayout(this);
            track.setBackground(rounded(Color.rgb(48, 50, 56), 2));
            View fill = new View(this);
            fill.setBackground(rounded(ACCENT, 2));
            float ratio = Math.min(1f, (float) record.positionSeconds / (float) record.durationSeconds);
            track.addView(fill, new FrameLayout.LayoutParams(Math.max(dp(8), Math.round((width - dp(20)) * ratio)), dp(3)));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
            tp.setMargins(dp(4), dp(2), dp(4), 0);
            card.addView(track, tp);
        }

        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.setMargins(0, 0, dp(12), dp(4));
        card.setLayoutParams(outer);
        return card;
    }

    private View movieCard(MovieRecord record, boolean showDelete) {
        int width = dp(compact ? 138 : 170);
        int posterHeight = dp(compact ? 194 : 238);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(5), dp(5), dp(5), dp(7));
        card.setBackground(roundedStroke(PANEL_2, BORDER, 14, 1));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnClickListener(v -> openRecord(record));
        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.05f : 1f).scaleY(focused ? 1.05f : 1f).setDuration(120).start();
            v.setBackground(roundedStroke(
                    focused ? FOCUS_BG : PANEL_2,
                    focused ? ACCENT : BORDER, 14, focused ? 2 : 1));
        });

        card.addView(posterBox(record, width - dp(10), posterHeight),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, posterHeight));

        TextView name = text(record.title, compact ? 13 : 15, TEXT);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setPadding(dp(3), dp(7), dp(3), 0);
        card.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 42 : 47)));

        String metaValue = ("series".equals(record.type) ? "Сериал" : "Фильм") +
                (record.year.isEmpty() ? "" : "  ·  " + record.year);
        TextView meta = text(metaValue, compact ? 10 : 11, MUTED);
        meta.setPadding(dp(3), 0, dp(3), 0);
        card.addView(meta, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button favorite = smallButton(record.favorite ? "★" : "☆");
        favorite.setOnClickListener(v -> {
            record.favorite = !record.favorite;
            store.update(record);
            refreshHome();
            refreshPlayerToolbars();
        });
        actions.addView(favorite, new LinearLayout.LayoutParams(0, dp(36), 1f));
        Button edit = smallButton("✎");
        edit.setOnClickListener(v -> showEditDialog(record));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(36), 1f);
        ep.setMargins(dp(4), 0, 0, 0);
        actions.addView(edit, ep);
        if (showDelete) {
            Button delete = smallButton("×");
            delete.setContentDescription("Удалить из истории " + record.title);
            delete.setOnClickListener(v -> {
                store.removeRecent(record.key);
                refreshHome();
            });
            LinearLayout.LayoutParams xp = new LinearLayout.LayoutParams(0, dp(36), 1f);
            xp.setMargins(dp(4), 0, 0, 0);
            actions.addView(delete, xp);
        }
        card.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(39)));

        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.setMargins(0, 0, dp(10), dp(4));
        card.setLayoutParams(outer);
        return card;
    }

    private FrameLayout posterBox(MovieRecord record, int width, int height) {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(gradient(Color.rgb(20, 40, 58), Color.rgb(8, 17, 28), 13));
        frame.setClipToOutline(true);

        String initial = record.title == null || record.title.trim().isEmpty()
                ? "▶" : record.title.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        TextView placeholder = text(initial, compact ? 42 : 52, Color.rgb(85, 130, 166));
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        frame.addView(placeholder, new FrameLayout.LayoutParams(width, height));

        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackgroundColor(Color.TRANSPARENT);
        image.setClipToOutline(true);
        frame.addView(image, new FrameLayout.LayoutParams(width, height));
        loadImage(record.posterUrl, record.sourceUrl, image);
        return frame;
    }

    private void startSearch() {
        String query = queryInput.getText().toString().trim();
        if (query.isEmpty()) {
            statusText.setText("Введи название или ссылку.");
            queryInput.requestFocus();
            return;
        }
        dismissKeyboard();
        if (looksLikeKinopoiskUrl(query)) {
            try {
                String canonical = canonicalKinopoiskUrl(query);
                MovieRecord record = store.getOrCreateFromUrl(extractFallbackTitle(canonical), canonical);
                openRecord(record);
            } catch (IllegalArgumentException e) {
                statusText.setText(e.getMessage());
            }
            return;
        }

        int generation = ++searchGeneration;
        searchProgress.setVisibility(View.VISIBLE);
        statusText.setText("Ищу…");
        resultsList.removeAllViews();
        ensureSearchWebView();

        String encoded;
        try {
            encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            encoded = Uri.encode(query);
        }
        searchWebView.loadUrl("https://www.kinopoisk.ru/index.php?kp_query=" + encoded);
        handler.postDelayed(() -> extractSearchResults(generation, 1), 1800);
        handler.postDelayed(() -> extractSearchResults(generation, 2), 3800);
        handler.postDelayed(() -> extractSearchResults(generation, 3), 7000);
    }

    private void ensureSearchWebView() {
        if (searchWebView != null) return;
        searchWebView = new WebView(this);
        configureWebSettings(searchWebView, true);
        // INVISIBLE WebView не формирует нормальный viewport, из-за чего lazy-load постеры
        // Кинопоиска остаются пустыми. Держим WebView видимым, но почти прозрачным и вне экрана.
        searchWebView.setVisibility(View.VISIBLE);
        searchWebView.setAlpha(0.01f);
        searchWebView.setFocusable(false);
        searchWebView.setFocusableInTouchMode(false);
        searchWebView.setClickable(false);
        searchWebView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                adBlocker.observeRequest("search", request);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                final int generation = searchGeneration;
                view.evaluateJavascript("(function(){var y=0;var t=setInterval(function(){y+=500;window.scrollTo(0,y);if(y>=document.body.scrollHeight){clearInterval(t);window.scrollTo(0,0);}},90);})()", null);
                handler.postDelayed(() -> extractSearchResults(generation, 0), 500);
                handler.postDelayed(() -> extractSearchResults(generation, 0), 1200);
            }
        });
        attachHiddenSearchWebView();
    }

    private void attachHiddenSearchWebView() {
        if (searchWebView == null) return;
        ViewGroup parent = (ViewGroup) searchWebView.getParent();
        if (parent != null) parent.removeView(searchWebView);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(390), dp(720));
        lp.leftMargin = -dp(430);
        lp.topMargin = -dp(760);
        content.addView(searchWebView, 0, lp);
    }

    private void extractSearchResults(int generation, int attempt) {
        if (generation != searchGeneration || searchWebView == null) return;
        String js = "(function(){" +
                "function abs(v){if(!v)return '';v=String(v).replace(/&amp;/g,'&').trim();" +
                "if(v.indexOf(',')>=0){var ps=v.split(','),best='';for(var z=0;z<ps.length;z++){var q=ps[z].trim().split(/\\s+/)[0];if(q)best=q;}v=best;}else v=v.split(/\\s+/)[0];" +
                "if(v.indexOf('//')===0)v='https:'+v;if(v.indexOf('/')===0)v=location.origin+v;" +
                "if(!/^https?:/i.test(v)||/^(data|blob):/i.test(v))return '';return v;}" +
                "function canon(h){var m=String(h||'').match(/kinopoisk\\.ru\\/(film|series)\\/(\\d+)/);return m?m[1]+':'+m[2]:'';}" +
                "function urls(im){var out=[],vals=[im.currentSrc,im.src,im.getAttribute&&im.getAttribute('src'),im.getAttribute&&im.getAttribute('data-src')," +
                "im.getAttribute&&im.getAttribute('data-original'),im.getAttribute&&im.getAttribute('data-lazy-src'),im.getAttribute&&im.getAttribute('data-srcset'),im.getAttribute&&im.getAttribute('srcset')];" +
                "for(var i=0;i<vals.length;i++){var u=abs(vals[i]);if(u&&out.indexOf(u)<0)out.push(u);}return out;}" +
                "function score(im,u,id,a){var sc=0,low=u.toLowerCase();if(/logo|avatar|sprite|icon|blank|transparent|placeholder|pixel|stub/.test(low))sc-=120;" +
                "if(new RegExp('(?:_|/|=)'+id+'(?:[_.?&/]|$)').test(u))sc+=100;if(a.contains&&a.contains(im))sc+=55;" +
                "var link=im.closest&&im.closest('a[href*=\"/film/\"],a[href*=\"/series/\"]');if(link){sc+=canon(link.href)==canon(a.href)?80:-160;}" +
                "var w=im.naturalWidth||im.width||parseInt(im.getAttribute&&im.getAttribute('width'))||0,h=im.naturalHeight||im.height||parseInt(im.getAttribute&&im.getAttribute('height'))||0;" +
                "if(h>w&&h>80)sc+=24;if(w>90&&h>120)sc+=14;if(/kinopoisk-image|avatars\\.mds|kp\\.yandex/.test(low))sc+=20;return sc;}" +
                "function poster(a,id){var nodes=[],box=a;try{nodes=nodes.concat([].slice.call(a.querySelectorAll('img,picture source')));}catch(e){}" +
                "for(var up=0;up<5&&box;up++,box=box.parentElement){try{var links=box.querySelectorAll('a[href*=\"/film/\"],a[href*=\"/series/\"]');" +
                "var same=0,other=0;for(var j=0;j<links.length;j++){if(canon(links[j].href)==canon(a.href))same++;else other++;}" +
                "if(same>0&&other<=1)nodes=nodes.concat([].slice.call(box.querySelectorAll('img,picture source')));if(other>2)break;}catch(e){}}" +
                "var best='',bestScore=-999;for(var n=0;n<nodes.length;n++){var us=urls(nodes[n]);for(var k=0;k<us.length;k++){var sc=score(nodes[n],us[k],id,a);if(sc>bestScore){bestScore=sc;best=us[k];}}}" +
                "return bestScore>0?best:'';}" +
                "var seen={},usedPosters={},out=[],as=document.querySelectorAll('a[href*=\"/film/\"],a[href*=\"/series/\"]');" +
                "for(var i=0;i<as.length&&out.length<24;i++){var a=as[i],h=(a.href||'').split('?')[0],m=h.match(/^https?:\\/\\/(?:www\\.)?kinopoisk\\.ru\\/(film|series)\\/(\\d+)\\/?/);" +
                "if(!m)continue;h='https://www.kinopoisk.ru/'+m[1]+'/'+m[2]+'/';if(seen[h])continue;" +
                "var c=a;for(var up=0;up<5&&c;up++){var txt=(c.innerText||'').trim(),links=c.querySelectorAll?c.querySelectorAll('a[href*=\"/film/\"],a[href*=\"/series/\"]'):[];if(txt.length>2&&links.length<=3)break;c=c.parentElement;}c=c||a.parentElement||a;" +
                "var t=(a.getAttribute('aria-label')||a.getAttribute('title')||a.innerText||'').trim();if(!t&&c){var nn=c.querySelector('h1,h2,h3,[data-tid*=name],[class*=name],[class*=title]');t=nn?(nn.innerText||''):'';}" +
                "t=t.trim().split('\\n')[0].trim();if(t.length<2||t.length>140)t=(m[1]=='series'?'Сериал ':'Фильм ')+m[2];" +
                "var txt=(c&&c.innerText)||'',ym=txt.match(/(?:19|20)\\d{2}/),p=poster(a,m[2]);" +
                "if(p&&usedPosters[p]&&p.indexOf(m[2])<0)p='';if(p)usedPosters[p]=1;seen[h]=1;out.push({title:t,year:ym?ym[0]:'',url:h,poster:p,type:m[1]});}" +
                "return JSON.stringify(out);})()";

        searchWebView.evaluateJavascript(js, value -> {
            if (generation != searchGeneration) return;
            List<SearchItem> items = parseSearchResults(value);
            if (!items.isEmpty()) {
                searchProgress.setVisibility(View.GONE);
                statusText.setText("Найдено: " + items.size());
                renderSearchResults(items);
            } else if (attempt == 3) {
                searchProgress.setVisibility(View.GONE);
                statusText.setText("Не удалось прочитать выдачу. Вставь прямую ссылку.");
            }
        });
    }

    private List<SearchItem> parseSearchResults(String value) {
        List<SearchItem> result = new ArrayList<>();
        if (value == null || "null".equals(value)) return result;
        try {
            Object decoded = new JSONTokener(value).nextValue();
            String json = decoded instanceof String ? (String) decoded : value;
            JSONArray array = new JSONArray(json);
            Map<String, SearchItem> unique = new LinkedHashMap<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                SearchItem item = new SearchItem(
                        o.optString("title", "Без названия"), o.optString("year", ""),
                        o.optString("url", ""), o.optString("poster", ""),
                        o.optString("type", "film"));
                if (looksLikeKinopoiskUrl(item.url)) unique.put(item.url, item);
            }
            result.addAll(unique.values());
        } catch (Exception ignored) {
        }
        return result;
    }

    private void renderSearchResults(List<SearchItem> items) {
        resultsList.removeAllViews();
        int max = compact ? Math.min(items.size(), 8) : Math.min(items.size(), 10);
        for (int i = 0; i < max; i++) {
            SearchItem item = items.get(i);
            MovieRecord record = MovieRecord.fromSearch(item.title, item.year, item.type, item.url, item.poster);
            MovieRecord saved = store.get(record.key);
            if (saved != null) {
                record.favorite = saved.favorite;
                record.note = saved.note;
                record.translation = saved.translation;
                record.translationManual = saved.translationManual;
                record.episodeManual = saved.episodeManual;
                record.season = saved.season;
                record.episode = saved.episode;
                record.positionSeconds = saved.positionSeconds;
                record.durationSeconds = saved.durationSeconds;
                if (record.posterUrl.isEmpty()) record.posterUrl = saved.posterUrl;
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(6), dp(10), dp(6));
            row.setBackground(roundedStroke(PANEL_2, BORDER, 13, 1));
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnClickListener(v -> { dismissKeyboard(); openRecord(store.merge(record)); });
            row.setOnFocusChangeListener((v, focused) -> {
                v.animate().scaleX(focused ? 1.012f : 1f).scaleY(focused ? 1.012f : 1f).setDuration(100).start();
                v.setBackground(roundedStroke(focused ? FOCUS_BG : PANEL_2, focused ? ACCENT : BORDER, 13, focused ? 2 : 1));
            });

            int thumbW = dp(compact ? 62 : 58);
            int thumbH = dp(compact ? 88 : 82);
            row.addView(posterBox(record, thumbW, thumbH), new LinearLayout.LayoutParams(thumbW, thumbH));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setGravity(Gravity.CENTER_VERTICAL);
            info.setPadding(dp(compact ? 10 : 14), 0, dp(8), 0);
            TextView name = text(record.title, compact ? 14 : 16, TEXT);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setMaxLines(compact ? 2 : 1);
            info.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(compact ? 40 : 25)));
            String meta = ("series".equals(record.type) ? "Сериал" : "Фильм") + (record.year.isEmpty() ? "" : "  ·  " + record.year);
            if (record.progressLabel() != null && !record.progressLabel().isEmpty()) meta += "  ·  " + record.progressLabel();
            TextView details = text(meta, compact ? 10 : 11, MUTED);
            details.setMaxLines(1);
            info.addView(details, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
            if (record.note != null && !record.note.trim().isEmpty()) {
                TextView note = text("✎  " + record.note, compact ? 10 : 11, Color.rgb(126, 196, 255));
                note.setMaxLines(1);
                info.addView(note, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));
            }
            row.addView(info, new LinearLayout.LayoutParams(0, thumbH, 1f));

            TextView arrow = text("›", compact ? 28 : 30, Color.rgb(117, 120, 130));
            arrow.setGravity(Gravity.CENTER);
            row.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(42)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(6));
            resultsList.addView(row, lp);
        }
    }

    private void openRecord(MovieRecord record) {
        store.touchRecent(record);
        refreshHome();


        for (BrowserTab tab : tabs) {
            if (tab.key.equals(record.key)) {
                showTab(tab);
                return;
            }
        }
        if (tabs.size() >= MAX_TABS) {
            BrowserTab old = tabs.remove(0);
            old.destroy();
        }
        BrowserTab tab = new BrowserTab(record.key);
        tabs.add(tab);
        saveTabs();
        showTab(tab);
    }


    private void restoreTabs() {
        for (String key : store.loadTabKeys()) {
            if (store.get(key) != null && tabs.size() < MAX_TABS) tabs.add(new BrowserTab(key));
        }
        rebuildTabButtons();
    }

    private BrowserTab findTab(String key) {
        if (key == null || key.isEmpty()) return null;
        for (BrowserTab tab : tabs) if (key.equals(tab.key)) return tab;
        return null;
    }

    private void showSearch() {
        if (embeddedFullScreen) exitEmbeddedFullScreen();
        if (activeTab != null) {
            capturePlayerState(activeTab);
            pauseTabWebView(activeTab);
        }
        activeTab = null;
        content.removeAllViews();
        ViewGroup parent = (ViewGroup) searchScroll.getParent();
        if (parent != null) parent.removeView(searchScroll);
        content.addView(searchScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        attachHiddenSearchWebView();
        rebuildTabButtons();
        saveTabs();
    }

    private void showTab(BrowserTab tab) {
        if (embeddedFullScreen && activeTab != tab) exitEmbeddedFullScreen();
        if (activeTab != null && activeTab != tab) {
            capturePlayerState(activeTab);
            pauseTabWebView(activeTab);
        }
        activeTab = tab;
        content.removeAllViews();
        View player = tab.getOrCreateView();
        ViewGroup parent = (ViewGroup) player.getParent();
        if (parent != null) parent.removeView(player);
        content.addView(player, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rebuildTabButtons();
        saveTabs();
        if (tab.webView != null) {
            resumeTabWebView(tab);
            handler.postDelayed(() -> refreshPlayerControls(tab, false), 180L);
        }
    }

    private void pauseTabWebView(BrowserTab tab) {
        if (tab == null || tab.webView == null) return;
        try { tab.webView.onPause(); } catch (Throwable ignored) { }
        handler.postDelayed(() -> {
            if (activeTab != tab) tab.hibernate();
        }, 250L);
    }

    private void resumeTabWebView(BrowserTab tab) {
        if (tab == null || tab.webView == null || !foreground) return;
        try { tab.webView.onResume(); } catch (Throwable ignored) { }
    }

    private void closeTab(BrowserTab tab) {
        capturePlayerState(tab);
        int index = tabs.indexOf(tab);
        boolean wasActive = activeTab == tab;
        if (index >= 0) tabs.remove(index);
        tab.destroy();
        if (wasActive) {
            if (tabs.isEmpty()) showSearch();
            else showTab(tabs.get(Math.max(0, index - 1)));
        } else {
            rebuildTabButtons();
            saveTabs();
        }
    }

    private void rebuildTabButtons() {
        if (tabButtons == null) return;
        tabButtons.removeAllViews();
        View search = createTabChip("Поиск", activeTab == null, true, null,
                v -> showSearch(), null);
        tabButtons.addView(search, tabParams());

        for (BrowserTab tab : tabs) {
            MovieRecord record = store.get(tab.key);
            if (record == null) continue;
            View chip = createTabChip(shortTitle(record.title), activeTab == tab, false, record,
                    v -> showTab(tab), v -> closeTab(tab));
            tabButtons.addView(chip, tabParams());
        }

        ImageButton add = imageButton(R.drawable.ic_plus, false);
        add.setContentDescription("Открыть поиск");
        add.setOnClickListener(v -> showSearch());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(42), dp(40));
        ap.setMargins(0, 0, dp(8), 0);
        tabButtons.addView(add, ap);
        if (tabScrollView != null) {
            tabScrollView.post(() -> {
                updateTabOverflowHints();
                scrollActiveTabIntoView();
            });
        }
    }

    private TextView createTabOverflowHint(String glyph, boolean right) {
        TextView hint = text(glyph, compact ? 24 : 28, TEXT);
        hint.setGravity(Gravity.CENTER);
        hint.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hint.setBackground(roundedStroke(HEADER_BG, BORDER, 14, 1));
        hint.setFocusable(true);
        hint.setClickable(true);
        hint.setContentDescription(right ? "Показать следующие вкладки" : "Показать предыдущие вкладки");
        hint.setOnClickListener(v -> {
            if (tabScrollView == null) return;
            int step = Math.max(dp(220), Math.round(tabScrollView.getWidth() * 0.68f));
            tabScrollView.smoothScrollBy(right ? step : -step, 0);
        });
        hint.setOnFocusChangeListener((v, focused) -> hint.setBackground(roundedStroke(
                focused ? FOCUS_BG : HEADER_BG, focused ? ACCENT : BORDER, 14, 1)));
        return hint;
    }

    private void updateTabOverflowHints() {
        if (tabScrollView == null) return;
        boolean canLeft = tabScrollView.canScrollHorizontally(-1);
        boolean canRight = tabScrollView.canScrollHorizontally(1);
        if (tabOverflowLeft != null) tabOverflowLeft.setVisibility(canLeft ? View.VISIBLE : View.INVISIBLE);
        if (tabOverflowRight != null) tabOverflowRight.setVisibility(canRight ? View.VISIBLE : View.INVISIBLE);
    }

    private void scrollActiveTabIntoView() {
        if (tabScrollView == null || tabButtons == null || tabButtons.getChildCount() == 0) return;
        int selectedIndex = 0;
        if (activeTab != null) {
            int logical = 1; // child 0 = Поиск
            for (BrowserTab tab : tabs) {
                MovieRecord record = store.get(tab.key);
                if (record == null) continue;
                if (tab == activeTab) { selectedIndex = logical; break; }
                logical++;
            }
        }
        if (selectedIndex < 0 || selectedIndex >= tabButtons.getChildCount()) return;
        View selected = tabButtons.getChildAt(selectedIndex);
        int target = Math.max(0, selected.getLeft() - dp(54));
        tabScrollView.smoothScrollTo(target, 0);
        tabScrollView.postDelayed(this::updateTabOverflowHints, 220L);
    }

    private LinearLayout.LayoutParams tabParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        p.setMargins(0, 0, dp(8), 0);
        return p;
    }

    private View createPlayerView(BrowserTab tab) {
        MovieRecord record = store.get(tab.key);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(compact ? 10 : 20), dp(5), dp(compact ? 10 : 20), dp(5));
        toolbar.setBackground(gradient(Color.rgb(9, 21, 34), Color.rgb(5, 12, 21), 0));
        tab.toolbar = toolbar;

        ImageButton home = imageButton(R.drawable.ic_home, false);
        home.setContentDescription("На главную");
        home.setOnClickListener(v -> showSearch());
        toolbar.addView(home, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = text(record == null ? "Видео" : record.title, compact ? 15 : 17, TEXT);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setMaxLines(1);
        title.setPadding(dp(12), 0, dp(12), 0);
        tab.toolbarTitle = title;
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1f));

        LinearLayout playerControls = new LinearLayout(this);
        playerControls.setOrientation(LinearLayout.HORIZONTAL);
        playerControls.setGravity(Gravity.CENTER_VERTICAL);
        playerControls.setVisibility(View.GONE);
        tab.controlsGroup = playerControls;

        Button translationControl = playerControlButton("Озвучка");
        translationControl.setContentDescription("Выбрать перевод или озвучку");
        translationControl.setOnClickListener(v -> showPlayerControlDialog(tab,
                PlayerControlsBridge.KIND_TRANSLATION));
        tab.translationControl = translationControl;
        playerControls.addView(translationControl, new LinearLayout.LayoutParams(
                dp(compact ? 122 : 180), dp(42)));

        Button seasonControl = playerControlButton("Сезон");
        seasonControl.setContentDescription("Выбрать сезон");
        seasonControl.setOnClickListener(v -> showPlayerControlDialog(tab,
                PlayerControlsBridge.KIND_SEASON));
        tab.seasonControl = seasonControl;
        LinearLayout.LayoutParams seasonControlParams = new LinearLayout.LayoutParams(
                dp(compact ? 88 : 112), dp(42));
        seasonControlParams.setMargins(dp(7), 0, 0, 0);
        playerControls.addView(seasonControl, seasonControlParams);

        Button episodeControl = playerControlButton("Серия");
        episodeControl.setContentDescription("Выбрать серию");
        episodeControl.setOnClickListener(v -> showPlayerControlDialog(tab,
                PlayerControlsBridge.KIND_EPISODE));
        tab.episodeControl = episodeControl;
        LinearLayout.LayoutParams episodeControlParams = new LinearLayout.LayoutParams(
                dp(compact ? 88 : 112), dp(42));
        episodeControlParams.setMargins(dp(7), 0, 0, 0);
        playerControls.addView(episodeControl, episodeControlParams);

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        controlsParams.setMargins(0, 0, dp(8), 0);
        toolbar.addView(playerControls, controlsParams);

        Button adPicker = smallButton("Убрать рекламу");
        adPicker.setTextSize(12);
        adPicker.setSingleLine(true);
        adPicker.setContentDescription("Режим ручного удаления рекламы");
        adPicker.setOnClickListener(v -> {
            if (adRemovalMode) stopAdRemovalMode(tab, true);
            else startAdRemovalMode(tab);
        });
        tab.adPickerButton = adPicker;
        LinearLayout.LayoutParams adPickerParams = new LinearLayout.LayoutParams(dp(compact ? 112 : 136), dp(42));
        adPickerParams.setMargins(0, 0, dp(7), 0);
        toolbar.addView(adPicker, adPickerParams);

        Button markVideoAd = smallButton("Это видеореклама");
        markVideoAd.setTextSize(11);
        markVideoAd.setSingleLine(true);
        markVideoAd.setContentDescription("Пометить текущую видеорекламу для диагностики");
        markVideoAd.setOnClickListener(v -> markCurrentVideoAdvertisement(tab));
        tab.markVideoAdButton = markVideoAd;
        LinearLayout.LayoutParams markVideoAdParams = new LinearLayout.LayoutParams(dp(compact ? 124 : 154), dp(42));
        markVideoAdParams.setMargins(0, 0, dp(7), 0);
        toolbar.addView(markVideoAd, markVideoAdParams);

        Button undoAd = smallButton("↶");
        undoAd.setTextSize(18);
        undoAd.setContentDescription("Отменить последнее удаление рекламы");
        undoAd.setOnClickListener(v -> undoManualAdRemoval());
        tab.undoAdButton = undoAd;
        LinearLayout.LayoutParams undoAdParams = new LinearLayout.LayoutParams(dp(44), dp(42));
        undoAdParams.setMargins(0, 0, dp(7), 0);
        toolbar.addView(undoAd, undoAdParams);

        Button fav = actionButton("",
                record != null && record.favorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
        fav.setContentDescription("Добавить в избранное");
        fav.setOnClickListener(v -> {
            MovieRecord current = store.get(tab.key);
            if (current == null) return;
            current.favorite = !current.favorite;
            store.update(current);
            refreshHome();
            refreshPlayerToolbars();
        });
        tab.favoriteButton = fav;
        toolbar.addView(fav, new LinearLayout.LayoutParams(dp(46), dp(42)));

        Button note = actionButton("", R.drawable.ic_note);
        note.setContentDescription("Заметка");
        note.setOnClickListener(v -> {
            MovieRecord current = store.get(tab.key);
            if (current != null) showEditDialog(current);
        });
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(46), dp(42));
        np.setMargins(dp(7), 0, 0, 0);
        toolbar.addView(note, np);

        Button reload = actionButton("", R.drawable.ic_refresh);
        reload.setContentDescription("Обновить плеер");
        reload.setOnClickListener(v -> {
            if (tab.webView != null) {
                tab.playerIsolated = false;
                tab.playerReady = false;
                tab.playbackResourceSeen = false;
                showPlayerShield(tab, "Загружаем плеер…");
                showPlayerLoading(tab);
                tab.webView.reload();
            }
        });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(46), dp(42));
        rp.setMargins(dp(7), 0, 0, 0);
        toolbar.addView(reload, rp);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        FrameLayout playerFrame = new FrameLayout(this);
        playerFrame.setBackgroundColor(Color.BLACK);
        WebView web = createPlayerWebView(tab);
        tab.webView = web;
        playerFrame.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView interactionShield = text("Загружаем плеер…", compact ? 16 : 18, TEXT);
        interactionShield.setGravity(Gravity.CENTER);
        interactionShield.setBackgroundColor(Color.BLACK);
        // Visual loading layer only. It must never steal mouse/remote input from WebView.
        interactionShield.setClickable(false);
        interactionShield.setFocusable(false);
        interactionShield.setFocusableInTouchMode(false);
        playerFrame.addView(interactionShield, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tab.interactionShield = interactionShield;

        ProgressBar loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        loading.setContentDescription("Загрузка плеера");
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER);
        playerFrame.addView(loading, loadingParams);
        tab.loadingIndicator = loading;

        root.addView(playerFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        tab.root = root;

        // Start navigation only after WebView + shield + spinner are fully attached.
        // Previously loadUrl() ran inside createPlayerWebView(), so fast callbacks could
        // arrive before loading UI existed and leave the shield stuck forever.
        if (record != null) {
            final String initialUrl = playerUrlFor(record);
            web.post(() -> {
                if (tab.webView == web && initialUrl != null && !initialUrl.isEmpty()) {
                    web.loadUrl(initialUrl);
                }
            });
        }
        return root;
    }

    private WebView createPlayerWebView(BrowserTab tab) {
        MovieRecord record = store.get(tab.key);
        WebView web = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? new InputDiagnosticWebView(this, tab)
                : new WebView(this);
        tab.openedAt = SystemClock.elapsedRealtime();
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.setOnFocusChangeListener((v, focused) -> {
            if (VERBOSE_INPUT_LOGS) {
                Log.i("SidiInput", "WEB_FOCUS focused=" + focused + " " + inputSnapshot());
            }
            if (!focused) {
                tab.pointerInsidePlayer = false;
                tab.lastPointerAt = 0L;
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            web.getSettings().setOffscreenPreRaster(false);
        }
        configureWebSettings(web, false);
        web.setInitialScale(0);
        web.addJavascriptInterface(new AdPickerBridge(tab, web), "SidiAdPicker");
        web.addJavascriptInterface(new PlayerChoiceBridge(tab, web), "SidiPlayerChoice");
        installAdPickerFrameSupport(web);
        final boolean legacyPlayer = isLegacyWebView();
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null) return false;
                String next = request.getUrl().toString();
                String current = view == null ? "" : view.getUrl();

                if (!request.isForMainFrame()) return false;
                MovieRecord record = store.get(tab.key);
                if (record != null) {
                    String canonicalPlayerUrl = playerUrlFor(record);
                    if (!isAllowedPlayerMainFrameNavigation(canonicalPlayerUrl, next)) {
                        Log.i("SidiAdBlock", "AD_MAIN_FRAME_BLOCKED gesture=" + request.hasGesture()
                                + " from=" + current + " to=" + next);
                        return true;
                    }
                }
                Log.i("SidiPlayer", "MAIN_FRAME_ALLOWED gesture=" + request.hasGesture()
                        + " from=" + current + " to=" + next);
                adBlocker.allowSessionUrl(next);
                return false;
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                boolean crashed = detail != null && detail.didCrash();
                Log.e("SidiPlayer", "render process gone crashed=" + crashed
                        + " url=" + (view == null ? "" : view.getUrl()));
                // Без обработки этого коллбэка система убивает всё приложение целиком,
                // когда рендер WebView падает по OOM (частый случай на дешёвых приставках
                // во время видео). Пересоздаём WebView вкладки и возвращаем true, чтобы
                // процесс приложения выжил.
                handleRenderProcessGone(tab, view);
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request == null) return null;
                String requestUrl = request.getUrl().toString();
                if (isStraversUrl(requestUrl) && requestUrl.contains("token_movie=")) {
                    Log.i("SidiPlayer", "STRAVERS_DOCUMENT " + requestUrl);
                }
                adBlocker.observeRequest("player", request);
                WebResourceResponse neutralized = adBlocker.interceptKnownVideoAd(request);
                if (neutralized != null) return neutralized;
                if (!tab.playbackResourceSeen
                        && isTrustedPlayerResource(requestUrl)
                        && adBlocker.isPrimaryPlaybackCandidate(requestUrl)) {
                    Log.i("SidiPlayer", "MAIN_MEDIA_FIRST host=" + Uri.parse(requestUrl).getHost());
                    adBlocker.markPrimaryPlaybackSeen();
                    markPlayerReady(tab, view, "resource", requestUrl);
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                tab.playerIsolated = false;
                tab.playerReady = false;
                tab.playbackResourceSeen = false;
                tab.playerIframeSeen = false;
                tab.playerLoadGeneration++;
                tab.playerChoiceRestoredGeneration = -1;
                // Refresh / next play: keep suppress if this tab already played or user marked video ads.
                if (tab.adsSuppressSticky || adBlocker.hasLearnedVideoAdMarks()) {
                    adBlocker.markPrimaryPlaybackSeen();
                    tab.adsSuppressSticky = true;
                    Log.i("SidiAdBlock", "AD_POST_PLAYBACK_SUPPRESS_KEEP reason=page-start-sticky-or-learned");
                } else {
                    adBlocker.resetPlaybackAdState();
                }
                final int loadGeneration = tab.playerLoadGeneration;
                resetPlayerControls(tab);
                if (url != null && !isDirectPlayerPage(url)
                        && !url.startsWith("about:") && !url.startsWith("chrome-error:")) {
                    tab.wrapperUrl = url;
                    if (!tab.directPlayerFallbackUsed) tab.directPlayerPromotionTried = false;
                }
                showPlayerShield(tab, "Загружаем плеер…");
                showPlayerLoading(tab);
                Log.i("SidiPlayer", "page started=" + url);
                if (url != null) {
                    try { view.evaluateJavascript("(function(){try{var ua='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36';function d(k,v){try{Object.defineProperty(navigator,k,{get:function(){return v;},configurable:true});}catch(e){}}d('userAgent',ua);d('platform','Win32');d('maxTouchPoints',0);}catch(e){}})();", null); } catch (Throwable ignored) {}
                }
                if (legacyPlayer) {
                    // Новый Kinobox использует :has(), которого нет в Chromium/WebView 83.
                    // Патчим только известный селектор, не переписывая скрипты/HTML плеера.
                    installLegacyKinoboxCompatibility(view);
                    handler.postDelayed(() -> {
                        if (tab.webView == view && tab.playerLoadGeneration == loadGeneration)
                            installLegacyKinoboxCompatibility(view);
                    }, 120L);
                    handler.postDelayed(() -> {
                        if (tab.webView == view && tab.playerLoadGeneration == loadGeneration)
                            installLegacyKinoboxCompatibility(view);
                    }, 500L);
                }
                // Никогда больше не держим собственный shield бесконечно. Даже если
                // DOM провайдера изменился, через 8 секунд отдаём управление WebView.
                handler.postDelayed(() -> revealPlayerAfterDeadline(tab, view, loadGeneration), 8000L);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                if (url == null || url.startsWith("chrome-error://") || url.startsWith("about:blank")) return;
                // Once Chromium has committed visible content, our overlay must get out
                // of the way even if provider-specific "ready" detection has not fired.
                hidePlayerShield(tab);
                hidePlayerLoading(tab);
                logDesktopBrowserIdentity(view);
                logDesktopBrowserIdentity(view);
                injectPlayerSafeLayout(view);
                restoreSavedPlayerChoice(tab, view);
                applySavedAdRules(view);
                autoHideKnownAdPopups(view, "page-start");
                probePlayerIframe(tab, view);
                Log.i("SidiPlayer", "page commit visible=" + url);
            }

            @Override
            public void onPageFinished(WebView view, String finishedUrl) {
                super.onPageFinished(view, finishedUrl);
                Log.i("SidiPlayer", "page finished=" + finishedUrl);
                if (finishedUrl == null || finishedUrl.startsWith("chrome-error://")
                        || finishedUrl.startsWith("about:blank")) {
                    hidePlayerLoading(tab);
                    return;
                }
                tab.mainFrameRetryCount = 0;
                if (legacyPlayer) installLegacyKinoboxCompatibility(view);
                // No iframe isolation, CSS rewriting or cleanup injection in 12.0.
                // Page content is visible and interactive now. Never keep our own
                // loading layer above it; the provider can show its own loading state.
                hidePlayerShield(tab);
                hidePlayerLoading(tab);
                injectPlayerSafeLayout(view);
                restoreSavedPlayerChoice(tab, view);
                applySavedAdRules(view);
                autoHideKnownAdPopups(view, "page-finished");
                if (adRemovalMode) activateAdPickerRuntime(view, "page-finished");
                probePlayerIframe(tab, view);
                handler.postDelayed(() -> {
                    if (tab.webView == view) {
                        injectPlayerSafeLayout(view);
                        restoreSavedPlayerChoice(tab, view);
                        applySavedAdRules(view);
                        autoHideKnownAdPopups(view, "page-finished-900");
                        if (adRemovalMode) activateAdPickerRuntime(view, "page-finished-900");
                        probePlayerIframe(tab, view);
                    }
                }, 900L);
                handler.postDelayed(() -> {
                    if (tab.webView == view) {
                        autoHideKnownAdPopups(view, "page-finished-2500");
                        if (adRemovalMode) activateAdPickerRuntime(view, "page-finished-2500");
                        restoreSavedPlayerChoice(tab, view);
                    }
                }, 2500L);
                handler.postDelayed(() -> {
                    if (tab.webView == view) autoHideKnownAdPopups(view, "page-finished-5000");
                }, 5000L);
                if (tab.playerReady || isDirectPlayerPage(finishedUrl)) {
                    tab.playerReady = true;
                }
                handler.postDelayed(() -> refreshPlayerControls(tab, false), 800L);
                handler.postDelayed(() -> capturePlayerState(tab), 5000L);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && isStraversUrl(request.getUrl() == null ? "" : request.getUrl().toString())) {
                    Log.e("SidiPlayer", "STRAVERS_NETWORK_ERROR " + request.getUrl());
                }
                if (request != null && request.isForMainFrame()) {
                    int code = error == null ? 0 : error.getErrorCode();
                    String failedUrl = request.getUrl() == null ? "" : request.getUrl().toString();
                    String message = error == null ? "Ошибка загрузки" : String.valueOf(error.getDescription());
                    Log.e("SidiPlayer", "PLAYER_INIT_ERROR main-frame code=" + code + " url=" + failedUrl
                            + " description=" + message);
                    boolean transientError = code == android.webkit.WebViewClient.ERROR_HOST_LOOKUP
                            || code == android.webkit.WebViewClient.ERROR_CONNECT
                            || code == android.webkit.WebViewClient.ERROR_TIMEOUT;
                    if (transientError && tab.mainFrameRetryCount < 2
                            && isSafePlayerRetryUrl(failedUrl)) {
                        tab.mainFrameRetryCount++;
                        int attempt = tab.mainFrameRetryCount;
                        handler.postDelayed(() -> {
                            if (tab.webView == view) {
                                Log.i("SidiPlayer", "retry main frame attempt=" + attempt + " url=" + failedUrl);
                                view.loadUrl(failedUrl);
                            }
                        }, 1200L * attempt);
                        return;
                    }
                    if (isDirectPlayerPage(failedUrl) && fallbackToWrapperPlayer(view, tab,
                            "network-error-" + code)) {
                        return;
                    }
                    hidePlayerLoading(tab);
                    tab.playerReady = false;
                    showPlayerShield(tab, transientError
                            ? "Не удалось подключиться к серверу плеера. Проверьте интернет / DNS и нажмите ↻"
                            : "Плеер не загрузился: " + message + "\nНажмите ↻ для повтора");
                    if (!transientError) {
                        Toast.makeText(MainActivity.this, "Плеер: " + message, Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                super.onReceivedHttpError(view, request, response);
                if (request != null && response != null
                        && isStraversUrl(request.getUrl() == null ? "" : request.getUrl().toString())
                        && response.getStatusCode() >= 400) {
                    Log.w("SidiPlayer", "STRAVERS_HTTP_ERROR " + response.getStatusCode() + " " + request.getUrl());
                }
                if (request != null && request.isForMainFrame() && response != null) {
                    int status = response.getStatusCode();
                    Log.w("SidiPlayer", "main-frame HTTP " + status + " " + request.getUrl());
                    String failedUrl = request.getUrl() == null ? "" : request.getUrl().toString();
                    if (status >= 500 && isDirectPlayerPage(failedUrl)
                            && fallbackToWrapperPlayer(view, tab, "http-" + status)) {
                        return;
                    }
                    // Embed-хосты (kodik, alloha и т.п.) защищают плеер проверкой Referer
                    // и временных токенов. При отказе они отдают 401/403, а не 5xx.
                    // Без этой ветки прямой плеер оставался бы мёртвой страницей, а
                    // directPlayerPromotionTried уже взведён — второго шанса не будет.
                    if ((status == 401 || status == 403) && isDirectPlayerPage(failedUrl)
                            && fallbackToWrapperPlayer(view, tab, "http-" + status)) {
                        return;
                    }
                    // Домены-обёртки Kinobox иногда отвечают HTTP 404, но всё равно отдают
                    // рабочий HTML и затем создают iframe плеера. Не останавливаем загрузку,
                    // не меняем User-Agent и не запускаем повторный переход: это ломало
                    // устройства, где страница фактически продолжала работать после 404.
                    if (status == 404 && isVillybizyUrl(failedUrl)) {
                        Log.i("SidiPlayer", "ignored wrapper 404; waiting for player iframe url=" + failedUrl);
                        return;
                    }
                    // Остальные 404 также не перекрываем собственным экраном ошибки.
                    if (status >= 500) {
                        handler.postDelayed(() -> Toast.makeText(MainActivity.this,
                                "Плеер временно недоступен: HTTP " + status,
                                Toast.LENGTH_LONG).show(), 1500);
                    }
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                Log.i("SidiAdBlock", "POPUP_WINDOW_BLOCKED gesture=" + isUserGesture);
                return false;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    String message = consoleMessage.message() == null ? "" : consoleMessage.message();
                    if (message.contains("video_init_error") || message.contains("Failed to fetch dynamically imported module")) {
                        Log.e("SidiPlayer", "PLAYER_INIT_ERROR console=" + message);
                    }
                    String marker = "[Kinobox] Switch to player:";
                    int markerAt = message.indexOf(marker);
                    if (markerAt >= 0) {
                        String provider = message.substring(markerAt + marker.length()).trim();
                        Log.i("SidiPlayerChoice", "PLAYER_PROVIDER_SWITCH provider=" + provider);
                        if (!provider.isEmpty() && SystemClock.elapsedRealtime() - tab.lastManualPlayerChoiceAt < 4000L) {
                            MovieRecord record = store.get(tab.key);
                            if (record != null) {
                                record.playerLabel = provider;
                                store.update(record);
                                Log.i("SidiPlayerChoice", "PLAYER_PROVIDER_SAVED key=" + tab.key
                                        + " index=" + record.playerIndex + " provider=" + provider);
                            }
                        }
                    }
                }
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                Log.i("SidiPlayer", "onShowCustomView class="
                        + (view == null ? "null" : view.getClass().getName())
                        + " page=" + (web.getUrl() == null ? "" : web.getUrl()));
                enterNativeFullScreen(view, callback, tab);
            }

            @Override
            public void onHideCustomView() {
                Log.i("SidiPlayer", "onHideCustomView");
                exitNativeFullScreenFromWeb();
            }
        });
        return web;
    }

    private void handleRenderProcessGone(BrowserTab tab, WebView deadView) {
        if (tab == null || deadView == null) return;
        // При падении рендера возвращаем обычную компоновку до пересоздания WebView.
        if (embeddedFullScreen) exitEmbeddedFullScreen();

        ViewGroup parent = deadView.getParent() instanceof ViewGroup
                ? (ViewGroup) deadView.getParent() : null;
        boolean wasActiveTab = tab.webView == deadView;
        try {
            deadView.stopLoading();
        } catch (Throwable ignored) {
        }
        if (parent != null) parent.removeView(deadView);
        try {
            deadView.destroy();
        } catch (Throwable ignored) {
        }
        if (!wasActiveTab) {
            // WebView неактивной/закрытой вкладки — просто освобождаем ссылку.
            if (tab.webView == deadView) tab.webView = null;
            return;
        }

        tab.playerIsolated = false;
        tab.playerReady = false;
        tab.playbackResourceSeen = false;
        tab.directPlayerPromotionTried = false;
        tab.directPlayerFallbackUsed = false;
        tab.mainFrameRetryCount = 0;

        WebView fresh = createPlayerWebView(tab);
        tab.webView = fresh;
        if (parent instanceof FrameLayout) {
            // Возвращаем WebView под shield и индикатор загрузки (индекс 0).
            parent.addView(fresh, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (parent != null) {
            parent.addView(fresh);
        }
        showPlayerShield(tab, "Плеер перезапущен после сбоя. Загружаем заново…");
        showPlayerLoading(tab);
        MovieRecord record = store.get(tab.key);
        if (record != null) {
            final String restartUrl = playerUrlFor(record);
            fresh.post(() -> {
                if (tab.webView == fresh && restartUrl != null && !restartUrl.isEmpty()) {
                    fresh.loadUrl(restartUrl);
                }
            });
        }
        if (activeTab == tab) resumeTabWebView(tab);
        toast("Плеер перезапущен после нехватки памяти");
    }

    private boolean isVideoTapZone(View view, float x, float y) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        int bottomControls = Math.max(dp(78), Math.round(view.getHeight() * 0.14f));
        return x >= 0 && y >= 0 && x < view.getWidth() && y < view.getHeight() - bottomControls;
    }

    private boolean isPlaybackResource(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            return host.equals("theatre.stravers.live") || host.endsWith(".stravers.live")
                    || host.equals("vkvideo.cloud") || host.endsWith(".vkvideo.cloud")
                    || host.contains("alloha") || host.contains("kodik")
                    || host.contains("bazon") || host.contains("collaps")
                    || host.contains("videocdn") || host.contains("video-cdn");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void markPlayerReady(BrowserTab tab, WebView web, String source, String detail) {
        if (tab == null || web == null) return;
        handler.post(() -> {
            if (tab.webView != web) return;
            boolean first = !tab.playerReady;
            tab.playbackResourceSeen = true;
            tab.playerReady = true;
            tab.adsSuppressSticky = true;
            tab.playerReadyUrl = detail == null ? "" : detail;
            if (adBlocker != null) adBlocker.markPrimaryPlaybackSeen();
            hidePlayerShield(tab);
            hidePlayerLoading(tab);
            if (first) {
                Log.i("SidiPlayer", "PLAYER_READY source=" + source + " detail=" + tab.playerReadyUrl);
            }
            autoHideKnownAdPopups(web, first ? "player-ready" : "player-ready-again");
            handler.postDelayed(() -> {
                if (tab.webView == web) autoHideKnownAdPopups(web, "player-ready-delayed");
            }, 1500L);
            if (adRemovalMode) activateAdPickerRuntime(web, "player-ready");
        });
    }

    private void revealPlayerAfterDeadline(BrowserTab tab, WebView web, int generation) {
        if (tab == null || web == null || tab.webView != web
                || tab.playerLoadGeneration != generation || tab.playerReady) return;
        String url = web.getUrl() == null ? "" : web.getUrl();
        if (url.startsWith("chrome-error://") || url.startsWith("about:blank")) return;
        tab.playerReady = true;
        hidePlayerShield(tab);
        hidePlayerLoading(tab);
        Log.w("SidiPlayer", "player readiness deadline reached; revealing WebView url=" + url);
    }

    /**
     * Chromium 83 не понимает CSS4 selector :has() в querySelector(). Текущий
     * Kinobox вызывает ровно один такой селектор. Подменяем только этот случай;
     * остальные selector API остаются нативными. Это не меняет HTML/JS Stravers
     * и не затрагивает SRI-защищённые ресурсы.
     */
    private void installLegacyKinoboxCompatibility(WebView web) {
        if (web == null || !isLegacyWebView()) return;
        String js = "(function(){try{" +
                "if(window.__sidiHasCompat)return 'already';" +
                "var D=window.Document&&Document.prototype,E=window.Element&&Element.prototype;" +
                "if(!D||!D.querySelector||!D.querySelectorAll)return 'no-api';" +
                "var dqs=D.querySelector,dqsa=D.querySelectorAll;" +
                "var eqs=E&&E.querySelector,eqsa=E&&E.querySelectorAll;" +
                "var target='.kinobox__menuBody:has(.kinobox__menuItem)';" +
                "function first(root,all){var b=all.call(root,'.kinobox__menuBody');" +
                "for(var i=0;i<b.length;i++){try{if(b[i].querySelector('.kinobox__menuItem'))return b[i];}catch(x){}}return null;}" +
                "D.querySelector=function(s){if(s===target)return first(this,dqsa);return dqs.call(this,s);};" +
                "if(E&&eqs&&eqsa)E.querySelector=function(s){if(s===target)return first(this,eqsa);return eqs.call(this,s);};" +
                "window.__sidiHasCompat=true;return 'installed';" +
                "}catch(e){return 'error:'+String(e&&e.message||e);}})();";
        try {
            web.evaluateJavascript(js, result -> {
                if (result != null && result.contains("installed"))
                    Log.i("SidiPlayer", "legacy Kinobox :has compatibility installed");
            });
        } catch (Throwable error) {
            Log.w("SidiPlayer", "legacy Kinobox compatibility failed", error);
        }
    }

    private boolean isTrustedPlayerResource(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            return host.equals("theatre.stravers.live") || host.endsWith(".stravers.live")
                    || host.equals("fbphdplay.top") || host.endsWith(".fbphdplay.top")
                    || host.equals("vkvideo.cloud") || host.endsWith(".vkvideo.cloud")
                    || host.contains("alloha") || host.contains("kodik")
                    || host.contains("bazon") || host.contains("collaps")
                    || host.contains("videocdn") || host.contains("video-cdn");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isAllowedPlayerMainFrameNavigation(String currentUrl, String nextUrl) {
        try {
            Uri current = Uri.parse(currentUrl == null ? "" : currentUrl);
            Uri next = Uri.parse(nextUrl == null ? "" : nextUrl);
            String currentHost = current.getHost() == null ? "" : current.getHost().toLowerCase(Locale.US);
            String nextHost = next.getHost() == null ? "" : next.getHost().toLowerCase(Locale.US);
            if (nextHost.isEmpty()) return false;
            if (nextHost.equals(currentHost)) return true;
            if (playerUrlRules != null && playerUrlRules.isKnownMirrorHost(nextHost)) return true;
            // Актуальная обёртка Kinobox/SSPoisk (2026-09). Это рабочий player host,
            // в отличие от рекламных redirect-доменов beebounder/gorgeous/melbet.
            if (nextHost.startsWith("bulkikim.")) return true;
            if (nextHost.equals("villybizy.best") || nextHost.endsWith(".villybizy.best")
                    || nextHost.equals("villybizy.skin") || nextHost.endsWith(".villybizy.skin")
                    || nextHost.startsWith("villybizy.")) return true;
            if (nextHost.equals("fbphdplay.top") || nextHost.endsWith(".fbphdplay.top")) return true;
            if (nextHost.equals("stravers.live") || nextHost.endsWith(".stravers.live")) return true;
            if (nextHost.equals("vkvideo.cloud") || nextHost.endsWith(".vkvideo.cloud")) return true;
            if (nextHost.contains("alloha") || nextHost.contains("kodik")
                    || nextHost.contains("bazon") || nextHost.contains("collaps")
                    || nextHost.contains("videocdn") || nextHost.contains("video-cdn")) return true;
            // Неизвестный домен разрешаем по эвристике только через HTTPS.
            // Это сохраняет совместимость с новыми зеркалами, но не даёт
            // рекламному popup увести WebView на произвольный cleartext-сайт.
            String scheme = next.getScheme() == null ? "" : next.getScheme().toLowerCase(Locale.US);
            if (!"https".equals(scheme)) return false;

            // SSPoisk/Kinobox регулярно меняет зеркало wrapper-домена. Если redirect
            // сохраняет тот же /film/<id>/ или /series/<id>/, это продолжение той же
            // страницы фильма, а не рекламный уход. Не привязываемся к конкретному TLD.
            String currentId = extractNumericId(currentUrl);
            String nextId = extractNumericId(nextUrl);
            if (!currentId.isEmpty() && currentId.equals(nextId)) {
                String currentPath = current.getPath() == null ? "" : current.getPath();
                String nextPath = next.getPath() == null ? "" : next.getPath();
                boolean currentMoviePath = currentPath.matches("/(film|series)/\\d+/?");
                boolean nextMoviePath = nextPath.matches("/(film|series)/\\d+/?");
                if (currentMoviePath && nextMoviePath) return true;
            }

            String lower = nextUrl == null ? "" : nextUrl.toLowerCase(Locale.US);
            return lower.contains("/player") || lower.contains("/embed")
                    || lower.contains("iframe") || lower.contains("alloha")
                    || lower.contains("kodik") || lower.contains("bazon")
                    || lower.contains("collaps") || lower.contains("videocdn")
                    || lower.contains("video-cdn") || lower.contains("stream");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isDirectPlayerPage(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.US);
            if (host.equals("theatre.stravers.live") || host.endsWith(".stravers.live")) return true;
            if (host.equals("vkvideo.cloud") || host.endsWith(".vkvideo.cloud")) return true;
            return host.contains("alloha") || host.contains("kodik") || host.contains("bazon")
                    || host.contains("collaps") || host.contains("videocdn")
                    || host.contains("video-cdn");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void prepareDirectPlayerPage(WebView web) {
        if (web == null) return;
        String js = "(function(){try{" +
                "var st=document.getElementById('__sidi_direct_style');" +
                "if(!st){st=document.createElement('style');st.id='__sidi_direct_style';" +
                "st.textContent='html,body{margin:0!important;padding:0!important;width:100%!important;height:100%!important;background:#000!important;overflow:hidden!important}' +" +
                "'video,iframe{max-width:none!important;max-height:none!important}html,body,video,*{cursor:default!important}';" +
                "(document.head||document.documentElement).appendChild(st);}" +
                "var v=document.querySelector('video');if(v){v.style.setProperty('width','100vw','important');" +
                "v.style.setProperty('height','100vh','important');v.style.setProperty('object-fit','contain','important');}" +
                "return 'direct-ready:'+(v?'video':'page');" +
                "}catch(e){return 'direct-error:'+String(e&&e.message||e);}})();";
        try {
            web.evaluateJavascript(js, result -> Log.i("SidiPlayer", "direct layout=" + result));
            handler.postDelayed(() -> {
                try { web.evaluateJavascript(js, null); } catch (Throwable ignored) {}
            }, 1500);
        } catch (Throwable error) {
            Log.w("SidiPlayer", "direct player layout failed", error);
        }
    }

    private void startPlayerIsolation(WebView web, BrowserTab tab, int attempt) {
        if (web == null || tab == null || tab.webView != web || tab.playerIsolated) return;
        String js = "(function(){try{" +
                "var bad=/(doubleclick|adservice|adsterra|exoclick|popads|onclick|banner|advert|vast|vpaid)/i;" +
                "var fs=document.querySelectorAll('iframe[src]'),f=null,best=-1,bestSrc='';" +
                "for(var i=0;i<fs.length;i++){var x=fs[i],src=x.src||x.getAttribute('src')||'';if(!src||bad.test(src))continue;" +
                "var r=x.getBoundingClientRect(),score=0;" +
                "if(/theatre\\.stravers\\.live/i.test(src))score=120;else if(/stravers\\.live/i.test(src))score=110;" +
                "else if(/alloha/i.test(src))score=100;else if(/kodik/i.test(src))score=90;else if(/bazon/i.test(src))score=85;" +
                "else if(/collaps/i.test(src))score=80;else if(/videocdn|video-cdn/i.test(src))score=75;" +
                "else if(/\\/(embed|player)(?:[\\/?#]|$)/i.test(src))score=45;" +
                "if(r.width>=480&&r.height>=250)score+=20;else if(r.width>=300&&r.height>=160)score+=8;" +
                "if(score>best){best=score;f=x;bestSrc=src;}}" +
                "if(!f||best<70)return 'waiting:'+fs.length+':'+best+':'+bestSrc;" +
                "var n=f;while(n&&n!==document.documentElement){" +
                "var p=n.parentElement;if(p){for(var j=0;j<p.children.length;j++){var c=p.children[j];" +
                "if(c!==n)c.style.setProperty('display','none','important');}}" +
                "n.style.setProperty('display','block','important');" +
                "n.style.setProperty('visibility','visible','important');" +
                "n.style.setProperty('opacity','1','important');" +
                "n.style.setProperty('transform','none','important');" +
                "n.style.setProperty('filter','none','important');" +
                "n.style.setProperty('overflow','visible','important');n=p;}" +
                "var h=document.documentElement,b=document.body;" +
                "var cs=document.getElementById('__sidi_cursor_style');if(!cs){cs=document.createElement('style');" +
                "cs.id='__sidi_cursor_style';cs.textContent='html,body,iframe,*{cursor:default!important}';" +
                "(document.head||document.documentElement).appendChild(cs);}" +
                "[h,b].forEach(function(e){if(!e)return;e.style.setProperty('margin','0','important');" +
                "e.style.setProperty('padding','0','important');e.style.setProperty('width','100%','important');" +
                "e.style.setProperty('height','100%','important');e.style.setProperty('overflow','hidden','important');" +
                "e.style.setProperty('background','#000','important');});" +
                "f.style.setProperty('position','fixed','important');f.style.setProperty('inset','0','important');" +
                "f.style.setProperty('left','0','important');f.style.setProperty('top','0','important');" +
                "f.style.setProperty('width','100vw','important');f.style.setProperty('height','100vh','important');" +
                "f.style.setProperty('min-width','100vw','important');f.style.setProperty('min-height','100vh','important');" +
                "f.style.setProperty('max-width','none','important');f.style.setProperty('max-height','none','important');" +
                "f.style.setProperty('border','0','important');f.style.setProperty('z-index','2147483647','important');" +
                "f.style.setProperty('pointer-events','auto','important');f.setAttribute('allowfullscreen','');" +
                "f.setAttribute('allow','autoplay; fullscreen; picture-in-picture');" +
                "window.__sidiPlayerIsolated=true;return 'isolated:'+best+':'+String(f.src||'');" +
                "}catch(e){return 'error:'+String(e&&e.message||e);}})();";
        try {
            web.evaluateJavascript(js, value -> {
                if (tab.webView != web || tab.playerIsolated) return;
                String result = value == null ? "" : value;
                if (result.contains("isolated:")) {
                    String playerUrl = extractIsolatedPlayerUrl(result);
                    Log.i("SidiPlayer", "player iframe isolated result=" + result);
                    if (shouldPromotePlayerFrame() && !tab.directPlayerPromotionTried
                            && isDirectPlayerPage(playerUrl)) {
                        tab.directPlayerPromotionTried = true;
                        tab.directPlayerUrl = playerUrl;
                        promotePlayerToMainFrame(web, tab, playerUrl);
                        return;
                    }
                    tab.playerIsolated = true;
                    hidePlayerShield(tab);
                    hidePlayerLoading(tab);
                    tab.playerReady = true;
                    handler.postDelayed(() -> refreshPlayerControls(tab, true), 250L);
                    handler.postDelayed(() -> refreshPlayerControls(tab, false), 1200L);
                    return;
                }
                if (result.contains("error:")) {
                    Log.w("SidiPlayer", "player isolation JS " + result);
                } else if (attempt % 20 == 0) {
                    Log.i("SidiPlayer", "player isolation wait attempt=" + attempt + " result=" + result);
                }
                int maxAttempts = tab.playerReady ? 18 : 90;
                if (attempt < maxAttempts) {
                    handler.postDelayed(() -> startPlayerIsolation(web, tab, attempt + 1), 800);
                } else {
                    // Если playback-ресурс уже работает, отсутствие доступного iframe
                    // не является ошибкой: provider мог спрятать его в закрытом shadow DOM.
                    Log.i("SidiPlayer", "player iframe isolation finished ready=" + tab.playerReady
                            + " url=" + web.getUrl());
                    hidePlayerShield(tab);
                    hidePlayerLoading(tab);
                    if (!tab.playerReady) {
                        Toast.makeText(MainActivity.this,
                                "Не удалось автоматически выделить плеер", Toast.LENGTH_LONG).show();
                    }
                }
            });
        } catch (Throwable error) {
            Log.e("SidiPlayer", "cannot run player isolation", error);
            int maxAttempts = tab.playerReady ? 18 : 90;
            if (attempt < maxAttempts) {
                handler.postDelayed(() -> startPlayerIsolation(web, tab, attempt + 1), 800);
            }
        }
    }

    private String extractIsolatedPlayerUrl(String result) {
        if (result == null) return "";
        String value = result.trim();
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            try {
                value = new JSONTokener(value).nextValue().toString();
            } catch (Throwable ignored) {
                value = value.substring(1, value.length() - 1);
            }
        }
        int marker = value.indexOf("isolated:");
        if (marker < 0) return "";
        int scoreEnd = value.indexOf(':', marker + "isolated:".length());
        if (scoreEnd < 0 || scoreEnd + 1 >= value.length()) return "";
        return value.substring(scoreEnd + 1).trim();
    }

    private void promotePlayerToMainFrame(WebView web, BrowserTab tab, String playerUrl) {
        if (web == null || tab == null || playerUrl == null || playerUrl.isEmpty()) return;
        String referer = tab.wrapperUrl == null || tab.wrapperUrl.isEmpty()
                ? web.getUrl() : tab.wrapperUrl;
        Map<String, String> headers = new LinkedHashMap<>();
        if (referer != null && !referer.isEmpty()) headers.put("Referer", referer);
        Log.i("SidiPlayer", "promoting player iframe to main frame url=" + playerUrl
                + " referer=" + referer);
        showPlayerShield(tab, "Открываем плеер…");
        showPlayerLoading(tab);
        adBlocker.allowSessionUrl(playerUrl);
        handler.post(() -> {
            if (tab.webView != web) return;
            try {
                web.loadUrl(playerUrl, headers);
            } catch (Throwable error) {
                Log.e("SidiPlayer", "direct player promotion failed", error);
                fallbackToWrapperPlayer(web, tab, "promotion-exception");
            }
        });
    }

    private boolean fallbackToWrapperPlayer(WebView web, BrowserTab tab, String reason) {
        if (web == null || tab == null || tab.directPlayerFallbackUsed) return false;
        String wrapper = tab.wrapperUrl == null ? "" : tab.wrapperUrl.trim();
        if (wrapper.isEmpty() || isDirectPlayerPage(wrapper)) return false;
        tab.directPlayerFallbackUsed = true;
        tab.directPlayerPromotionTried = true;
        tab.playerIsolated = false;
        Log.w("SidiPlayer", "direct player fallback to wrapper reason=" + reason
                + " wrapper=" + wrapper);
        showPlayerShield(tab, "Возвращаем совместимый режим…");
        showPlayerLoading(tab);
        handler.postDelayed(() -> {
            if (tab.webView == web) web.loadUrl(wrapper);
        }, 350L);
        return true;
    }

    private void showPlayerShield(BrowserTab tab, String message) {
        if (tab == null || tab.interactionShield == null) return;
        tab.interactionShield.setText(message == null ? "" : message);
        tab.interactionShield.setVisibility(View.VISIBLE);
        tab.interactionShield.bringToFront();
        if (tab.loadingIndicator != null) tab.loadingIndicator.bringToFront();
    }

    private void hidePlayerShield(BrowserTab tab) {
        if (tab != null && tab.interactionShield != null) {
            tab.interactionShield.setVisibility(View.GONE);
        }
    }

    private void installAdPickerFrameSupport(WebView web) {
        if (web == null) return;
        final String js = "(function(){try{" +
                "if(window.__sidiFramePicker1317)return;window.__sidiFramePicker1317=true;" +
                "function playerUrl(u){u=String(u||'').toLowerCase();return /stravers\\.live|bulkikim|villybizy|alloha|kodik|voidboost|videocdn|collaps|hdvb|nextembed\\.ws|shortener2\\.link|videotoday\\.site|obrut\\.show/.test(u);}" +
                "function isAdFrameHost(){try{var h=String(location.hostname||'').toLowerCase();if(playerUrl(location.href||''))return false;return /moviead55|buzzoola|traffaret|adriver|video-mech|betweendigital|vqserve|ussp\\.io|showjet|skcrtxr|adlook|bidster|aj2635|prestreamnos|moe\\.video/.test(h);}catch(x){return false;}}" +
                "function known(e){try{return e&&e.closest?e.closest('#movie_video,#tgWrapper,#TopAdMb,.adDown,.topAdPad,.adlook-pc-wrapper,.adlook-mob-wrapper,.adlk-sticky,.adlk-content,.adlk-player-host,.rmp-vpaid-container,.rmp-ad-container,.rmp-ad-overlay,.rmp-vast-container,[id^=ad-element],[class^=rb-adman-],iframe[src*=\\\"traffaret\\\"],iframe[src*=\\\"adriver\\\"],iframe[src*=\\\"moviead55\\\"],iframe[src*=\\\"buzzoola\\\"],iframe[src*=\\\"video-mech\\\"],iframe[src*=\\\"betweendigital\\\"],iframe[src*=\\\"go/bn\\\"],[class*=\\\"popup\\\"],[class*=\\\"banner\\\"],[class*=\\\"advert\\\"],[class*=\\\"preroll\\\"],[class*=\\\"vpaid\\\"],[class*=\\\"vast\\\"]'):null;}catch(x){return null;}}" +
                /* 13.18: ad iframes (moviead55/…) — not Turbo/obrut.player embeds */
                "function protectedNode(e){if(!e)return true;if(known(e))return false;var t=(e.tagName||'').toLowerCase();if(t==='html'||t==='body')return true;if((t==='video'||t==='audio')&&!isAdFrameHost())return true;if(t==='iframe')return playerUrl(e.src||e.getAttribute('src')||'');try{if(!isAdFrameHost()&&e.closest('video,audio'))return true;}catch(x){}return false;}" +
                "function adFramePickTarget(raw){try{var k=known(raw);if(k)return overlayRoot(k);var n=raw,best=null;while(n&&n!==document.documentElement){var t=(n.tagName||'').toLowerCase();if(t&&t!=='video'&&t!=='audio'&&t!=='source'&&t!=='html'){try{var r=n.getBoundingClientRect();if(r&&r.width>40&&r.height>40){best=n;if(t==='body')break;}}catch(z){}}n=n.parentElement;}if(best){var o=overlayRoot(best);if(o&&String(o.tagName||'').toLowerCase()!=='html')return o;if(String(best.tagName||'').toLowerCase()!=='html')return best;}var kids=document.body?document.body.children:[];for(var i=0;i<kids.length;i++){try{var rr=kids[i].getBoundingClientRect();if(rr.width>80&&rr.height>80)return kids[i];}catch(z){}}return null;}catch(x){return null;}}" +
                "function externalLink(e){try{var a=e.closest&&e.closest('a[href]');if(!a&&e.querySelector)a=e.querySelector('a[href]');if(!a)return null;var u=new URL(a.getAttribute('href')||a.href||'',location.href);if(!/^https?:$/.test(u.protocol)||u.hostname===location.hostname)return null;if(playerUrl(u.href))return null;return a;}catch(x){return null;}}" +
                "function overlayRoot(e){var link=externalLink(e),n=e,best=e,screen=Math.max(1,innerWidth*innerHeight),i=0;while(n&&n!==document.body&&i<10){if(n!==e&&protectedNode(n))break;try{var c=getComputedStyle(n),r=n.getBoundingClientRect(),a=r.width*r.height,cls=String((n.id||'')+' '+(n.className||'')).toLowerCase(),close=!!(n.querySelector&&n.querySelector('[class*=close],[class*=cross],[aria-label*=close],[aria-label*=закры],button'));var adish=/ad|advert|banner|promo|popup|preroll|vast|vpaid|allplay/.test(cls);if(r.width>24&&r.height>24&&a<screen*.92){if((adish||link||close)&&(c.position==='fixed'||c.position==='sticky'||c.position==='absolute'||a>screen*.08))best=n;}}catch(x){}n=n.parentElement;i++;}return best;}" +
                "function choose(e){if(!e)return null;var k=known(e);if(k)return overlayRoot(k);if(protectedNode(e))return null;var ext=externalLink(e);if(ext)return overlayRoot(e);var n=e,best=e,screen=Math.max(1,innerWidth*innerHeight),i=0;while(n&&n!==document.body&&i<8){if(n!==e&&protectedNode(n))break;try{var c=getComputedStyle(n),r=n.getBoundingClientRect(),a=r.width*r.height;if(r.width>18&&r.height>18&&a<screen*.78){if((c.position==='fixed'||c.position==='sticky')&&a>1200)return n;best=n;}}catch(x){}n=n.parentElement;i++;}return best;}" +
                "function esc(v){try{return CSS.escape(v);}catch(x){return String(v).replace(/[^a-zA-Z0-9_-]/g,function(c){return '\\\\'+c;});}}" +
                "function attr(n,v){return '['+n+'=\\\"'+String(v).replace(/\\\\/g,'\\\\\\\\').replace(/\\\"/g,'\\\\\\\"')+'\\\"]';}" +
                "function stableKnownSelector(e){try{if(!e)return '';if(e.id==='movie_video'||e.id==='tgWrapper'||e.id==='TopAdMb')return '#'+esc(e.id);if(e.matches('.topAdPad'))return 'div.topAdPad';if(e.matches('.rmp-vpaid-container'))return '.rmp-vpaid-container';if(e.matches('.rmp-ad-container'))return '.rmp-ad-container';if(e.matches('.rmp-ad-overlay'))return '.rmp-ad-overlay';if(e.matches('.rmp-vast-container'))return '.rmp-vast-container';var cls=[];try{Array.prototype.forEach.call(e.classList,function(c){if(cls.length<2&&c.length<60&&/^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(c)&&/ad|advert|banner|promo|popup|overlay|preroll|vast|vpaid|sponsor|allplay|creative/i.test(c))cls.push(c);});}catch(z){}if(cls.length)return e.tagName.toLowerCase()+'.'+cls.map(esc).join('.');var id=e.id||'';var m=id.match(/^([A-Za-z]{4,8})[0-9]{12,}$/);if(m)return '[id^=\"'+esc(m[1])+'\"]';}catch(x){}return '';}" +
                "function selector(e){var stable=stableKnownSelector(e);if(stable)return stable;if(e.id&&e.id.length<100)return '#'+esc(e.id);if((e.tagName||'').toLowerCase()==='iframe'&&e.getAttribute('src')){var q='iframe'+attr('src',e.getAttribute('src'));try{if(document.querySelectorAll(q).length<=2)return q;}catch(x){}}var attrs=['data-testid','data-id','data-ad','aria-label','title'];for(var ai=0;ai<attrs.length;ai++){var av=e.getAttribute&&e.getAttribute(attrs[ai]);if(av&&av.length<120){var q=e.tagName.toLowerCase()+attr(attrs[ai],av);try{if(document.querySelectorAll(q).length<=4)return q;}catch(x){}}}var parts=[],n=e,d=0;while(n&&n.nodeType===1&&d<7&&n!==document.body){var part=n.tagName.toLowerCase(),cs=[];try{Array.prototype.forEach.call(n.classList,function(c){if(cs.length<3&&c.length<40&&/^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(c)&&!/[0-9a-f]{8,}/i.test(c))cs.push(c);});}catch(x){}if(cs.length)part+='.'+cs.map(esc).join('.');else if(n.parentElement){var same=Array.prototype.filter.call(n.parentElement.children,function(x){return x.tagName===n.tagName;});if(same.length>1)part+=':nth-of-type('+(same.indexOf(n)+1)+')';}parts.unshift(part);try{if(document.querySelectorAll(parts.join(' > ')).length===1)break;}catch(x){}n=n.parentElement;d++;}return parts.join(' > ');}" +
                "function hrefFor(e){try{if((e.tagName||'').toLowerCase()==='iframe')return new URL(e.getAttribute('src')||e.src||'',location.href).href;var a=e.tagName==='A'?e:e.querySelector('a[href]');return a?new URL(a.getAttribute('href')||a.href||'',location.href).href:'';}catch(x){return '';}}" +
                "function norm(v){return String(v||'').replace(/\\s+/g,' ').trim();}" +
                "function hide(e,id){try{if(!e||protectedNode(e))return false;e.setAttribute('data-sidi-manual-hidden','1');if(id)e.setAttribute('data-sidi-manual-rule',id);e.style.setProperty('display','none','important');e.style.setProperty('visibility','hidden','important');e.style.setProperty('pointer-events','none','important');return true;}catch(x){return false;}}" +
                "function hrefOf(a){try{return new URL(a.getAttribute('href')||a.href||'',location.href).href;}catch(x){return a.href||'';}}" +
                "function fallbackRoot(e){var k=known(e);if(k)return overlayRoot(k);return overlayRoot(e);}" +
                "function applyRules(){try{var raw=SidiAdPicker.rulesForHost(location.hostname||'');if(!raw)return 0;var rules=JSON.parse(raw),st=document.getElementById('__sidi_manual_css_1310');if(!st){st=document.createElement('style');st.id='__sidi_manual_css_1310';(document.head||document.documentElement).appendChild(st);}var total=0;rules.forEach(function(r){try{if(r.selector)st.sheet.insertRule(r.selector+'{display:none!important;visibility:hidden!important;pointer-events:none!important}',st.sheet.cssRules.length);}catch(x){}var hit=0;try{document.querySelectorAll(r.selector).forEach(function(e){if(hide(e,r.id)){hit++;total++;}});}catch(x){}if(!hit&&r.href){try{document.querySelectorAll('a[href]').forEach(function(a){if(hrefOf(a)===r.href&&hide(fallbackRoot(a),r.id)){hit++;total++;}});}catch(x){}}if(!hit&&r.text&&r.text.length>=4){try{var tags=r.tag?document.getElementsByTagName(r.tag):document.querySelectorAll('a,button,div,span');for(var i=0;i<tags.length&&i<2500;i++){var e=tags[i];if(norm(e.innerText)===r.text&&hide(fallbackRoot(e),r.id)){hit++;total++;break;}}}catch(x){}}});if(rules.length&&!window.__sidiManualRuleObserver1310){window.__sidiManualRuleObserver1310=true;var pending=false;new MutationObserver(function(){if(pending)return;pending=true;setTimeout(function(){pending=false;applyRules();},120);}).observe(document.documentElement||document,{childList:true,subtree:true});}return total;}catch(x){return 0;}}" +
                "function active(){try{return !!SidiAdPicker.isActive();}catch(x){return false;}}" +
                "var box=null;function ensureBox(){if(box&&box.isConnected)return box;box=document.createElement('div');box.style.cssText='position:fixed;display:none;pointer-events:none;z-index:2147483647;border:4px solid #ffcc00;background:rgba(255,204,0,.08);box-sizing:border-box';(document.documentElement||document.body).appendChild(box);return box;}" +
                "function show(e){var b=ensureBox();if(!e){b.style.display='none';return;}try{var r=e.getBoundingClientRect();b.style.left=r.left+'px';b.style.top=r.top+'px';b.style.width=r.width+'px';b.style.height=r.height+'px';b.style.display='block';}catch(x){b.style.display='none';}}" +
                "function protectedPlayerFrameTarget(t){try{return t&&String(t.tagName||'').toLowerCase()==='iframe'&&playerUrl(t.src||t.getAttribute('src')||'');}catch(x){return false;}}" +
                "function visualAt(x,y,raw){try{var root=document.body||document.documentElement,all=root?root.querySelectorAll('*'):[],best=null,bestScore=-1e9,screen=Math.max(1,innerWidth*innerHeight),lim=Math.min(all.length,5000);for(var i=0;i<lim;i++){var e=all[i];if(!e||e===box)continue;var tag=String(e.tagName||'').toLowerCase();if(!tag||/^(html|body|script|style|meta|link|video|audio|source)$/.test(tag))continue;var r;try{r=e.getBoundingClientRect();}catch(z){continue;}if(!r||r.width<18||r.height<18||x<r.left||x>r.right||y<r.top||y>r.bottom)continue;var c;try{c=getComputedStyle(e);}catch(z){continue;}if(!c||c.display==='none'||c.visibility==='hidden'||parseFloat(c.opacity||'1')<.02)continue;var area=r.width*r.height;if(area>screen*.96)continue;var meta=String((e.id||'')+' '+(e.className||'')+' '+((e.getAttribute&&e.getAttribute('role'))||'')).toLowerCase();var adish=/ad|advert|banner|promo|popup|overlay|preroll|vast|vpaid|sponsor|allplay|creative/.test(meta);var k=known(e),ext=externalLink(e),pn=c.pointerEvents==='none',pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';var hasVideo=false;try{hasVideo=!!(e.querySelector&&e.querySelector('video,audio'));}catch(z){}if(hasVideo&&!k&&!adish&&!ext)continue;if(protectedNode(e)&&!k&&!adish&&!ext&&!pn)continue;var zi=parseInt(c.zIndex,10);if(!isFinite(zi))zi=0;var close=false;try{close=!!(e.querySelector&&e.querySelector('[class*=close],[class*=cross],[aria-label*=close],[aria-label*=закры],button'));}catch(z){}var score=0;if(k)score+=8000;if(adish)score+=5200;if(ext)score+=3800;if(pn)score+=2600;if(pos)score+=1400;if(close)score+=700;score+=Math.min(1400,Math.max(0,zi)/4);var frac=area/screen;if(frac>.025&&frac<.82)score+=900-Math.abs(frac-.30)*700;if(raw&&e===raw)score-=1200;if(raw&&e.contains&&e.contains(raw))score+=120;if(score>bestScore){best=e;bestScore=score;}}if(best){var rr=overlayRoot(best);if(rr&&!protectedNode(rr))return rr;return best;}return choose(raw);}catch(z){return choose(raw);}}" +
                "function guard(ev){if(!active())return;if(protectedPlayerFrameTarget(ev.target))return;ev.preventDefault();ev.stopPropagation();if(ev.stopImmediatePropagation)ev.stopImmediatePropagation();}" +
                "document.addEventListener('pointerdown',guard,true);document.addEventListener('mousedown',guard,true);document.addEventListener('contextmenu',guard,true);" +
                "var __sidiVisualAt=0,__sidiVisualEl=null;document.addEventListener('mousemove',function(ev){if(!active()){if(box)box.style.display='none';return;}var now=Date.now();if(now-__sidiVisualAt>180){__sidiVisualAt=now;__sidiVisualEl=visualAt(ev.clientX,ev.clientY,ev.target);}show(__sidiVisualEl||choose(ev.target));},true);" +
                "document.addEventListener('click',function(ev){if(!active())return;var raw=ev.target;ev.preventDefault();ev.stopPropagation();if(ev.stopImmediatePropagation)ev.stopImmediatePropagation();var e=visualAt(ev.clientX,ev.clientY,raw);if(isAdFrameHost()){try{SidiAdPicker.candidate('ad-frame-block host='+(location.hostname||'')+' tag='+String((raw&&raw.tagName)||'')+' visual='+String((e&&e.tagName)||''));}catch(x){}try{SidiAdPicker.blockAdFrame(location.hostname||'');}catch(x){}if(box)box.style.display='none';return false;}if(!e){if(protectedPlayerFrameTarget(raw)){try{SidiAdPicker.deferred('player-frame:'+location.hostname);}catch(x){}}else{try{SidiAdPicker.rejected('no-visual-ad:'+location.hostname+':'+String((raw&&raw.tagName)||''));}catch(x){}}if(box)box.style.display='none';return false;}show(e);var sel=selector(e);if(!sel)return false;var token='f'+Date.now().toString(36)+Math.random().toString(36).slice(2);e.setAttribute('data-sidi-picker-token',token);var txt=String(e.innerText||e.getAttribute('title')||e.getAttribute('aria-label')||'').replace(/\s+/g,' ').trim().slice(0,160);try{SidiAdPicker.candidate((location.hostname||'')+' selector='+sel+' tag='+(e.tagName||'')+' pe='+(getComputedStyle(e).pointerEvents||''));}catch(x){}try{SidiAdPicker.pick(location.hostname||'',sel,token,e.tagName||'',hrefFor(e),txt);}catch(x){}var tries=0,t=setInterval(function(){tries++;var d=0;try{d=SidiAdPicker.decision(token);}catch(x){}if(d===1){clearInterval(t);try{var st=document.getElementById('__sidi_manual_css_1310');if(!st){st=document.createElement('style');st.id='__sidi_manual_css_1310';(document.head||document.documentElement).appendChild(st);}st.sheet.insertRule(sel+'{display:none!important;visibility:hidden!important;pointer-events:none!important}',st.sheet.cssRules.length);}catch(x){}hide(e,'approved');applyRules();if(box)box.style.display='none';try{e.removeAttribute('data-sidi-picker-token');}catch(x){}}else if(d===-1||tries>300){clearInterval(t);try{e.removeAttribute('data-sidi-picker-token');}catch(x){}if(box)box.style.display='none';}},100);return false;},true);" +
                "if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',applyRules,{once:true});else applyRules();setTimeout(applyRules,400);setTimeout(applyRules,1200);setTimeout(applyRules,2800);" +
                "}catch(e){}})();";
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(web, js, Collections.singleton("*"));
                Log.i("SidiAdPicker", "FRAME_PICKER_DOCUMENT_START enabled");
            } else {
                Log.w("SidiAdPicker", "FRAME_PICKER_DOCUMENT_START unavailable; top-frame fallback only");
            }
        } catch (Throwable error) {
            Log.w("SidiAdPicker", "cannot install frame picker", error);
        }
    }

    private void configureWebSettings(WebView web, boolean searchOnly) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadsImagesAutomatically(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        // Поиск работает только по HTTPS. Совместимость со смешанным контентом
        // оставлена исключительно плееру, где встречаются старые HTTP-источники.
        s.setMixedContentMode(searchOnly
                ? WebSettings.MIXED_CONTENT_NEVER_ALLOW
                : WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(!searchOnly);
        if (!searchOnly) {
            s.setUserAgentString(DESKTOP_WINDOWS_UA);
        }
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Player WebView получает desktop UA вместе с wide viewport: Kinobox иначе прячет
        // selector источников и отдаёт TV/mobile layout даже на Full HD экране.
    }

    private void installDesktopBrowserIdentity(WebView web) {
        if (web == null) return;
        final String js = "(function(){try{" +
                "var ua='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/153.0.0.0 Safari/537.36';" +
                "function d(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v;},configurable:true});return true;}catch(e){try{Object.defineProperty(navigator,k,{get:function(){return v;},configurable:true});return true;}catch(x){return false;}}}" +
                "d(Navigator.prototype,'userAgent',ua);d(Navigator.prototype,'appVersion','5.0 (Windows)');d(Navigator.prototype,'platform','Win32');d(Navigator.prototype,'maxTouchPoints',0);" +
                "var brands=[{brand:'Chromium',version:'153'},{brand:'Google Chrome',version:'153'},{brand:'Not_A Brand',version:'99'}];" +
                "var data={brands:brands,mobile:false,platform:'Windows',toJSON:function(){return {brands:brands,mobile:false,platform:'Windows'};},getHighEntropyValues:function(){return Promise.resolve({architecture:'x86',bitness:'64',brands:brands,fullVersionList:[{brand:'Chromium',version:'153.0.0.0'},{brand:'Google Chrome',version:'153.0.0.0'},{brand:'Not_A Brand',version:'99.0.0.0'}],mobile:false,model:'',platform:'Windows',platformVersion:'10.0.0',uaFullVersion:'153.0.0.0',wow64:false});}};" +
                "d(Navigator.prototype,'userAgentData',data);" +
                "window.__sidiDesktopIdentity={ua:navigator.userAgent,platform:navigator.platform,touch:navigator.maxTouchPoints,mobile:navigator.userAgentData?navigator.userAgentData.mobile:null,uaPlatform:navigator.userAgentData?navigator.userAgentData.platform:null};" +
                "}catch(e){}})();";
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(web, js, Collections.singleton("*"));
                Log.i("SidiPlayer", "DESKTOP_IDENTITY_DOCUMENT_START enabled");
            } else {
                Log.w("SidiPlayer", "DOCUMENT_START_SCRIPT unavailable; using page-start fallback");
            }
        } catch (Throwable error) {
            Log.w("SidiPlayer", "cannot install desktop identity at document start", error);
        }
    }

    private void logDesktopBrowserIdentity(WebView web) {
        if (web == null) return;
        String js = "(function(){try{return JSON.stringify({ua:navigator.userAgent,platform:navigator.platform,touch:navigator.maxTouchPoints,mobile:navigator.userAgentData?navigator.userAgentData.mobile:null,uaPlatform:navigator.userAgentData?navigator.userAgentData.platform:null,iw:innerWidth,ih:innerHeight});}catch(e){return JSON.stringify({error:String(e)});}})();";
        try { web.evaluateJavascript(js, value -> Log.i("SidiPlayer", "DESKTOP_IDENTITY " + value)); }
        catch (Throwable ignored) {}
    }

    private boolean shouldUseLightTheme() {
        String mode = getThemeMode();
        boolean systemNight = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return "day".equals(mode) || ("system".equals(mode) && !systemNight);
    }

    private void applyThemePalette() {
        lightTheme = shouldUseLightTheme();
        if (lightTheme) {
            BG = Color.rgb(238, 244, 250);
            PANEL = Color.rgb(255, 255, 255);
            PANEL_2 = Color.rgb(247, 250, 253);
            BORDER = Color.rgb(183, 199, 216);
            TEXT = Color.rgb(17, 31, 48);
            MUTED = Color.rgb(89, 108, 130);
            ACCENT = Color.rgb(0, 130, 220);
            ACCENT_DARK = Color.rgb(99, 170, 220);
            HEADER_BG = Color.rgb(228, 237, 246);
            FOCUS_BG = Color.rgb(221, 239, 252);
        } else {
            BG = Color.rgb(3, 8, 14);
            PANEL = Color.rgb(7, 15, 25);
            PANEL_2 = Color.rgb(11, 22, 35);
            BORDER = Color.rgb(43, 59, 78);
            TEXT = Color.rgb(247, 249, 252);
            MUTED = Color.rgb(147, 162, 182);
            ACCENT = Color.rgb(27, 190, 255);
            ACCENT_DARK = Color.rgb(8, 58, 98);
            HEADER_BG = Color.rgb(4, 10, 18);
            FOCUS_BG = Color.rgb(8, 31, 50);
        }
    }

    private String getThemeMode() {
        return getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, "night");
    }

    private String themeModeLabel() {
        String mode = getThemeMode();
        if ("day".equals(mode)) return "День";
        if ("night".equals(mode)) return "Ночь";
        return "Системный";
    }

    private void setThemeMode(String mode) {
        getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_THEME_MODE, mode).apply();
        recreate();
    }

    private String shortAppVersion() {
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = info.versionName == null ? "?" : info.versionName.trim();
            int dash = version.indexOf('-');
            return dash > 0 ? version.substring(0, dash) : version;
        } catch (Throwable ignored) {
            return "?";
        }
    }

    // Промоушен iframe плеера в основной документ (добавлен в 11.7.1) ломает
    // балансеры, которые требуют встроенного (iframe) запуска: Stravers и подобные
    // при top-level загрузке отдают заглушку «контент не найден/недоступен в регионе»
    // с HTTP 200, поэтому сетевой fallback не срабатывает. В 11.7 промоушена не было
    // и плеер играл как iframe. Оставляем это поведением по умолчанию; промоушен
    // включается вручную только там, где нужен JS-доступ к video для Play/Pause.
    private static final boolean ENABLE_PLAYER_FRAME_PROMOTION = false;

    private boolean shouldPromotePlayerFrame() {
        if (!ENABLE_PLAYER_FRAME_PROMOTION) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
                if (info != null && info.versionName != null) {
                    String version = info.versionName;
                    int dot = version.indexOf('.');
                    String majorText = dot < 0 ? version : version.substring(0, dot);
                    int major = Integer.parseInt(majorText);
                    return major > 0 && major < 105;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isLegacyWebView() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
                if (info != null && info.versionName != null) {
                    String version = info.versionName;
                    int dot = version.indexOf('.');
                    String majorText = dot < 0 ? version : version.substring(0, dot);
                    int major = Integer.parseInt(majorText);
                    return major > 0 && major < 105;
                }
            }
        } catch (Throwable ignored) {
        }
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.R;
    }


    private boolean isYandexWebView() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
                return info != null && "com.yandex.webview".equals(info.packageName);
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean isVillybizyUrl(String rawUrl) {
        try {
            String host = Uri.parse(rawUrl).getHost();
            return host != null && (host.equalsIgnoreCase("villybizy.best")
                    || host.toLowerCase(Locale.US).endsWith(".villybizy.best"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void applyPlayerCompatibilityUserAgent(WebView web) {
        if (web == null) return;
        try {
            // Не меняем UA глобально. Подмена применяется только после воспроизведённого
            // 404 на Yandex WebView, поэтому устройства с рабочим плеером остаются на
            // штатном профиле WebView.
            web.getSettings().setUserAgentString(
                    "Mozilla/5.0 (Linux; Android 11; Android TV) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) "
                            + "Chrome/120.0.0.0 Safari/537.36");
            web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            CookieManager.getInstance().flush();
            Log.i("SidiPlayer", "compatibility UA enabled for Yandex WebView");
        } catch (Throwable error) {
            Log.e("SidiPlayer", "cannot enable compatibility UA", error);
        }
    }

    private boolean looksLikePlayerResource(String rawUrl) {
        if (rawUrl == null) return false;
        String lower = rawUrl.toLowerCase(Locale.US);
        return lower.contains("stravers.live") || lower.contains("vkvideo.cloud")
                || lower.contains(".m3u8") || lower.contains(".mpd")
                || lower.contains(".m4s") || lower.contains(".mp4")
                || lower.contains("manifest") || lower.contains("playlist");
    }

    private boolean shouldPatchLegacyPlayerDocument(String rawUrl) {
        if (rawUrl == null) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            String host = uri.getHost();
            String path = uri.getPath();
            return host != null
                    && host.toLowerCase(Locale.US).endsWith("theatre.stravers.live")
                    && (path == null || path.isEmpty() || "/".equals(path));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private WebResourceResponse interceptPatchedLegacyPlayerDocument(WebResourceRequest request) {
        if (request == null) return null;
        String rawUrl = request.getUrl().toString();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(rawUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(18000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept-Encoding", "identity");

            Map<String, String> requestHeaders = request.getRequestHeaders();
            String requestUserAgent = requestHeaders.get("User-Agent");
            connection.setRequestProperty("User-Agent", requestUserAgent == null || requestUserAgent.isEmpty()
                    ? "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/83.0.4103.120 Safari/537.36"
                    : requestUserAgent);

            String cookie = CookieManager.getInstance().getCookie(rawUrl);
            if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
            for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                String key = entry.getKey();
                if (key == null || key.equalsIgnoreCase("Accept-Encoding")
                        || key.equalsIgnoreCase("Cookie") || key.equalsIgnoreCase("User-Agent")) continue;
                connection.setRequestProperty(key, entry.getValue());
            }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 400) {
                Log.w("SidiPlayer", "player document fetch HTTP " + code);
                return null;
            }

            InputStream stream = connection.getInputStream();
            if ("gzip".equalsIgnoreCase(connection.getContentEncoding())) stream = new GZIPInputStream(stream);
            byte[] original = readLimited(stream, 3 * 1024 * 1024);
            String html = new String(original, StandardCharsets.UTF_8);

            // Удаляем Subresource Integrity: иначе браузер отвергнет даже корректно
            // загруженный JS после любых изменений/проксирования.
            html = html.replaceAll("(?is)\\s+integrity\\s*=\\s*(['\"]).*?\\1", "");

            String guard = "<script>" + legacyPlayerGuardSource()
                    + "console.log('[Sidi] iframe codec guard booted');</script>";
            if (html.matches("(?is).*<head[^>]*>.*")) {
                html = html.replaceFirst("(?is)<head([^>]*)>", "<head$1>" + guard);
            } else {
                html = guard + html;
            }

            Map<String, String> responseHeaders = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                String key = entry.getKey();
                List<String> values = entry.getValue();
                if (key == null || values == null || values.isEmpty()) continue;
                if (key.equalsIgnoreCase("Content-Length")
                        || key.equalsIgnoreCase("Content-Encoding")
                        || key.equalsIgnoreCase("Content-Security-Policy")
                        || key.equalsIgnoreCase("Content-Security-Policy-Report-Only")
                        || key.equalsIgnoreCase("X-WebKit-CSP")) continue;
                responseHeaders.put(key, values.get(values.size() - 1));
                if (key.equalsIgnoreCase("Set-Cookie")) {
                    for (String value : values) CookieManager.getInstance().setCookie(rawUrl, value);
                }
            }
            responseHeaders.put("Cache-Control", "no-store, no-cache, must-revalidate");
            responseHeaders.put("Pragma", "no-cache");

            byte[] patched = html.getBytes(StandardCharsets.UTF_8);
            Log.i("SidiPlayer", "legacy iframe HTML patched, bytes=" + patched.length);
            return new WebResourceResponse(
                    "text/html",
                    "UTF-8",
                    code,
                    connection.getResponseMessage() == null ? "OK" : connection.getResponseMessage(),
                    responseHeaders,
                    new ByteArrayInputStream(patched));
        } catch (Throwable error) {
            Log.e("SidiPlayer", "cannot patch player document", error);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private byte[] readLimited(InputStream stream, int limit) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IllegalStateException("player script is too large");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private String legacyPlayerGuardSource() {
        return "(function(){try{" +
                "var av=/av01|\\bav1\\b/i;" +
                "if(window.MediaSource&&MediaSource.isTypeSupported){var old=MediaSource.isTypeSupported.bind(MediaSource);" +
                "MediaSource.isTypeSupported=function(t){if(av.test(String(t||'')))return false;return old(t);};}" +
                "var p=window.HTMLMediaElement&&HTMLMediaElement.prototype;if(p&&p.canPlayType){var cp=p.canPlayType;" +
                "p.canPlayType=function(t){if(av.test(String(t||'')))return '';return cp.call(this,t);};}" +
                "if(navigator.mediaCapabilities&&navigator.mediaCapabilities.decodingInfo){" +
                "var di=navigator.mediaCapabilities.decodingInfo.bind(navigator.mediaCapabilities);" +
                "navigator.mediaCapabilities.decodingInfo=function(c){try{var t=c&&c.video&&c.video.contentType;" +
                "if(av.test(String(t||'')))return Promise.resolve({supported:false,smooth:false,powerEfficient:false});" +
                "}catch(x){}return di(c);};}" +
                "console.log('[Sidi] codec guard installed in current document');" +
                "}catch(e){console.log('[Sidi] codec guard error: '+e);}})();";
    }

    private void showPlayerLoading(BrowserTab tab) {
        if (tab != null && tab.loadingIndicator != null) tab.loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void hidePlayerLoading(BrowserTab tab) {
        if (tab != null && tab.loadingIndicator != null) tab.loadingIndicator.setVisibility(View.GONE);
    }

    private void injectLegacyPlayerCompatibility(WebView web) {
        if (web == null) return;
        String js = legacyPlayerGuardSource();
        try {
            web.evaluateJavascript(js, null);
        } catch (Throwable ignored) {
            try { web.loadUrl("javascript:" + js); } catch (Throwable ignoredAgain) {}
        }
    }

    private void discoverPlayerHosts(WebView web) {
        if (web == null) return;
        String js = "(function(){var out=[];function add(v){try{if(!v)return;var u=new URL(v,location.href);" +
                "if(/^https?:$/.test(u.protocol)&&out.indexOf(u.href)<0)out.push(u.href);}catch(e){}}" +
                "document.querySelectorAll('iframe[src],video[src],source[src]').forEach(function(e){add(e.src||e.getAttribute('src'));});" +
                "return JSON.stringify(out);})()";
        web.evaluateJavascript(js, value -> {
            try {
                Object decoded = new JSONTokener(value).nextValue();
                String json = decoded instanceof String ? (String) decoded : value;
                JSONArray urls = new JSONArray(json);
                for (int i = 0; i < urls.length(); i++) adBlocker.allowSessionUrl(urls.optString(i));
            } catch (Exception ignored) {
            }
        });
    }

    private void injectPlayerSafeLayout(WebView web) {
        if (web == null) return;
        String js = "(function(){try{" +
                "var head=document.head||document.getElementsByTagName('head')[0];if(!head)return JSON.stringify({wait:'head'});" +
                "var meta=document.querySelector('meta[name=viewport]');if(!meta){meta=document.createElement('meta');meta.name='viewport';meta.setAttribute('content','width=device-width, initial-scale=1, user-scalable=yes');head.appendChild(meta);}" +
                "var id='__sidi_desktop_player_136';var st=document.getElementById(id);" +
                "if(!st){st=document.createElement('style');st.id=id;st.textContent='" +
                "html,body{margin:0!important;padding:0!important;background:#000!important;}" +
                ".kinobox_iframe_container,.kinobox__iframeWrapper{box-sizing:border-box!important;}" +
                ".kinobox__menuItem,.kinobox_menu li,.kbt_list li,.kbt_button li{visibility:visible!important;opacity:1!important;}" +
                "';head.appendChild(st);}" +
                "function playerUrl(u){u=String(u||'').toLowerCase();return /stravers\\.live|bulkikim|villybizy|alloha|kodik|voidboost|videocdn|collaps|hdvb|nextembed\\.ws|shortener2\\.link|videotoday\\.site|obrut\\.show/.test(u);}" +
                "function adUrl(u){u=String(u||'').toLowerCase();return /moviead55|buzzoola|video-mech|vqserve|adhigh\\.net|ussp\\.io|showjet|skcrtxr|bidster|adlook|traffaret|adriver|betweendigital|prestreamnos/.test(u);}" +
                "function rect(e){try{var r=e.getBoundingClientRect();return {top:Math.round(r.top),left:Math.round(r.left),w:Math.round(r.width),h:Math.round(r.height),bottom:Math.round(r.bottom)};}catch(x){return null;}}" +
                "function hasMainPlayer(e){try{var fs=e.querySelectorAll('iframe[src]');for(var i=0;i<fs.length;i++){if(playerUrl(fs[i].src||fs[i].getAttribute('src')||''))return true;}}catch(x){}return false;}" +
                "function hideAd(e,why){if(!e||hasMainPlayer(e))return 0;try{e.setAttribute('data-sidi-known-ad',why||'1');e.style.setProperty('display','none','important');e.style.setProperty('visibility','hidden','important');e.style.setProperty('pointer-events','none','important');return 1;}catch(x){return 0;}}" +
                "function cleanKnownAds(){var n=0;['#movie_video','div.topAdPad','#tgWrapper','.rmp-vpaid-container','.rmp-ad-container','.rmp-ad-overlay'].forEach(function(q){try{document.querySelectorAll(q).forEach(function(e){n+=hideAd(e,q);});}catch(x){}});" +
                "try{document.querySelectorAll('iframe[src]').forEach(function(f){var s=(f.src||f.getAttribute('src')||'').toLowerCase();if(playerUrl(s))return;if(adUrl(s))n+=hideAd(f,'ad-iframe');});}catch(x){}" +
                "try{document.querySelectorAll('[id]').forEach(function(e){var v=e.id||'';if(!/^[A-Za-z]{5}[0-9]{10,22}$/.test(v)||hasMainPlayer(e))return;try{if(e.closest&&e.closest('.kinobox,.kinobox_iframe_container,.kinobox__iframeWrapper'))return;}catch(z){}var c=getComputedStyle(e),z=parseInt(c.zIndex||'0',10)||0;var pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';if(!(pos||z>=100))return;var hasBlank=!!e.querySelector('a[target=\"_blank\"]');var hasIframe=!!e.querySelector('iframe');if(pos||hasBlank||hasIframe)n+=hideAd(e,'logged-random-id');});}catch(x){}" +
                "try{var screen=Math.max(1,innerWidth*innerHeight),nodes=document.querySelectorAll('div,section,aside');for(var i=0;i<nodes.length&&i<2000;i++){var e=nodes[i];if(hasMainPlayer(e)||e.getAttribute('data-sidi-known-ad'))continue;var c=getComputedStyle(e);if(c.position!=='fixed'&&c.position!=='sticky'&&c.position!=='absolute')continue;var r=e.getBoundingClientRect(),a=r.width*r.height;if(r.width<60||r.height<40||a<60*40||a>screen*0.96)continue;var meta=String((e.id||'')+' '+(e.className||'')).toLowerCase();var adish=/ad|advert|banner|promo|popup|overlay|preroll|vast|vpaid|traffaret|adriver|bn_|bn-/.test(meta);var close=!!(e.querySelector&&e.querySelector('[class*=close],[class*=cross],[aria-label*=close],[aria-label*=закры]'));var corner=(r.left<80&&r.bottom>innerHeight*0.55)||(r.right>innerWidth-80&&r.bottom>innerHeight*0.55);if(adish||(close&&(a>screen*0.02||corner))||(corner&&a>screen*0.015&&!!e.querySelector('iframe,img,a[target=\\\"_blank\\\"]')))n+=hideAd(e,adish?'auto-fixed-ad':(corner?'auto-corner-banner':'auto-fixed-close'));}}catch(x){}return n;}" +
                "var adsHidden=cleanKnownAds();" +
                "if(adsHidden){try{console.log('SidiAutoAd hidden='+adsHidden);}catch(x){}}" +
                "if(!window.__sidiKnownPopupGuard136){window.__sidiKnownPopupGuard136=true;document.addEventListener('click',function(ev){try{var a=ev.target&&ev.target.closest?ev.target.closest('a[href]'):null;if(!a)return;var h=a.href||a.getAttribute('href')||'';if((a.target||'').toLowerCase()==='_blank'&&adUrl(h)){ev.preventDefault();ev.stopPropagation();if(ev.stopImmediatePropagation)ev.stopImmediatePropagation();}}catch(x){}},true);}" +
                "var frame=null;Array.prototype.slice.call(document.querySelectorAll('iframe[src]')).some(function(f){if(playerUrl(f.src||f.getAttribute('src')||'')){frame=f;return true;}return false;});" +
                "var wrap=document.querySelector('.kinobox__iframeWrapper,.kinobox_iframe_container');" +
                "if(!wrap&&frame)wrap=frame.parentElement;" +
                "var root=wrap?(wrap.closest('.kinobox')||wrap.parentElement):null;" +
                "var before=rect(wrap),frBefore=rect(frame);" +
                "var menuItems=document.querySelectorAll('.kinobox__menuItem,.kinobox_menu li,.kbt_list li,.kbt_button li');" +
                "var menu=document.querySelector('.kinobox__menuBody,.kinobox_menu,.kbt_list,.kbt_button');" +
                "if(!window.__sidiPlayerChoiceListener1310){window.__sidiPlayerChoiceListener1310=true;document.addEventListener('click',function(ev){try{var item=ev.target&&ev.target.closest?ev.target.closest('.kinobox__menuItem,.kinobox_menu li,.kbt_list li,.kbt_button li'):null;if(!item)return;var items=Array.prototype.slice.call(document.querySelectorAll('.kinobox__menuItem,.kinobox_menu li,.kbt_list li,.kbt_button li'));var idx=items.indexOf(item);if(idx<0)return;var label=String(item.innerText||item.textContent||item.getAttribute('title')||'').replace(/\\s+/g,' ').trim().slice(0,120);SidiPlayerChoice.save(idx,label);}catch(x){}},true);}" +
                "if(wrap){var viewportW=Math.max(640,Math.min(window.innerWidth,(document.documentElement&&document.documentElement.clientWidth)||window.innerWidth));var menuW=Math.min(220,Math.max(180,Math.round(viewportW*0.22)));var playerW=Math.min(760,Math.max(560,viewportW-menuW-16));" +
                "wrap.style.setProperty('width',playerW+'px','important');wrap.style.setProperty('max-width',playerW+'px','important');wrap.style.setProperty('min-width','0','important');" +
                "wrap.style.setProperty('height','330px','important');wrap.style.setProperty('min-height','330px','important');wrap.style.setProperty('max-height','330px','important');wrap.style.setProperty('padding-top','0','important');wrap.style.setProperty('overflow','hidden','important');" +
                "if(frame){frame.style.setProperty('width','100%','important');frame.style.setProperty('max-width','100%','important');frame.style.setProperty('height','330px','important');frame.style.setProperty('max-height','330px','important');}" +
                "if(root){root.style.setProperty('position','relative','important');root.style.setProperty('width',Math.min(viewportW,playerW+menuW+12)+'px','important');root.style.setProperty('max-width','100%','important');root.style.setProperty('min-height','330px','important');root.style.setProperty('overflow','visible','important');}" +
                "if(menu){var wr=wrap.getBoundingClientRect(),rr=root?root.getBoundingClientRect():{left:0,top:0};var left=Math.round((wr.left-rr.left)+playerW+12),top=Math.round(wr.top-rr.top);menu.style.setProperty('display','block','important');menu.style.setProperty('visibility','visible','important');menu.style.setProperty('opacity','1','important');menu.style.setProperty('position','absolute','important');menu.style.setProperty('left',left+'px','important');menu.style.setProperty('top',top+'px','important');menu.style.setProperty('width',menuW+'px','important');menu.style.setProperty('height','330px','important');menu.style.setProperty('z-index','20','important');menu.style.setProperty('max-height','330px','important');menu.style.setProperty('overflow-y','auto','important');menu.style.setProperty('z-index','20','important');}" +
                "}" +
                "if(wrap&&!window.__sidiPlayerAutoScrolled136){window.__sidiPlayerAutoScrolled136=true;setTimeout(function(){try{var r=wrap.getBoundingClientRect();if(r.top>80||r.bottom>window.innerHeight+20){window.scrollTo({top:Math.max(0,(window.scrollY||0)+r.top-8),behavior:'auto'});}}catch(x){}},180);}" +
                "if(wrap){setTimeout(function(){try{var r=wrap.getBoundingClientRect();if(r.top<0||r.top>120||r.bottom>window.innerHeight+20||r.bottom<40){window.scrollTo({top:Math.max(0,(window.scrollY||0)+r.top-12),behavior:'auto'});}}catch(x){}},220);}" +
                "return JSON.stringify({iw:window.innerWidth,ih:window.innerHeight,scrollY:Math.round(window.scrollY||0),dpr:window.devicePixelRatio,frame:frame?(frame.src||'').slice(0,220):'',wrapClass:wrap?(wrap.className||wrap.id||wrap.tagName):'',before:before,frameBefore:frBefore,after:rect(wrap),frameAfter:rect(frame),menuItems:menuItems.length,menu:rect(menu),viewportW:(typeof viewportW==='number'?viewportW:null),playerW:(typeof playerW==='number'?playerW:null),menuW:(typeof menuW==='number'?menuW:null),adsHidden:adsHidden,docH:Math.max(document.body?document.body.scrollHeight:0,document.documentElement?document.documentElement.scrollHeight:0)});" +
                "}catch(e){return JSON.stringify({error:String(e&&e.message||e)});}})();";
        try {
            web.evaluateJavascript(js, result -> Log.i("SidiPlayerLayout", "PLAYER_DESKTOP_LAYOUT_136 " + result));
        } catch (Throwable ignored) {}
    }


    private void restoreSavedPlayerChoice(BrowserTab tab, WebView web) {
        if (tab == null || web == null || tab.webView != web) return;
        MovieRecord record = store.get(tab.key);
        if (record == null || record.playerIndex < 0) return;
        if (tab.playerChoiceRestoredGeneration == tab.playerLoadGeneration) return;
        final int wantedIndex = record.playerIndex;
        final String wantedLabel = record.playerLabel == null ? "" : record.playerLabel.trim();
        String js = "(function(){try{" +
                "var items=Array.prototype.slice.call(document.querySelectorAll('.kinobox__menuItem,.kinobox_menu li,.kbt_list li,.kbt_button li'));" +
                "if(!items.length)return JSON.stringify({status:'no-menu'});" +
                "function norm(v){return String(v||'').replace(/\\s+/g,' ').trim().toLowerCase();}" +
                "var idx=" + wantedIndex + ",wanted=" + JSONObject.quote(wantedLabel) + ";" +
                "if((idx<0||idx>=items.length)&&wanted){var w=norm(wanted);for(var i=0;i<items.length;i++){var t=norm(items[i].innerText||items[i].textContent||items[i].getAttribute('title')||'');if(t===w||t.indexOf(w)>=0||w.indexOf(t)>=0){idx=i;break;}}}" +
                "if(idx<0||idx>=items.length)return JSON.stringify({status:'not-found',count:items.length,index:idx,wanted:wanted});" +
                "var e=items[idx],cls=String(e.className||'').toLowerCase(),selected=e.getAttribute('aria-selected')==='true'||/(^|\\s)(active|selected|current)(\\s|$)/.test(cls);" +
                "var label=String(e.innerText||e.textContent||e.getAttribute('title')||'').replace(/\\s+/g,' ').trim();" +
                "if(selected)return JSON.stringify({status:'already',index:idx,label:label,count:items.length});" +
                "e.click();return JSON.stringify({status:'clicked',index:idx,label:label,count:items.length});" +
                "}catch(e){return JSON.stringify({status:'error',error:String(e&&e.message||e)});}})();";
        try {
            final int generation = tab.playerLoadGeneration;
            web.evaluateJavascript(js, value -> {
                if (tab.webView != web || tab.playerLoadGeneration != generation || value == null) return;
                String decoded = value;
                try {
                    Object raw = new JSONTokener(value).nextValue();
                    if (raw instanceof String) decoded = (String) raw;
                    JSONObject result = new JSONObject(decoded);
                    String status = result.optString("status", "");
                    Log.i("SidiPlayerChoice", "PLAYER_CHOICE_RESTORE status=" + status
                            + " index=" + result.optInt("index", -1)
                            + " label=" + result.optString("label", "")
                            + " count=" + result.optInt("count", 0));
                    if ("clicked".equals(status) || "already".equals(status)) {
                        tab.playerChoiceRestoredGeneration = generation;
                    }
                } catch (Throwable ignored) {
                    Log.i("SidiPlayerChoice", "PLAYER_CHOICE_RESTORE raw=" + value);
                }
            });
        } catch (Throwable error) {
            Log.w("SidiPlayerChoice", "PLAYER_CHOICE_RESTORE_FAILED", error);
        }
    }

    private void capturePlayerState(BrowserTab tab) {
        if (tab == null || tab.webView == null) return;
        tab.webView.evaluateJavascript(PlayerMediaController.buildStateScript(), value -> {
            try {
                Object decoded = new JSONTokener(value).nextValue();
                String json = decoded instanceof String ? (String) decoded : value;
                JSONObject o = new JSONObject(json);
                MovieRecord record = store.get(tab.key);
                if (record == null) return;
                boolean changed = false;
                int season = o.optInt("season", 0);
                int episode = o.optInt("episode", 0);
                String translation = o.optString("translation", "").trim();
                long position = o.optLong("position", 0);
                long duration = o.optLong("duration", 0);
                if (!record.episodeManual && season > 0 && season != record.season) { record.season = season; changed = true; }
                if (!record.episodeManual && episode > 0 && episode != record.episode) { record.episode = episode; changed = true; }
                if (!record.translationManual && !translation.isEmpty() && !translation.equals(record.translation)) { record.translation = translation; changed = true; }
                if (position > 2 && Math.abs(position - record.positionSeconds) >= 5) { record.positionSeconds = position; changed = true; }
                if (duration > 0 && duration != record.durationSeconds) { record.durationSeconds = duration; changed = true; }
                if (changed) {
                    store.update(record);
                    refreshPlayerToolbar(tab);
                    if (activeTab == null) refreshHome();
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void resetPlayerControls(BrowserTab tab) {
        if (tab == null) return;
        tab.controlsBridgeInstalled = false;
        tab.controlsReadInFlight = false;
        tab.controlsMissCount = 0;
        tab.controlsSignature = "";
        tab.controlsState = null;
        if (tab.controlsGroup != null) tab.controlsGroup.setVisibility(View.GONE);
    }

    private void refreshPlayerControls(BrowserTab tab, boolean forceInstall) {
        if (tab == null || tab.webView == null || tab.controlsReadInFlight) return;
        WebView web = tab.webView;
        // 12.0: do not install MutationObserver/setInterval into the player page.
        tab.controlsBridgeInstalled = false;
        tab.controlsReadInFlight = true;
        PlayerControlsBridge.read(web, state -> {
            tab.controlsReadInFlight = false;
            if (tab.webView != web) return;
            if (!state.supported) {
                tab.controlsMissCount++;
                if (tab.controlsMissCount >= 3 && tab.controlsGroup != null
                        && tab.controlsGroup.getVisibility() != View.GONE) {
                    tab.controlsGroup.setVisibility(View.GONE);
                    refreshPlayerToolbar(tab);
                }
                if (tab.controlsMissCount == 1 || tab.controlsMissCount % 12 == 0) {
                    Log.i("SidiControls", "menu probe unsupported url=" + web.getUrl()
                            + " reason=" + state.error);
                }
                return;
            }
            tab.controlsMissCount = 0;
            String signature = state.signature();
            boolean changed = !signature.equals(tab.controlsSignature);
            tab.controlsState = state;
            tab.controlsSignature = signature;
            applyPlayerControlsToToolbar(tab, state);
            syncRecordFromPlayerControls(tab, state);
            if (changed) {
                Log.i("SidiControls", "menus provider=" + state.provider
                        + " translations=" + state.translation.options.size()
                        + " seasons=" + state.season.options.size()
                        + " episodes=" + state.episode.options.size()
                        + " selected=" + state.translation.selectedLabel + "/"
                        + state.season.selectedLabel + "/" + state.episode.selectedLabel
                        + " translationOptions=" + state.translation.options
                        + " seasonOptions=" + state.season.options
                        + " episodeOptions=" + state.episode.options);
            }
        });
    }

    private void applyPlayerControlsToToolbar(BrowserTab tab, PlayerControlsBridge.State state) {
        if (tab == null || state == null) return;
        boolean any = updatePlayerControlButton(tab.translationControl, state.translation,
                "Озвучка", PlayerControlsBridge.KIND_TRANSLATION);
        any |= updatePlayerControlButton(tab.seasonControl, state.season,
                "Сезон", PlayerControlsBridge.KIND_SEASON);
        any |= updatePlayerControlButton(tab.episodeControl, state.episode,
                "Серия", PlayerControlsBridge.KIND_EPISODE);
        if (tab.controlsGroup != null) {
            tab.controlsGroup.setVisibility(any ? View.VISIBLE : View.GONE);
        }
    }

    private boolean updatePlayerControlButton(Button button, PlayerControlsBridge.Menu menu,
                                              String fallback, String kind) {
        if (button == null) return false;
        if (menu == null || !menu.available()) {
            button.setVisibility(View.GONE);
            return false;
        }
        String selected = menu.selectedLabel == null ? "" : menu.selectedLabel.trim();
        if (selected.isEmpty() && menu.selectedIndex >= 0
                && menu.selectedIndex < menu.options.size()) {
            selected = menu.options.get(menu.selectedIndex);
        }
        String label;
        if (PlayerControlsBridge.KIND_TRANSLATION.equals(kind)) {
            label = selected.isEmpty() ? fallback : selected;
        } else if (selected.isEmpty()) {
            label = fallback;
        } else if (selected.toLowerCase(Locale.US).contains(fallback.toLowerCase(Locale.US))) {
            label = selected;
        } else {
            int number = extractFirstPositiveInt(selected);
            label = number > 0 ? fallback + " " + number : fallback + ": " + selected;
        }
        button.setText(label + "  ▾");
        button.setVisibility(View.VISIBLE);
        button.setEnabled(true);
        return true;
    }

    private void syncRecordFromPlayerControls(BrowserTab tab, PlayerControlsBridge.State state) {
        MovieRecord record = store.get(tab.key);
        if (record == null || state == null || !state.supported) return;
        boolean changed = false;
        String translation = cleanSelectedControlLabel(state.translation,
                PlayerControlsBridge.KIND_TRANSLATION);
        int season = selectedControlNumber(state.season);
        int episode = selectedControlNumber(state.episode);
        if (!translation.isEmpty() && !translation.equals(record.translation)) {
            record.translation = translation;
            record.translationManual = false;
            changed = true;
        }
        if (season > 0 && season != record.season) {
            record.season = season;
            record.episodeManual = false;
            changed = true;
        }
        if (episode > 0 && episode != record.episode) {
            record.episode = episode;
            record.episodeManual = false;
            changed = true;
        }
        if (changed) {
            store.update(record);
            refreshPlayerToolbar(tab);
            if (activeTab == null) refreshHome();
        }
    }

    private String cleanSelectedControlLabel(PlayerControlsBridge.Menu menu, String kind) {
        if (menu == null) return "";
        String value = menu.selectedLabel == null ? "" : menu.selectedLabel.trim();
        if (value.isEmpty() && menu.selectedIndex >= 0 && menu.selectedIndex < menu.options.size()) {
            value = menu.options.get(menu.selectedIndex).trim();
        }
        if (PlayerControlsBridge.KIND_TRANSLATION.equals(kind)) return value;
        int number = extractFirstPositiveInt(value);
        return number > 0 ? String.valueOf(number) : value;
    }

    private int selectedControlNumber(PlayerControlsBridge.Menu menu) {
        return extractFirstPositiveInt(cleanSelectedControlLabel(menu, menu == null ? "" : menu.kind));
    }

    private int extractFirstPositiveInt(String value) {
        if (value == null) return 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d{1,4}").matcher(value);
        if (!matcher.find()) return 0;
        try {
            return Math.max(0, Integer.parseInt(matcher.group()));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private void showPlayerControlDialog(BrowserTab tab, String kind) {
        if (tab == null || tab.webView == null) return;
        PlayerControlsBridge.State state = tab.controlsState;
        PlayerControlsBridge.Menu menu = state == null ? null : state.menu(kind);
        if (menu == null || !menu.available()) {
            refreshPlayerControls(tab, true);
            toast("Список плеера ещё загружается");
            return;
        }
        String title;
        if (PlayerControlsBridge.KIND_TRANSLATION.equals(kind)) title = "Перевод / озвучка";
        else if (PlayerControlsBridge.KIND_SEASON.equals(kind)) title = "Сезон";
        else title = "Серия";
        String[] options = menu.options.toArray(new String[0]);
        int checked = menu.selectedIndex >= 0 && menu.selectedIndex < options.length
                ? menu.selectedIndex : -1;
        final AlertDialog[] holder = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(options, checked, (d, which) -> {
                    if (which < 0 || which >= options.length) return;
                    String selected = options[which];
                    if (holder[0] != null) holder[0].dismiss();
                    PlayerControlsBridge.select(tab.webView, kind, which, selected, (sent, reason) -> {
                        if (!sent) {
                            toast("Плеер не принял выбор");
                            Log.w("SidiControls", "selection rejected kind=" + kind
                                    + " option=" + selected + " reason=" + reason);
                            return;
                        }
                        handler.postDelayed(() -> refreshPlayerControls(tab, false), 180L);
                        handler.postDelayed(() -> refreshPlayerControls(tab, false), 700L);
                        handler.postDelayed(() -> refreshPlayerControls(tab, false), 1600L);
                    });
                })
                .setNegativeButton("Отмена", null)
                .create();
        holder[0] = dialog;
        dialog.show();
    }

    private static final String CHOICE_EMPTY = "Не указано";
    private static final String CHOICE_OTHER = "Другое…";

    private static final String[] TRANSLATION_CHOICES = {
            "HD Rezka",
            "TV Shows",
            "Newstudio",
            "Lostfilm",
            "Кубик в Кубе",
            "Amedia",
            "AlexFilm",
            "BaibaKo",
            "Jaskier",
            "Кураж-Бамбей",
            "IdeaFilm",
            "ColdFilm",
            "NewComers",
            "Dubbing Pro",
            "Официальный дубляж",
            "Оригинал",
            "Субтитры"
    };

    private void showEditDialog(MovieRecord record) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));

        EditText note = formField("Заметка", record.note, false);
        note.setMinLines(2);
        note.setMaxLines(4);
        form.addView(fieldBlock("Заметка", note));

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.TOP);

        BrowserTab linkedTab = findTab(record.key);
        PlayerControlsBridge.State liveControls = linkedTab == null ? null : linkedTab.controlsState;
        Spinner translation = choiceSpinner(translationOptions(record.translation,
                        liveControls == null ? null : liveControls.translation), record.translation,
                "Свой вариант перевода", false);
        Spinner season = choiceSpinner(numberOptions(record.season,
                        liveControls == null ? null : liveControls.season),
                record.season > 0 ? String.valueOf(record.season) : "",
                "Номер сезона", true);
        Spinner episode = choiceSpinner(numberOptions(record.episode,
                        liveControls == null ? null : liveControls.episode),
                record.episode > 0 ? String.valueOf(record.episode) : "",
                "Номер серии", true);

        LinearLayout translationBlock = fieldBlock("Перевод / озвучка", translation);
        LinearLayout seasonBlock = fieldBlock("Сезон", season);
        LinearLayout episodeBlock = fieldBlock("Серия", episode);

        choices.addView(translationBlock, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2f));
        LinearLayout.LayoutParams seasonParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        seasonParams.setMargins(dp(10), 0, 0, 0);
        choices.addView(seasonBlock, seasonParams);
        LinearLayout.LayoutParams episodeParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        episodeParams.setMargins(dp(10), 0, 0, 0);
        choices.addView(episodeBlock, episodeParams);
        form.addView(choices);

        String current = record.positionSeconds > 0 ? MovieRecord.formatTime(record.positionSeconds) : "не определено";
        TextView position = text("Позиция плеера: " + current + ". Если плеер доступен из WebView, она обновляется автоматически.",
                12, MUTED);
        position.setPadding(0, dp(8), 0, 0);
        form.addView(position);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(record.title)
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            record.note = note.getText().toString().trim();
            record.translation = selectedChoice(translation);
            boolean liveMetadata = linkedTab != null && linkedTab.controlsState != null
                    && linkedTab.controlsState.supported;
            record.translationManual = !liveMetadata && !record.translation.isEmpty();
            record.season = parsePositiveInt(selectedChoice(season));
            record.episode = parsePositiveInt(selectedChoice(episode));
            record.episodeManual = !liveMetadata && (record.season > 0 || record.episode > 0);
            store.update(record);
            refreshHome();
            refreshPlayerToolbars();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private List<String> translationOptions(String current, PlayerControlsBridge.Menu liveMenu) {
        List<String> values = new ArrayList<>();
        values.add(CHOICE_EMPTY);
        if (liveMenu != null && liveMenu.available()) {
            for (String option : liveMenu.options) {
                String clean = option == null ? "" : option.trim();
                if (!clean.isEmpty() && !containsIgnoreCase(values, clean)) values.add(clean);
            }
        } else {
            for (String option : TRANSLATION_CHOICES) values.add(option);
        }
        String clean = current == null ? "" : current.trim();
        if (!clean.isEmpty() && !containsIgnoreCase(values, clean)) values.add(clean);
        values.add(CHOICE_OTHER);
        return values;
    }

    private List<String> numberOptions(int current, PlayerControlsBridge.Menu liveMenu) {
        List<String> values = new ArrayList<>();
        values.add(CHOICE_EMPTY);
        if (liveMenu != null && liveMenu.available()) {
            for (String option : liveMenu.options) {
                int number = extractFirstPositiveInt(option);
                if (number > 0) {
                    String value = String.valueOf(number);
                    if (!values.contains(value)) values.add(value);
                }
            }
        } else {
            for (int i = 1; i <= 20; i++) values.add(String.valueOf(i));
        }
        if (current > 0 && !values.contains(String.valueOf(current))) values.add(String.valueOf(current));
        values.add(CHOICE_OTHER);
        return values;
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) if (value.equalsIgnoreCase(target)) return true;
        return false;
    }

    private Spinner choiceSpinner(List<String> values, String current, String customTitle, boolean numeric) {
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        spinner.setFocusable(true);
        spinner.setFocusableInTouchMode(true);
        spinner.setPadding(dp(10), 0, dp(8), 0);
        spinner.setBackground(roundedStroke(PANEL_2, BORDER, 10, 1));
        setSpinnerValues(spinner, values, current);
        final boolean[] ready = {false};
        final int[] lastValidPosition = {spinner.getSelectedItemPosition()};
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!ready[0]) { ready[0] = true; lastValidPosition[0] = position; return; }
                Object item = spinner.getSelectedItem();
                if (item != null && CHOICE_OTHER.equals(item.toString())) {
                    showCustomChoiceDialog(spinner, customTitle, numeric, lastValidPosition[0]);
                } else {
                    lastValidPosition[0] = position;
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        return spinner;
    }

    private void setSpinnerValues(Spinner spinner, List<String> values, String selected) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, values) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    text.setTextColor(TEXT);
                    text.setTextSize(15);
                    text.setPadding(dp(10), 0, dp(8), 0);
                }
                return view;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    text.setTextColor(TEXT);
                    text.setTextSize(16);
                    text.setPadding(dp(16), dp(12), dp(16), dp(12));
                    text.setBackgroundColor(PANEL_2);
                }
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        String clean = selected == null ? "" : selected.trim();
        int selectedIndex = 0;
        if (!clean.isEmpty()) {
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i).equalsIgnoreCase(clean)) { selectedIndex = i; break; }
            }
        }
        spinner.setSelection(selectedIndex, false);
    }

    private void showCustomChoiceDialog(Spinner spinner, String title, boolean numeric, int previous) {
        EditText input = formField(numeric ? "Например: 21" : "Введите название", "", true);
        if (numeric) input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        previous = Math.max(0, previous);
        final int restorePosition = previous;
        AlertDialog custom = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setNegativeButton("Отмена", (d, w) -> spinner.setSelection(restorePosition))
                .setPositiveButton("Добавить", null)
                .create();
        custom.setOnShowListener(d -> custom.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (numeric && parsePositiveInt(value) <= 0) {
                input.setError("Введите число больше нуля");
                return;
            }
            if (value.isEmpty()) {
                input.setError("Введите значение");
                return;
            }
            List<String> updated = new ArrayList<>();
            for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
                String item = String.valueOf(spinner.getAdapter().getItem(i));
                if (!CHOICE_OTHER.equals(item) && !item.equalsIgnoreCase(value)) updated.add(item);
            }
            updated.add(value);
            updated.add(CHOICE_OTHER);
            setSpinnerValues(spinner, updated, value);
            custom.dismiss();
        }));
        custom.setOnDismissListener(d -> {
            Object item = spinner.getSelectedItem();
            if (item != null && CHOICE_OTHER.equals(item.toString())) spinner.setSelection(restorePosition);
        });
        custom.show();
    }

    private String selectedChoice(Spinner spinner) {
        Object selected = spinner.getSelectedItem();
        if (selected == null) return "";
        String value = selected.toString().trim();
        if (CHOICE_EMPTY.equals(value) || CHOICE_OTHER.equals(value)) return "";
        return value;
    }

    private EditText formField(String hint, String value, boolean singleLine) {
        EditText input = new EditText(this);
        input.setText(value == null ? "" : value);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(Color.rgb(112, 121, 138));
        input.setTextSize(16);
        input.setSingleLine(singleLine);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(rounded(PANEL_2, 10));
        return input;
    }

    private EditText numberField(int value) {
        EditText input = formField("0", value > 0 ? String.valueOf(value) : "", true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private LinearLayout fieldBlock(String label, View input) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView caption = text(label, 13, MUTED);
        caption.setPadding(0, dp(8), 0, dp(5));
        block.addView(caption);
        block.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return block;
    }

    private void showSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(12));

        LinearLayout appInfo = new LinearLayout(this);
        appInfo.setOrientation(LinearLayout.HORIZONTAL);
        appInfo.setGravity(Gravity.CENTER_VERTICAL);
        appInfo.setPadding(dp(14), dp(10), dp(14), dp(10));
        appInfo.setBackground(roundedStroke(PANEL_2, BORDER, 14, 1));

        ImageView appLogo = new ImageView(this);
        appLogo.setImageResource(R.drawable.ic_brand_sofa);
        appLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        appInfo.addView(appLogo, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout appText = new LinearLayout(this);
        appText.setOrientation(LinearLayout.VERTICAL);
        appText.setPadding(dp(12), 0, dp(12), 0);
        TextView appName = text("Сиди и смотри", 17, TEXT);
        appName.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        appText.addView(appName);
        TextView version = text("Версия " + shortAppVersion(), 12, MUTED);
        appText.addView(version);
        appInfo.addView(appText, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button updateApp = smallButton(UpdateManager.hasAvailableUpdate(this)
                ? "Обновить" : "Проверить обновления");
        updateApp.setTextSize(12);
        updateApp.setSingleLine(true);
        updateApp.setMinHeight(0);
        updateApp.setMinimumHeight(0);
        updateApp.setPadding(dp(14), 0, dp(14), 0);
        appInfo.addView(updateApp, new LinearLayout.LayoutParams(
                dp(compact ? 150 : 188), dp(40)));
        box.addView(appInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button theme = settingsButton("Режим: " + themeModeLabel(), "");
        Button manualAd = settingsButton("Удалить рекламу", "");
        Button undoAdRule = settingsButton("Отменить удаление", "");
        Button addressRules = settingsButton("Подмена адреса: " + playerUrlRules.activeRuleLabel(), "");
        Button send = settingsButton("Экспорт данных", "");
        Button receive = settingsButton("Импорт данных", "");
        Button diagnostics = settingsButton("Диагностический отчёт", "");
        Button sleepTimer = settingsButton(sleepTimerSettingsLabel(), "");
        Button clearHistory = settingsButton("Очистить историю", "");
        Button clearFavorites = settingsButton("Очистить избранное", "");

        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams columnsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        columnsParams.setMargins(0, dp(10), 0, 0);
        box.addView(columns, columnsParams);

        LinearLayout appearanceCard = settingsCard("Оформление");
        appearanceCard.addView(theme, settingsCardButtonParams());
        columns.addView(appearanceCard, settingsColumnParams(true));

        LinearLayout adCard = settingsCard("Реклама");
        adCard.addView(manualAd, settingsCardButtonParams());
        adCard.addView(undoAdRule, settingsCardButtonParams());
        columns.addView(adCard, settingsColumnParams(false));

        LinearLayout playerCard = settingsCard("Плеер");
        TextView uaStatus = text("Режим браузера: Windows Chrome", 11, MUTED);
        uaStatus.setPadding(dp(12), 0, dp(12), dp(6));
        playerCard.addView(uaStatus);
        playerCard.addView(addressRules, settingsCardButtonParams());
        columns.addView(playerCard, settingsColumnParams(false));

        LinearLayout dataCard = settingsCard("Данные");
        dataCard.addView(send, settingsCardButtonParams());
        dataCard.addView(receive, settingsCardButtonParams());
        columns.addView(dataCard, settingsColumnParams(false));

        LinearLayout systemCard = settingsCard("Система");
        systemCard.addView(diagnostics, settingsCardButtonParams());
        systemCard.addView(sleepTimer, settingsCardButtonParams());
        columns.addView(systemCard, settingsColumnParams(false));

        TextView dangerTitle = settingsSection("Очистка данных");
        dangerTitle.setPadding(dp(4), dp(10), dp(4), dp(5));
        box.addView(dangerTitle);
        LinearLayout dangerRow = settingsGridRow();
        dangerRow.addView(clearHistory, settingsGridButtonParams(true));
        dangerRow.addView(clearFavorites, settingsGridButtonParams(false));
        box.addView(dangerRow, settingsGridRowParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Настройки")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();

        diagnostics.setOnClickListener(v -> {
            dialog.dismiss();
            startDiagnosticsCollection();
        });
        send.setOnClickListener(v -> {
            dialog.dismiss();
            showTransferDataDialog();
        });
        receive.setOnClickListener(v -> {
            dialog.dismiss();
            showReceiveDataDialog();
        });
        theme.setOnClickListener(v -> showThemeDialog());
        sleepTimer.setOnClickListener(v -> {
            dialog.dismiss();
            showSleepTimerDialog();
        });
        manualAd.setOnClickListener(v -> {
            dialog.dismiss();
            if (activeTab == null || activeTab.webView == null) toast("Сначала откройте фильм или сериал");
            else startAdRemovalMode(activeTab);
        });
        undoAdRule.setOnClickListener(v -> undoManualAdRemoval());
        addressRules.setOnClickListener(v -> {
            dialog.dismiss();
            showAddressReplacementDialog();
        });
        updateApp.setOnClickListener(v -> {
            if (UpdateManager.hasAvailableUpdate(this)) {
                dialog.dismiss();
                UpdateManager.startAvailableUpdate(this);
                return;
            }
            updateApp.setEnabled(false);
            updateApp.setText("Проверяю…");
            UpdateManager.check(this, true, available -> {
                setUpdateAvailable(available);
                updateApp.setEnabled(true);
                updateApp.setText(available ? "Обновить" : "Проверить обновления");
            });
        });
        clearHistory.setOnClickListener(v -> confirmClearHistory());
        clearFavorites.setOnClickListener(v -> confirmClearFavorites());
        dialog.setOnShowListener(v -> {
            if (dialog.getWindow() != null && !compact) {
                int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
                dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private LinearLayout settingsCard(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(8));
        card.setBackground(roundedStroke(PANEL_2, BORDER, 12, 1));
        TextView heading = settingsSection(title);
        heading.setPadding(dp(8), dp(4), dp(8), dp(7));
        card.addView(heading);
        return card;
    }

    private LinearLayout.LayoutParams settingsColumnParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(first ? 0 : dp(4), 0, dp(4), 0);
        return params;
    }

    private LinearLayout.LayoutParams settingsCardButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        params.setMargins(0, 0, 0, dp(5));
        return params;
    }

    private TextView settingsSection(String title) {
        TextView heading = text(title.toUpperCase(Locale.ROOT), 12, ACCENT);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setLetterSpacing(0.05f);
        heading.setPadding(dp(4), dp(20), dp(4), dp(8));
        return heading;
    }

    private Button settingsButton(String title, String subtitle) {
        Button button = smallButton(title);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setTextSize(13);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setMaxLines(1);
        return button;
    }

    private LinearLayout settingsGridRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private LinearLayout.LayoutParams settingsGridButtonParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        if (first) params.setMargins(0, 0, dp(4), 0);
        else params.setMargins(dp(4), 0, 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams settingsGridRowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        params.setMargins(0, 0, 0, dp(6));
        return params;
    }

    private String playerUrlFor(MovieRecord record) {
        if (record == null || record.sourceUrl == null || record.sourceUrl.trim().isEmpty()) return "";
        return playerUrlRules == null ? record.playerUrl() : playerUrlRules.apply(record.sourceUrl);
    }

    private boolean isStraversUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        try {
            String host = Uri.parse(rawUrl).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            return host.equals("stravers.live") || host.endsWith(".stravers.live");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isSafePlayerRetryUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty()) return false;
        if (isDirectPlayerPage(rawUrl) || isVillybizyUrl(rawUrl)) return true;
        try {
            String host = Uri.parse(rawUrl).getHost();
            if (host != null) {
                host = host.toLowerCase(Locale.US);
                if (host.startsWith("bulkikim.")) return true;
                if (playerUrlRules != null && playerUrlRules.isKnownMirrorHost(host)) return true;
            }
        } catch (Throwable ignored) {
        }
        MovieRecord record = activeTab == null ? null : store.get(activeTab.key);
        return record != null && isAllowedPlayerMainFrameNavigation(playerUrlFor(record), rawUrl);
    }

    private void probePlayerIframe(BrowserTab tab, WebView web) {
        if (tab == null || web == null || tab.webView != web) return;
        String js = "(function(){try{var f=document.querySelector('iframe[src*=\\\"stravers.live\\\"]');return f?(f.src||f.getAttribute('src')||''):'';}catch(e){return '';}})();";
        try {
            web.evaluateJavascript(js, value -> {
                if (tab.webView != web || value == null) return;
                try {
                    Object decoded = new JSONTokener(value).nextValue();
                    String src = decoded instanceof String ? (String) decoded : "";
                    if (src.isEmpty()) return;
                    if (!tab.playerIframeSeen) {
                        tab.playerIframeSeen = true;
                        Log.i("SidiPlayer", "PLAYER_IFRAME_CREATED " + src);
                    }
                    markPlayerReady(tab, web, "iframe", src);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void applySavedAdRules(WebView web) {
        if (web == null || adRemovalStore == null) return;
        String current = web.getUrl();
        if (current == null || current.isEmpty()) return;
        String host;
        try {
            host = Uri.parse(current).getHost();
        } catch (Throwable ignored) {
            return;
        }
        if (host == null || host.isEmpty()) return;
        List<AdRemovalStore.Rule> rules = adRemovalStore.forHost(host);
        if (rules.isEmpty()) return;
        JSONArray encoded = new JSONArray();
        for (AdRemovalStore.Rule rule : rules) {
            JSONObject o = new JSONObject();
            try {
                o.put("id", rule.id);
                o.put("selector", rule.selector);
                o.put("tag", rule.tag);
                o.put("href", rule.href);
                o.put("text", rule.text);
                encoded.put(o);
            } catch (Throwable ignored) {}
        }
        String js = "(function(){try{var rules=" + encoded + ";" +
                "function playerUrl(u){u=String(u||'').toLowerCase();return /stravers\\.live|bulkikim|villybizy|alloha|kodik|voidboost|videocdn|collaps|hdvb|nextembed\\.ws|shortener2\\.link|videotoday\\.site|obrut\\.show/.test(u);}" +
                "function prot(e){if(!e)return true;var t=(e.tagName||'').toLowerCase();if(t==='html'||t==='body'||t==='video'||t==='audio')return true;" +
                "try{if(e.matches('.kinobox,.kinobox__iframeWrapper,.kinobox_iframe_container')){var fs=e.querySelectorAll('iframe[src]');for(var wi=0;wi<fs.length;wi++){if(playerUrl(fs[wi].src||fs[wi].getAttribute('src')||''))return true;}return false;}}catch(x){}" +
                "if(t==='iframe'){return playerUrl(e.src||e.getAttribute('src')||'');}try{if(e.closest('video,audio'))return true;var fs=e.querySelectorAll('iframe[src]');for(var fi=0;fi<fs.length;fi++){if(playerUrl(fs[fi].src||fs[fi].getAttribute('src')||''))return true;}}catch(x){}return false;}" +
                "function hide(e,id){if(!e||prot(e))return 0;e.setAttribute('data-sidi-manual-hidden','1');e.setAttribute('data-sidi-manual-rule',id||'');e.style.setProperty('display','none','important');e.style.setProperty('visibility','hidden','important');e.style.setProperty('pointer-events','none','important');return 1;}" +
                "function norm(v){return String(v||'').replace(/\\s+/g,' ').trim();}" +
                "function hrefOf(a){try{return new URL(a.getAttribute('href')||a.href||'',location.href).href;}catch(x){return a.href||'';}}" +
                "function fixedAncestor(e){var n=e,best=e,i=0;while(n&&n!==document.body&&i<5){if(prot(n))break;try{var c=getComputedStyle(n),r=n.getBoundingClientRect(),area=r.width*r.height,screen=innerWidth*innerHeight;if((c.position==='fixed'||c.position==='sticky')&&r.width>20&&r.height>20&&area<screen*0.8)return n;if(r.width>20&&r.height>20&&area<screen*0.55)best=n;}catch(x){}n=n.parentElement;i++;}return best;}" +
                "function apply(){var total=0;rules.forEach(function(r){var hit=0;try{document.querySelectorAll(r.selector).forEach(function(e){hit+=hide(e,r.id);});}catch(x){}" +
                "if(!hit&&r.href){try{document.querySelectorAll('a[href]').forEach(function(a){if(hrefOf(a)===r.href){var e=fixedAncestor(a);hit+=hide(e,r.id);}});}catch(x){}}" +
                "if(!hit&&r.text&&r.text.length>=4){try{var tags=r.tag?document.getElementsByTagName(r.tag):document.querySelectorAll('a,button,div,span');for(var i=0;i<tags.length&&i<2500;i++){var e=tags[i];if(norm(e.innerText)===r.text){hit+=hide(fixedAncestor(e),r.id);if(hit)break;}}}catch(x){}}total+=hit;});return total;}" +
                "var total=apply();" +
                "return total;}catch(e){return -1;}})();";
        try {
            final String ruleHost = host;
            final int ruleCount = rules.size();
            web.evaluateJavascript(js, result -> Log.i("SidiAdPicker",
                    "MANUAL_AD_RULES_APPLIED host=" + ruleHost + " rules=" + ruleCount + " hidden=" + result));
        } catch (Throwable ignored) {}
    }


    private void startAdRemovalMode(BrowserTab tab) {
        if (tab == null || tab.webView == null) {
            toast("Сначала откройте фильм или сериал");
            return;
        }
        for (BrowserTab t : tabs) {
            if (t.adPickerButton != null) t.adPickerButton.setText("Убрать рекламу");
        }
        adRemovalMode = true;
        adPickerPromptOpen = false;
        if (tab.adPickerButton != null) tab.adPickerButton.setText("Отмена");
        // 13.14: no full-screen bm0 overlay (broke Play). Localized hit-catchers over ad candidates.
        activateAdPickerRuntime(tab.webView, "mode-start");
        autoHideKnownAdPopups(tab.webView, "mode-start");
        handler.removeCallbacks(adPickerPollRunnable);
        handler.postDelayed(adPickerPollRunnable, 700L);
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Log.i("SidiAdPicker", "PICKER_INSTALL result=\"document-start+hit-catchers+no-auto-propose\"");
            toast("Режим выбора: кликни по жёлтой рамке на баннере. Авто-выбор выключен (плеер не трогаем).");
            return;
        }
        String js = "(function(){try{" +
                "if(window.__sidiAdPickerCancel)window.__sidiAdPickerCancel();" +
                "var root=document.documentElement||document.body;if(!root)return 'error:no-root';" +
                "var box=document.createElement('div');box.id='__sidi_ad_picker_box_133';box.style.cssText='position:fixed;display:none;pointer-events:none;z-index:2147483646;border:4px solid #ffcc00;background:rgba(255,204,0,.08);box-sizing:border-box';" +
                "var ov=document.createElement('div');ov.id='__sidi_ad_picker_overlay_133';ov.style.cssText='position:fixed;left:0;top:0;width:100vw;height:100vh;z-index:2147483647;background:rgba(0,0,0,.01);cursor:crosshair;pointer-events:auto';root.appendChild(box);root.appendChild(ov);" +
                "var current=null;" +
                "function playerUrl(u){u=String(u||'').toLowerCase();return /stravers\\.live|bulkikim|villybizy|alloha|kodik|voidboost|videocdn|collaps|hdvb|nextembed\\.ws|shortener2\\.link|videotoday\\.site|obrut\\.show/.test(u);}" +
                "function adUrl(u){u=String(u||'').toLowerCase();return /moviead55|buzzoola|vqserve|timing-js-menu|ussp\\.io|showjet|skcrtxr|ad[sx]?\\.|advert|banner|promo|telegram/.test(u);}" +
                "function protectedNode(e){if(!e)return true;var t=(e.tagName||'').toLowerCase();if(t==='html'||t==='body'||t==='video'||t==='audio')return true;try{if(e.matches('.kinobox,.kinobox__iframeWrapper,.kinobox_iframe_container')){var fs=e.querySelectorAll('iframe[src]');for(var wi=0;wi<fs.length;wi++){if(playerUrl(fs[wi].src||fs[wi].getAttribute('src')||''))return true;}return false;}}catch(x){}if(t==='iframe'){var src=e.src||e.getAttribute('src')||'';if(adUrl(src))return false;return playerUrl(src);}try{if(e.closest('video,audio'))return true;var fs=e.querySelectorAll('iframe[src]');for(var fi=0;fi<fs.length;fi++){if(playerUrl(fs[fi].src||fs[fi].getAttribute('src')||''))return true;}}catch(x){}return false;}" +
                "function under(x,y){var old=ov.style.pointerEvents;ov.style.pointerEvents='none';box.style.display='none';var a=[];try{a=document.elementsFromPoint?document.elementsFromPoint(x,y):[document.elementFromPoint(x,y)];}catch(z){}ov.style.pointerEvents=old||'auto';for(var i=0;i<a.length;i++){var e=a[i];if(e&&e!==ov&&e!==box)return e;}return null;}" +
                "function visualAt(x,y,raw){try{var all=(document.body||document.documentElement).querySelectorAll('*'),best=null,bestScore=-1e9,screen=Math.max(1,innerWidth*innerHeight),lim=Math.min(all.length,5000);for(var i=0;i<lim;i++){var e=all[i];if(!e||e===ov||e===box)continue;var tag=String(e.tagName||'').toLowerCase();if(!tag||/^(html|body|script|style|meta|link|video|audio|source)$/.test(tag))continue;var r=e.getBoundingClientRect();if(!r||r.width<18||r.height<18||x<r.left||x>r.right||y<r.top||y>r.bottom)continue;var c=getComputedStyle(e);if(!c||c.display==='none'||c.visibility==='hidden'||parseFloat(c.opacity||'1')<.02)continue;var area=r.width*r.height;if(area>screen*.96)continue;var meta=String((e.id||'')+' '+(e.className||'')).toLowerCase(),adish=/ad|advert|banner|promo|popup|overlay|preroll|vast|vpaid|sponsor|allplay|creative/.test(meta),pn=c.pointerEvents==='none',pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';var ext='';try{var a=e.closest&&e.closest('a[href]');if(!a&&e.querySelector)a=e.querySelector('a[href]');ext=a&&a.href&&!playerUrl(a.href)?a.href:'';}catch(z){}var hasVideo=false;try{hasVideo=!!(e.querySelector&&e.querySelector('video,audio'));}catch(z){}if(hasVideo&&!adish&&!ext)continue;if(protectedNode(e)&&!adish&&!ext&&!pn)continue;var zi=parseInt(c.zIndex,10);if(!isFinite(zi))zi=0;var score=(adish?5200:0)+(ext?3800:0)+(pn?2600:0)+(pos?1400:0)+Math.min(1400,Math.max(0,zi)/4);var frac=area/screen;if(frac>.025&&frac<.82)score+=900-Math.abs(frac-.30)*700;if(raw&&e===raw)score-=1200;if(score>bestScore){best=e;bestScore=score;}}return best?choose(best):choose(raw);}catch(z){return choose(raw);}}" +
                "function choose(e){if(!e)return null;try{var known=e.closest('#movie_video,#tgWrapper,#TopAdMb,.adDown,.topAdPad,.adlook-pc-wrapper,.adlook-mob-wrapper,.adlk-sticky,.adlk-content,.adlk-player-host,[id^=ad-element],[class^=rb-adman-]');if(known&&!protectedNode(known))return known;}catch(x){}if(protectedNode(e))return null;var screen=innerWidth*innerHeight,startArea=0;try{var sr=e.getBoundingClientRect();startArea=Math.max(1,sr.width*sr.height);}catch(x){}var n=e,best=e,bestArea=startArea,i=0;while(n&&n!==document.body&&i<8){if(n!==e&&protectedNode(n))break;try{var c=getComputedStyle(n),r=n.getBoundingClientRect(),area=r.width*r.height;if(r.width>20&&r.height>20&&area<screen*.72){if((c.position==='fixed'||c.position==='sticky')&&area>startArea*1.15)return n;if(area>bestArea&&area<=Math.max(startArea*18,screen*.48)){best=n;bestArea=area;}}}catch(x){}n=n.parentElement;i++;}return best;}" +
                "function show(e){current=e;if(!e){box.style.display='none';return;}try{var r=e.getBoundingClientRect();box.style.left=Math.max(0,r.left)+'px';box.style.top=Math.max(0,r.top)+'px';box.style.width=Math.max(2,r.width)+'px';box.style.height=Math.max(2,r.height)+'px';box.style.display='block';}catch(x){box.style.display='none';}}" +
                "function cssEsc(v){try{return CSS.escape(v);}catch(x){return String(v).replace(/[^a-zA-Z0-9_-]/g,'\\\\$&');}}" +
                "function attr(name,val){return '['+name+'='+JSON.stringify(String(val))+']';}" +
                "function selector(e){if(e.id&&e.id.length<100)return '#'+cssEsc(e.id);if((e.tagName||'').toLowerCase()==='iframe'&&e.getAttribute('src')){var q='iframe'+attr('src',e.getAttribute('src'));try{if(document.querySelectorAll(q).length<=2)return q;}catch(x){}}var attrs=['data-testid','data-id','data-ad','aria-label','title'];for(var ai=0;ai<attrs.length;ai++){var av=e.getAttribute&&e.getAttribute(attrs[ai]);if(av&&av.length<120){var q=e.tagName.toLowerCase()+attr(attrs[ai],av);try{if(document.querySelectorAll(q).length<=4)return q;}catch(x){}}}if(e.tagName==='A'&&e.getAttribute('href')){var q='a'+attr('href',e.getAttribute('href'));try{if(document.querySelectorAll(q).length<=4)return q;}catch(x){}}var parts=[],n=e,depth=0;while(n&&n.nodeType===1&&depth<7&&n!==document.body){var part=n.tagName.toLowerCase();var cs=[];try{Array.prototype.forEach.call(n.classList,function(c){if(cs.length<3&&c.length<40&&/^[a-zA-Z_-][a-zA-Z0-9_-]*$/.test(c)&&!/[0-9a-f]{8,}/i.test(c))cs.push(c);});}catch(x){}if(cs.length)part+='.'+cs.map(cssEsc).join('.');else if(n.parentElement){var same=Array.prototype.filter.call(n.parentElement.children,function(x){return x.tagName===n.tagName;});if(same.length>1)part+=':nth-of-type('+(same.indexOf(n)+1)+')';}parts.unshift(part);try{if(document.querySelectorAll(parts.join(' > ')).length===1)break;}catch(x){}n=n.parentElement;depth++;}return parts.join(' > ');}" +
                "function hrefFor(e){try{if((e.tagName||'').toLowerCase()==='iframe')return new URL(e.getAttribute('src')||e.src||'',location.href).href;var a=e.tagName==='A'?e:e.querySelector('a[href]');return a?new URL(a.getAttribute('href')||a.href||'',location.href).href:'';}catch(x){return '';}}" +
                "function move(ev){var raw=under(ev.clientX,ev.clientY),e=visualAt(ev.clientX,ev.clientY,raw);show(e);ev.preventDefault();}" +
                "function down(ev){ev.preventDefault();ev.stopPropagation();if(ev.stopImmediatePropagation)ev.stopImmediatePropagation();}" +
                "function click(ev){ev.preventDefault();ev.stopPropagation();if(ev.stopImmediatePropagation)ev.stopImmediatePropagation();var raw=under(ev.clientX,ev.clientY),e=visualAt(ev.clientX,ev.clientY,raw);show(e);if(!e){try{SidiAdPicker.rejected('protected:'+String((under(ev.clientX,ev.clientY)||{}).tagName||''));}catch(x){}return false;}var sel=selector(e);if(!sel)return false;var token='p'+Date.now().toString(36)+Math.random().toString(36).slice(2);e.setAttribute('data-sidi-picker-token',token);var txt=String(e.innerText||e.getAttribute('title')||e.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim().slice(0,160);try{SidiAdPicker.candidate((location.hostname||'')+' selector='+sel+' tag='+(e.tagName||'')+' pe='+(getComputedStyle(e).pointerEvents||''));}catch(x){}try{SidiAdPicker.pick(location.hostname||'',sel,token,e.tagName||'',hrefFor(e),txt);}catch(x){}return false;}" +
                "ov.addEventListener('mousemove',move,true);ov.addEventListener('pointermove',move,true);ov.addEventListener('mousedown',down,true);ov.addEventListener('pointerdown',down,true);ov.addEventListener('click',click,true);ov.addEventListener('contextmenu',down,true);" +
                "window.__sidiAdPickerCancel=function(){try{ov.remove();box.remove();}catch(x){}window.__sidiAdPickerCancel=null;return true;};" +
                "return 'installed-overlay';}catch(e){return 'error:'+String(e&&e.message||e);}})();";
        try {
            tab.webView.evaluateJavascript(js, value -> {
                Log.i("SidiAdPicker", "PICKER_INSTALL result=" + value);
                if (value != null && value.contains("error:")) {
                    stopAdRemovalMode(tab, false);
                    toast("Не удалось включить выбор рекламы");
                }
            });
            toast("Режим выбора включён: наведи на рекламу и нажми OK. Переход по баннеру заблокирован.");
        } catch (Throwable error) {
            stopAdRemovalMode(tab, false);
            toast("Не удалось включить выбор рекламы");
        }
    }


    private void stopAdRemovalMode(BrowserTab tab, boolean notify) {
        adRemovalMode = false;
        adPickerPromptOpen = false;
        handler.removeCallbacks(adPickerPollRunnable);
        for (BrowserTab t : tabs) {
            if (t.adPickerButton != null) t.adPickerButton.setText("Убрать рекламу");
        }
        if (tab != null && tab.webView != null) {
            injectPlayerSafeLayout(tab.webView);
            restorePlayerViewport(tab.webView);
        }
        WebView web = tab == null ? null : tab.webView;
        if (web != null) {
            try {
                web.evaluateJavascript("(function(){try{" +
                        "if(window.__sidiAdPickerCancel)window.__sidiAdPickerCancel();" +
                        "if(window.__sidiPickerHitStop)window.__sidiPickerHitStop();" +
                        "return true;}catch(e){return false;}})();", null);
            } catch (Throwable ignored) {}
        }
        if (notify) toast("Режим удаления рекламы выключен");
    }

    /**
     * Scan ad candidates, draw yellow frame, and place localized hit-catchers over them
     * (so cross-origin iframe banners receive clicks in the parent frame). Never covers Stravers.
     */
    private void activateAdPickerRuntime(WebView web, String reason) {
        if (web == null) return;
        final String why = reason == null ? "" : reason;
        String js = "(function(){try{" +
                "var reason=" + JSONObject.quote(why) + ";" +
                "try{SidiAdPicker.candidate('runtime-activate:'+reason+' host='+(location.hostname||''));}catch(x){}" +
                "function playerUrl(u){u=String(u||'').toLowerCase();return /stravers\\.live|bulkikim|villybizy|alloha|kodik|voidboost|videocdn|collaps|hdvb|nextembed\\.ws|shortener2\\.link|videotoday\\.site|obrut\\.show/.test(u);}" +
                "function adHost(u){u=String(u||'').toLowerCase();return /traffaret|adriver|moviead55|buzzoola|video-mech|betweendigital|vqserve|ussp\\.io|showjet|skcrtxr|go\\/bn|prestreamnos|adlook|bidster/.test(u);}" +
                "function isPlayerFrame(e){try{return e&&String(e.tagName||'').toLowerCase()==='iframe'&&playerUrl(e.src||e.getAttribute('src')||'');}catch(x){return false;}}" +
                "function hasPlayer(e){if(!e||!e.querySelector)return false;try{var fs=e.querySelectorAll('iframe[src]');for(var i=0;i<fs.length;i++){if(playerUrl(fs[i].src||''))return true;}}catch(x){}return false;}" +
                "function isPickerChrome(e){try{if(!e)return true;var id=String(e.id||'');if(id.indexOf('__sidi_')===0)return true;if(e.getAttribute&&e.getAttribute('data-sidi-hit'))return true;var cls=String(e.className||'').toLowerCase();if(/kinobox|kbt_|allplay/.test(cls)||/kinobox|kbt_|allplay/.test(id))return true;if(e.closest&&e.closest('.kinobox,.kinobox__iframeWrapper,.kinobox_iframe_container,.kinobox_loader,[id^=__sidi_]'))return true;}catch(x){}return false;}" +
                /* 13.17: clear ad iframe is never protected even if visually near player; hasPlayer only for true player descendants */
                "function protectedNode(e){if(!e)return true;if(isPickerChrome(e))return true;var t=(e.tagName||'').toLowerCase();if(t==='iframe'){var src=e.src||e.getAttribute('src')||'';if(adHost(src))return false;if(playerUrl(src))return true;}if(hasPlayer(e)&&t!=='iframe')return true;if(t==='html'||t==='body'||t==='video'||t==='audio')return true;try{if(e.closest('video,audio'))return true;}catch(x){}return false;}" +
                "function overlayRoot(e){var n=e,best=e,screen=Math.max(1,innerWidth*innerHeight),i=0;while(n&&n!==document.body&&i<8){if(n!==e&&protectedNode(n))break;try{var c=getComputedStyle(n),r=n.getBoundingClientRect(),a=r.width*r.height,cls=String((n.id||'')+' '+(n.className||'')).toLowerCase();var adish=/ad|advert|banner|promo|popup|overlay|preroll|vast|vpaid|bn_|bn-/.test(cls);var close=!!(n.querySelector&&n.querySelector('[class*=close],[class*=cross],[aria-label*=close],[aria-label*=закры]'));if(r.width>24&&r.height>24&&a<screen*.95&&(adish||close||(c.position==='fixed'||c.position==='sticky'||c.position==='absolute')))best=n;}catch(x){}n=n.parentElement;i++;}return best;}" +
                "function esc(v){try{return CSS.escape(v);}catch(x){return String(v).replace(/[^a-zA-Z0-9_-]/g,'\\\\$&');}}" +
                "function attr(n,v){return '['+n+'=\\\"'+String(v).replace(/\\\\/g,'\\\\\\\\').replace(/\\\"/g,'\\\\\\\"')+'\\\"]';}" +
                "function selector(e){try{if(!e)return '';if(e.id&&e.id.length<100)return '#'+esc(e.id);if((e.tagName||'').toLowerCase()==='iframe'&&e.getAttribute('src')){var q='iframe'+attr('src',e.getAttribute('src'));if(document.querySelectorAll(q).length<=3)return q;}var cls=[];try{Array.prototype.forEach.call(e.classList,function(c){if(cls.length<2&&/ad|banner|popup|overlay|vpaid|vast|promo/i.test(c))cls.push(c);});}catch(z){}if(cls.length)return e.tagName.toLowerCase()+'.'+cls.map(esc).join('.');var id=e.id||'';var m=id.match(/^([A-Za-z]{4,8})[0-9]{12,}$/);if(m)return '[id^=\\\"'+esc(m[1])+'\\\"]';}catch(x){}return e?(e.tagName||'').toLowerCase():'';}" +
                "function hrefFor(e){try{if((e.tagName||'').toLowerCase()==='iframe')return new URL(e.getAttribute('src')||e.src||'',location.href).href;var a=e.querySelector&&e.querySelector('a[href]');return a?a.href:'';}catch(x){return '';}}" +
                "function visible(e){try{var c=getComputedStyle(e),r=e.getBoundingClientRect();if(!r||r.width<20||r.height<20)return false;if(c.display==='none'||c.visibility==='hidden'||parseFloat(c.opacity||'1')<.05)return false;return true;}catch(x){return false;}}" +
                "var screen=Math.max(1,innerWidth*innerHeight),best=null,bestScore=-1,cands=[];" +
                "function consider(e,bonus){if(!e||protectedNode(e)||isPickerChrome(e)||e.getAttribute('data-sidi-manual-hidden'))return;try{if(!visible(e))return;var r=e.getBoundingClientRect(),c=getComputedStyle(e);var area=r.width*r.height;if(area>screen*.97)return;var src=(e.tagName||'').toLowerCase()==='iframe'?(e.src||e.getAttribute('src')||''):'';var meta=String((e.id||'')+' '+(e.className||'')+' '+src).toLowerCase();if(/kinobox|kbt_|allplay|__sidi_/.test(meta))return;var adish=/ad|advert|banner|promo|popup|overlay|preroll|vast|vpaid|traffaret|adriver|moviead55|buzzoola|bn_|bn-/.test(meta)||adHost(src);var randomId=/^[A-Za-z]{5}[0-9]{10,22}$/.test(String(e.id||''));var pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';var zi=parseInt(c.zIndex,10)||0;var corner=(r.left<120&&r.bottom>innerHeight*0.45)||(r.right>innerWidth-120&&r.bottom>innerHeight*0.45);if(!adish&&!adHost(src)&&!randomId)return;var score=(bonus||0)+(adish||randomId?5000:0)+(adHost(src)?4500:0)+(pos?2000:0)+(corner?2000:0)+Math.min(2000,zi)+(area/screen>0.08?1500:(area/screen>0.012?1100:0));var root=e;if(!randomId){root=overlayRoot(e);if(root&&(protectedNode(root)||isPickerChrome(root)||hasPlayer(root)))root=e;}if(protectedNode(root)||isPickerChrome(root)||hasPlayer(root)||isPlayerFrame(root))return;cands.push({e:root||e,score:score,sel:selector(root||e),clearAd:!!(adHost(src)||randomId||/#movie_video|#tgWrapper|topAdPad|rmp-/.test(meta))});if(score>bestScore){best=root||e;bestScore=score;}}catch(x){}}" +
                "try{document.querySelectorAll('iframe[src]').forEach(function(f){if(isPlayerFrame(f)||!adHost(f.src||f.getAttribute('src')||''))return;consider(f,5000);});}catch(x){}" +
                "try{document.querySelectorAll('#movie_video,#tgWrapper,#TopAdMb,.topAdPad,.rmp-vpaid-container,.rmp-ad-container,.rmp-ad-overlay').forEach(function(e){consider(e,3500);});}catch(x){}" +
                "try{document.querySelectorAll('[id]').forEach(function(e){if(/^[A-Za-z]{5}[0-9]{10,22}$/.test(e.id||''))consider(e,4800);});}catch(x){}" +
                "try{var all=document.querySelectorAll('div,section,aside');for(var i=0;i<all.length&&i<3000;i++){var e=all[i],c=getComputedStyle(e);if(c.position!=='fixed'&&c.position!=='sticky'&&c.position!=='absolute')continue;consider(e,700);}}catch(x){}" +
                "cands.sort(function(a,b){return b.score-a.score;});" +
                "var uniq=[],seen={};cands.forEach(function(c){if(!c.e||seen[c.sel])return;seen[c.sel]=1;uniq.push(c);});cands=uniq;" +
                "if(reason!=='poll'){for(var i=0;i<Math.min(8,cands.length);i++){try{SidiAdPicker.candidate('scan#'+i+' score='+Math.round(cands[i].score)+' sel='+cands[i].sel+' host='+(location.hostname||''));}catch(x){}}}" +
                /* yellow box — highlight only, never selectable as a rule target */
                "var box=document.getElementById('__sidi_picker_hl_1314');" +
                "if(!box){box=document.createElement('div');box.id='__sidi_picker_hl_1314';box.style.cssText='position:fixed;display:none;pointer-events:none;z-index:2147483646;border:4px solid #ffcc00;background:rgba(255,204,0,.12);box-sizing:border-box';(document.documentElement||document.body).appendChild(box);}" +
                "function showHl(e){if(!e||isPickerChrome(e)){box.style.display='none';return;}try{var r=e.getBoundingClientRect();box.style.left=Math.max(0,r.left)+'px';box.style.top=Math.max(0,r.top)+'px';box.style.width=Math.max(2,r.width)+'px';box.style.height=Math.max(2,r.height)+'px';box.style.display='block';}catch(x){box.style.display='none';}}" +
                "if(best&&!isPickerChrome(best)&&!hasPlayer(best))showHl(best);else box.style.display='none';" +
                /* localized hit-catchers ONLY over clear ad iframes / random-id overlays — never player */
                "var layer=document.getElementById('__sidi_picker_hits_1314');" +
                "if(!layer){layer=document.createElement('div');layer.id='__sidi_picker_hits_1314';layer.style.cssText='position:fixed;left:0;top:0;width:0;height:0;z-index:2147483645;pointer-events:none';(document.documentElement||document.body).appendChild(layer);}" +
                "while(layer.firstChild)layer.removeChild(layer.firstChild);" +
                "function propose(e,sel,src){if(!e||!sel||isPickerChrome(e)||hasPlayer(e)||protectedNode(e)){try{SidiAdPicker.candidate('propose-skip unsafe sel='+sel);}catch(z){}return;}var token='h'+Date.now().toString(36)+Math.random().toString(36).slice(2);try{e.setAttribute('data-sidi-picker-token',token);}catch(z){}var txt=String(e.innerText||e.getAttribute('title')||e.getAttribute('aria-label')||'').replace(/\\s+/g,' ').trim().slice(0,160);try{SidiAdPicker.candidate(src+' sel='+sel+' tag='+(e.tagName||''));}catch(z){}try{SidiAdPicker.pick(location.hostname||'',sel,token,e.tagName||'',hrefFor(e),txt);}catch(z){}" +
                "var tries=0,t=setInterval(function(){tries++;var d=0;try{d=SidiAdPicker.decision(token);}catch(z){}if(d===1){clearInterval(t);try{e.style.setProperty('display','none','important');e.style.setProperty('visibility','hidden','important');e.style.setProperty('pointer-events','none','important');e.setAttribute('data-sidi-manual-hidden','1');}catch(z){}try{var st=document.getElementById('__sidi_manual_css_1310');if(!st){st=document.createElement('style');st.id='__sidi_manual_css_1310';(document.head||document.documentElement).appendChild(st);}st.sheet.insertRule(sel+'{display:none!important;visibility:hidden!important;pointer-events:none!important}',st.sheet.cssRules.length);}catch(z){}showHl(null);try{SidiAdPicker.candidate('hit-hide-ok sel='+sel);}catch(z){}}else if(d===-1||tries>300){clearInterval(t);}},100);}" +
                "cands.sort(function(a,b){return (b.score||0)-(a.score||0);});var placed=0;for(var hi=0;hi<cands.length&&placed<1;hi++){var c=cands[hi];if(!c.e||!c.clearAd||c.score<4000||protectedNode(c.e)||isPickerChrome(c.e)||isPlayerFrame(c.e)||hasPlayer(c.e))continue;var isRid=/^[A-Za-z]{5}[0-9]{10,22}$/.test(String(c.e.id||''));var isAdIframe=(String(c.e.tagName||'').toLowerCase()==='iframe');if(!isRid&&!isAdIframe)continue;try{var r=c.e.getBoundingClientRect();if(r.width<24||r.height<24||r.width*r.height>screen*0.85)continue;var hit=document.createElement('div');hit.setAttribute('data-sidi-hit','1');hit.style.cssText='position:fixed;left:'+r.left+'px;top:'+r.top+'px;width:'+r.width+'px;height:'+r.height+'px;z-index:2147483645;pointer-events:auto;cursor:crosshair;background:rgba(255,204,0,0.08);outline:2px solid #fc0;';hit.title='Клик = убрать рекламу';(function(target,sel,score){hit.addEventListener('click',function(ev){ev.preventDefault();ev.stopPropagation();if(ev.stopImmediatePropagation)ev.stopImmediatePropagation();showHl(target);propose(target,sel,'hit-click score='+Math.round(score));},true);hit.addEventListener('pointerdown',function(ev){ev.preventDefault();ev.stopPropagation();},true);})(c.e,c.sel,c.score);layer.appendChild(hit);placed++;}catch(z){}}" +
                "window.__sidiPickerHitStop=function(){try{if(layer&&layer.parentNode)layer.parentNode.removeChild(layer);}catch(z){}try{if(box)box.style.display='none';}catch(z){}window.__sidiLastProposedSel=null;return true;};" +
                /* 13.15/13.16: NEVER auto-propose dialog (player false positives). Auto-HIDE only clear random-id / ad-iframe. */
                "var autoHid=0;cands.forEach(function(c){if(!c.clearAd||!c.e||protectedNode(c.e)||isPickerChrome(c.e)||hasPlayer(c.e)||isPlayerFrame(c.e))return;try{var rid=String(c.e.id||'');c.e.setAttribute('data-sidi-known-ad','picker-clear');c.e.style.cssText='display:none!important;visibility:hidden!important;pointer-events:none!important;opacity:0!important;width:0!important;height:0!important;';if(/^[A-Za-z]{5}[0-9]{10,22}$/.test(rid)||c.clearAd){try{if(c.e.parentNode)c.e.parentNode.removeChild(c.e);}catch(z){}}autoHid++;try{SidiAdPicker.candidate('auto-hide-clear sel='+c.sel);}catch(z){}}catch(z){}});" +
                "if(autoHid)showHl(null);" +
                "try{SidiAdPicker.candidate('no-auto-propose reason='+reason+' cands='+cands.length+' hits='+placed+' autoHid='+autoHid+' best='+Math.round(bestScore));}catch(x){}" +
                "if(!cands.length){try{SidiAdPicker.candidate('scan-empty host='+(location.hostname||''));}catch(x){}}" +
                "return 'scanned:'+cands.length+':hits='+placed+':autoHid='+autoHid+':best='+Math.round(bestScore)+':no-auto';" +
                "}catch(e){try{SidiAdPicker.rejected('runtime-error:'+String(e&&e.message||e));}catch(x){}return 'error';}})();";
        try {
            web.evaluateJavascript(js, value -> Log.i("SidiAdPicker", "PICKER_RUNTIME reason=" + why + " result=" + value));
        } catch (Throwable error) {
            Log.w("SidiAdPicker", "PICKER_RUNTIME_FAILED reason=" + why, error);
        }
    }

    private void pickAdAtNormalizedPoint(WebView web, float nx, float ny) {
        if (web == null) return;
        String js = "(function(){try{" +
                "var x=Math.round((" + nx + ")*innerWidth),y=Math.round((" + ny + ")*innerHeight);" +
                "try{SidiAdPicker.candidate('fullscreen-point x='+x+' y='+y+' host='+(location.hostname||''));}catch(z){}" +
                "function playerUrl(u){u=String(u||'').toLowerCase();return /stravers\\.live|bulkikim|villybizy|alloha|kodik|voidboost|videocdn|collaps|hdvb|nextembed\\.ws|shortener2\\.link|videotoday\\.site|obrut\\.show/.test(u);}" +
                "function adHost(u){u=String(u||'').toLowerCase();return /traffaret|adriver|moviead55|buzzoola|video-mech|betweendigital|vqserve|go\\/bn/.test(u);}" +
                "function known(e){try{return e&&e.closest?e.closest('#movie_video,#tgWrapper,.topAdPad,.rmp-vpaid-container,.rmp-ad-container,iframe[src*=traffaret],iframe[src*=adriver],iframe[src*=moviead55],[class*=popup],[class*=banner]'):null;}catch(x){return null;}}" +
                "function protectedNode(e){if(!e)return true;if(known(e))return false;var t=(e.tagName||'').toLowerCase();if(t==='html'||t==='body'||t==='video'||t==='audio')return true;if(t==='iframe'){var s=e.src||'';return adHost(s)?false:playerUrl(s);}try{if(e.closest('video,audio'))return true;}catch(x){}return false;}" +
                "var raw=document.elementFromPoint(x,y);" +
                "var e=known(raw)||raw;" +
                "while(e&&e!==document.body&&protectedNode(e))e=e.parentElement;" +
                "if(!e||protectedNode(e)){try{SidiAdPicker.rejected('fullscreen-no-ad:'+String((raw&&raw.tagName)||''));}catch(z){}return 'none';}" +
                "function esc(v){try{return CSS.escape(v);}catch(x){return String(v);}}" +
                "var sel=e.id?'#'+esc(e.id):((e.tagName||'').toLowerCase()==='iframe'&&e.getAttribute('src')?('iframe[src=\\\"'+String(e.getAttribute('src')).replace(/\\\"/g,'\\\\\\\"')+'\\\"]'):(e.tagName||'').toLowerCase());" +
                "var token='g'+Date.now().toString(36)+Math.random().toString(36).slice(2);e.setAttribute('data-sidi-picker-token',token);" +
                "var href='';try{href=(e.tagName||'').toLowerCase()==='iframe'?(e.src||''):'';}catch(z){}" +
                "try{SidiAdPicker.candidate('fullscreen-hit sel='+sel+' tag='+(e.tagName||''));}catch(z){}" +
                "try{SidiAdPicker.pick(location.hostname||'',sel,token,e.tagName||'',href,'');}catch(z){}" +
                "return sel;}catch(e){try{SidiAdPicker.rejected('fullscreen-error');}catch(z){}return 'error';}})();";
        try {
            web.evaluateJavascript(js, value -> Log.i("SidiAdPicker", "PICKER_FULLSCREEN_POINT result=" + value));
        } catch (Throwable ignored) {}
    }

    private void hideResumeAdContainers(WebView web) {
        if (web == null || adBlocker == null || !adBlocker.isPostPlaybackAdSuppress()) return;
        String js = "(function(){try{var n=0;['.rmp-vpaid-container','.rmp-ad-container','.rmp-ad-overlay','.rmp-vast-container','[class*=vpaid]','[class*=preroll]'].forEach(function(q){document.querySelectorAll(q).forEach(function(e){try{e.style.setProperty('display','none','important');e.style.setProperty('visibility','hidden','important');e.style.setProperty('pointer-events','none','important');n++;}catch(x){}});});return n;}catch(e){return -1;}})();";
        try {
            web.evaluateJavascript(js, value -> Log.i("SidiAdBlock", "AD_RESUME_DOM_HIDE hidden=" + value));
        } catch (Throwable ignored) {}
        autoHideKnownAdPopups(web, "resume-pause");
    }

    /** Auto-hide known popup/banner iframes without requiring «Убрать рекламу». Never touches Stravers/obrut player. */
    private void autoHideKnownAdPopups(WebView web, String reason) {
        if (web == null) return;
        ensureAlwaysOnAdPoll();
        String js = "(function(){try{" +
                "function playerUrl(u){u=String(u||'').toLowerCase();return /stravers\\.live|bulkikim|villybizy|alloha|kodik|voidboost|videocdn|collaps|hdvb|nextembed\\.ws|shortener2\\.link|videotoday\\.site|obrut\\.show/.test(u);}" +
                "function adUrl(u){u=String(u||'').toLowerCase();return /moviead55|buzzoola|video-mech|vqserve|traffaret|adriver|betweendigital|prestreamnos|ussp\\.io|showjet|skcrtxr|adlook|bidster|moe\\.video|alloviewroll|kinoad\\.click|ufouxbwn|wily-adv|cinemalook/.test(u);}" +
                "function isPlayerIframe(e){try{if(!e||String(e.tagName||'').toLowerCase()!=='iframe')return false;return playerUrl(e.src||e.getAttribute('src')||'');}catch(x){return false;}}" +
                "function isKinoboxShell(e){try{if(!e||!e.matches)return false;return e.matches('.kinobox,.kinobox__iframeWrapper,.kinobox_iframe_container,.kinobox_loader,[class*=\\\"kinobox\\\"],[class*=\\\"kbt_\\\"],[class*=\\\"allplay\\\"]');}catch(x){return false;}}" +
                "function protectedPlayer(e){try{if(!e)return true;if(isPlayerIframe(e))return true;if(isKinoboxShell(e))return true;var tx=String(e.textContent||'');if(/Продолжить просмотр|Вы остановились/.test(tx)&&tx.length<400)return true;var t=String(e.tagName||'').toLowerCase();if(t==='video'||t==='audio'){var p=e.parentElement;if(p&&(isKinoboxShell(p)||(p.closest&&p.closest('.kinobox,.kinobox_iframe_container'))))return true;}}catch(x){}return false;}" +
                "function hide(e,why){if(!e||protectedPlayer(e))return 0;try{if(e.querySelector&&e.querySelector('iframe[src*=stravers],iframe[src*=obrut],iframe[src*=alloha],iframe[src*=kodik],iframe[src*=voidboost]')&&!adLike(e))return 0;var rid=String(e.id||'');var forceRid=/^[A-Za-z]{5}[0-9]{10,22}$/.test(rid)||/^(nTgQB|odPgP|mTTEi|nGRXI|dcXeb|Acquk|UDkzH|KfvQY)/.test(rid);e.setAttribute('data-sidi-known-ad',why||'1');try{e.style.setProperty('display','none','important');e.style.setProperty('visibility','hidden','important');e.style.setProperty('pointer-events','none','important');e.style.setProperty('opacity','0','important');e.style.setProperty('width','0','important');e.style.setProperty('height','0','important');e.style.setProperty('overflow','hidden','important');e.style.setProperty('left','-99999px','important');e.style.setProperty('position','fixed','important');}catch(z){}try{e.setAttribute('hidden','true');}catch(z){}if(forceRid||why==='auto-random-id'||why==='auto-prefix'||why==='auto-close-popup'||why==='auto-close-root'||why==='auto-ona-overlay'||String(why||'').indexOf('moviead')>=0||String(why||'').indexOf('picker-clear')>=0){try{if(e.parentNode)e.parentNode.removeChild(e);}catch(z){}}return 1;}catch(x){return 0;}}" +
                "function hasClose(e){try{if(!e||!e.querySelector)return false;if(e.querySelector('[class*=close],[class*=cross],[class*=dismiss],[class*=Close],[aria-label*=close],[aria-label*=закры],[aria-label*=Close],[title*=close],[title*=закры],[title*=Close]'))return true;var nodes=e.querySelectorAll('div,span,button,a,i,svg');var lim=Math.min(nodes.length,80);for(var i=0;i<lim;i++){var n=nodes[i],t=String(n.textContent||'').replace(/\\s+/g,'');if(!t||t.length>3)continue;if(!/^(×|✕|✖|x|X|Х|х|╳)$/.test(t))continue;var r=n.getBoundingClientRect();if(r.width>=14&&r.width<=120&&r.height>=14&&r.height<=120)return true;} }catch(x){}return false;}" +
                "function adLike(e){try{var meta=String((e.id||'')+' '+(e.className||'')).toLowerCase();if(/ad|advert|banner|promo|popup|overlay|preroll|vast|vpaid|traffaret|adriver|bn_|bn-|moviead|buzzoola/.test(meta))return true;if(/^[A-Za-z]{5}[0-9]{10,22}$/.test(String(e.id||'')))return true;if(hasClose(e))return true;var hit=false;try{e.querySelectorAll('iframe[src]').forEach(function(f){var s=f.src||f.getAttribute('src')||'';if(adUrl(s)&&!playerUrl(s))hit=true;});}catch(z){}return hit;}catch(x){return false;}}" +
                "function isBannerRandomId(e){try{var v=e.id||'';if(!/^[A-Za-z]{5}[0-9]{10,22}$/.test(v)&&!/^(nTgQB|odPgP|mTTEi|nGRXI|dcXeb)/.test(v))return false;if(protectedPlayer(e)||isPlayerIframe(e)||isKinoboxShell(e))return false;return true;}catch(x){return false;}}" +
                "function isClosePopup(e){try{if(!e||protectedPlayer(e)||e.getAttribute('data-sidi-known-ad'))return false;var c=getComputedStyle(e);var pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';var z=parseInt(c.zIndex||'0',10)||0;if(!(pos||z>=100))return false;var r=e.getBoundingClientRect(),screen=Math.max(1,innerWidth*innerHeight),a=r.width*r.height;if(r.width<48||r.height<40||a<48*40||a>screen*0.97)return false;if(!hasClose(e)&&!adLike(e))return false;if(e.querySelector&&e.querySelector('iframe[src*=\\\"stravers\\\"],iframe[src*=\\\"obrut\\\"]')&&!adLike(e))return false;return true;}catch(x){return false;}}" +
                "function sweep(){var n=0,screen=Math.max(1,innerWidth*innerHeight);" +
                /* Ad iframes: hide even when nested under kinobox — moviead55 is never the main player. */
                "try{document.querySelectorAll('iframe[src]').forEach(function(f){var s=f.src||f.getAttribute('src')||'';if(playerUrl(s)||!adUrl(s)||isPlayerIframe(f))return;n+=hide(f,'auto-iframe');var p=f.parentElement,i=0;while(p&&p!==document.body&&i<5){try{if(protectedPlayer(p)&&!adLike(p))break;var c=getComputedStyle(p),r=p.getBoundingClientRect();var pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';var a=r.width*r.height;if((pos||adLike(p)||hasClose(p))&&a<screen*0.97&&a>40*30){n+=hide(p,'auto-iframe-wrap');break;}}catch(z){}p=p.parentElement;i++;}});}catch(x){}" +
                "try{['#movie_video','div.topAdPad','#tgWrapper','.rmp-vpaid-container','.rmp-ad-container','.rmp-ad-overlay','.rmp-vast-container','[id^=ad-element]','.adlook-pc-wrapper','.adlook-mob-wrapper','.adlk-sticky'].forEach(function(q){document.querySelectorAll(q).forEach(function(e){n+=hide(e,q);});});}catch(x){}" +
                "try{document.querySelectorAll('[id]').forEach(function(e){if(isBannerRandomId(e))n+=hide(e,'auto-random-id');});}catch(x){}" +
                "try{document.querySelectorAll('[id^=nTgQB],[id^=odPgP],[id^=mTTEi],[id^=nGRXI],[id^=dcXeb]').forEach(function(e){if(!protectedPlayer(e))n+=hide(e,'auto-prefix');});}catch(x){}" +
                "try{document.querySelectorAll('div,section,aside').forEach(function(e){try{if(e.getAttribute('data-sidi-known-ad')||protectedPlayer(e))return;var tx=String(e.innerText||e.textContent||'');if(!tx||tx.length>80)return;if(!/(она|Реклама|реклама)/.test(tx))return;if(!hasClose(e)&&tx.indexOf('\u00d7')<0&&tx.indexOf('\u2715')<0)return;var c=getComputedStyle(e),pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';var z=parseInt(c.zIndex||'0',10)||0;if(pos||z>=50||adLike(e))n+=hide(e,'auto-ona-overlay');}catch(z){}});}catch(x){}" +
                "try{document.querySelectorAll('div,section,aside,span,button,a').forEach(function(e){if(isClosePopup(e))n+=hide(e,'auto-close-popup');});}catch(x){}" +"try{var marks=[];document.querySelectorAll('div,span,button,a,i').forEach(function(n){var t=String(n.textContent||'').replace(/\\s+/g,'');if(!/^(×|✕|✖|x|X|Х|х|╳)$/.test(t))return;var r=n.getBoundingClientRect();if(r.width<12||r.width>140||r.height<12||r.height>140)return;marks.push(n);});marks.forEach(function(mark){var p=mark,best=null,i=0,screen=Math.max(1,innerWidth*innerHeight);while(p&&p!==document.body&&i<8){try{if(protectedPlayer(p))break;var txt=String(p.textContent||'');if(/Продолжить просмотр|Вы остановились|продолжить просмотр/i.test(txt))break;var c=getComputedStyle(p),r=p.getBoundingClientRect(),a=r.width*r.height;var pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';var z=parseInt(c.zIndex||'0',10)||0;if((pos||z>=50)&&a>20*20&&a<screen*0.98)best=p;if(adLike(p)||hasClose(p))best=p;}catch(z){}p=p.parentElement;i++;}if(best)n+=hide(best,'auto-close-root');else n+=hide(mark,'auto-close-mark');});}catch(x){}" +
                "try{var nodes=document.querySelectorAll('div,section,aside');for(var i=0;i<nodes.length&&i<2600;i++){var e=nodes[i];if(e.getAttribute('data-sidi-known-ad')||protectedPlayer(e))continue;var c=getComputedStyle(e);if(c.position!=='fixed'&&c.position!=='sticky'&&c.position!=='absolute')continue;var r=e.getBoundingClientRect(),a=r.width*r.height;if(r.width<50||r.height<36||a<50*36||a>screen*0.97)continue;var meta=String((e.id||'')+' '+(e.className||'')).toLowerCase();var adish=/ad|advert|banner|promo|popup|overlay|preroll|vast|vpaid|traffaret|adriver|bn_|bn-|moviead/.test(meta);var close=hasClose(e);var corner=(r.left<120&&r.bottom>innerHeight*0.45)||(r.right>innerWidth-120&&r.bottom>innerHeight*0.45);var center=r.left>innerWidth*0.15&&r.right<innerWidth*0.95&&r.top>40;var hasMedia=!!e.querySelector('iframe,img,a[target=_blank],video');if((adish||(close&&(a>screen*0.012||corner||center))||(corner&&hasMedia&&a>screen*0.01))&&!e.querySelector('iframe[src*=stravers],iframe[src*=obrut],iframe[src*=alloha],iframe[src*=kodik],iframe[src*=voidboost]'))n+=hide(e,adish?'auto-overlay':(close?'auto-close':'auto-corner'));}}catch(x){}return n;}" +
                "var n=sweep();" +
                "if(!window.__sidiAutoAdObs1321){window.__sidiAutoAdObs1321=true;var pending=false;" +
                "new MutationObserver(function(){if(pending)return;pending=true;setTimeout(function(){pending=false;try{sweep();}catch(z){}},120);})" +
                ".observe(document.documentElement||document,{childList:true,subtree:true,attributes:true,attributeFilter:['style','class','id']});" +
                "try{if(!window.__sidiAutoAdTick1321){window.__sidiAutoAdTick1321=setInterval(function(){try{sweep();}catch(z){}},550);}}catch(z){}}" +
                "return n;}catch(e){return -1;}})();";
        try {
            final String why = reason == null ? "" : reason;
            web.evaluateJavascript(js, value -> Log.i("SidiAdBlock", "AUTO_AD_HIDDEN reason=" + why + " count=" + value));
        } catch (Throwable ignored) {}
    }

    private void ensureAlwaysOnAdPoll() {
        handler.removeCallbacks(alwaysOnAdPollRunnable);
        handler.postDelayed(alwaysOnAdPollRunnable, 650L);
    }

    private void restorePlayerViewport(WebView web) {
        if (web == null) return;
        String js = "(function(){try{var wrap=document.querySelector('.kinobox__iframeWrapper,.kinobox_iframe_container');" +
                "var frame=null;document.querySelectorAll('iframe[src]').forEach(function(f){var s=(f.src||'').toLowerCase();if(/stravers\\.live|alloha|kodik|voidboost|videocdn|collaps|hdvb|obrut\\.show|nextembed\\.ws|shortener2\\.link|videotoday\\.site/.test(s))frame=f;});" +
                "if(!wrap&&frame)wrap=frame.parentElement;if(!wrap)return 'no-wrap';" +
                "try{wrap.scrollIntoView({block:'center',inline:'nearest'});}catch(x){}" +
                "var r=wrap.getBoundingClientRect();window.scrollTo({top:Math.max(0,(window.scrollY||0)+r.top-16),behavior:'auto'});" +
                "return JSON.stringify({top:Math.round(r.top),bottom:Math.round(r.bottom),scrollY:Math.round(window.scrollY||0)});}" +
                "catch(e){return 'error:'+String(e&&e.message||e);}})();";
        try {
            web.evaluateJavascript(js, value -> Log.i("SidiPlayerLayout", "PLAYER_VIEWPORT_RESTORE " + value));
        } catch (Throwable ignored) {}
    }

    private boolean isSafeManualSelection(String selector, String tag, String href) {
        if (selector == null) return false;
        String s = selector.trim().toLowerCase(Locale.US);
        String t = tag == null ? "" : tag.trim().toLowerCase(Locale.US);
        String u = href == null ? "" : href.trim().toLowerCase(Locale.US);
        if (s.isEmpty() || s.length() > 500) return false;
        if (s.equals("html") || s.equals("body") || s.equals("video") || s.equals("audio")
                || s.equals("div") || s.equals("span") || s.equals("a") || s.equals("iframe")
                || s.startsWith("video>") || s.startsWith("video ")) return false;
        // Never block player chrome / our picker UI (13.14 wrongly auto-proposed these).
        if (s.contains("kinobox") || s.contains("kbt_") || s.contains("allplay")
                || s.contains("__sidi_") || s.contains("stravers") || s.contains("voidboost")
                || s.contains("kodik")) return false;
        if (AdRemovalStore.isDangerousSelector(selector, tag, href, "")) return false;
        if ("iframe".equals(t)) {
            if (s.equals("iframe")) return false;
            if (u.contains("stravers.live") || u.contains("bulkikim") || u.contains("villybizy")
                    || u.contains("alloha") || u.contains("kodik") || u.contains("voidboost")
                    || u.contains("videocdn") || u.contains("collaps") || u.contains("hdvb")) return false;
            // Clear ad iframe selectors (moviead55/…) allowed; never obrut (Turbo player).
            if (s.contains("obrut") || s.contains("nextembed") || s.contains("shortener2")) return false;
            if (s.contains("iframe[src*=") && isClearAdIframeSelector(s)) return true;
        }
        return true;
    }

    /** True for iframe[src*="…"] needles that are known ad hosts, never Stravers/kinobox/Turbo. */
    private static boolean isClearAdIframeSelector(String selectorLower) {
        if (selectorLower == null) return false;
        if (selectorLower.contains("obrut") || selectorLower.contains("stravers")
                || selectorLower.contains("nextembed") || selectorLower.contains("shortener2")
                || selectorLower.contains("videotoday")) return false;
        return selectorLower.contains("moviead55")
                || selectorLower.contains("buzzoola") || selectorLower.contains("traffaret")
                || selectorLower.contains("adriver") || selectorLower.contains("video-mech")
                || selectorLower.contains("betweendigital") || selectorLower.contains("vqserve")
                || selectorLower.contains("moe.video") || selectorLower.contains("aj2635")
                || selectorLower.contains("prestreamnos");
    }

    /**
     * Map ad-iframe hostname to a stable parent-frame src needle.
     * Never returns Stravers / Alloha / Kodik / Turbo(obrut) needles.
     */
    private static String adFrameSrcNeedle(String frameHost) {
        String h = frameHost == null ? "" : frameHost.trim().toLowerCase(Locale.US);
        if (h.isEmpty()) return "";
        if (h.contains("stravers") || h.contains("bulkikim") || h.contains("villybizy")
                || h.contains("alloha") || h.contains("kodik") || h.contains("voidboost")
                || h.contains("videocdn") || h.contains("collaps") || h.contains("hdvb")
                || h.contains("obrut") || h.contains("nextembed") || h.contains("shortener2")
                || h.contains("videotoday")) {
            return "";
        }
        if (h.contains("moviead55")) return "moviead55";
        if (h.contains("buzzoola")) return "buzzoola";
        if (h.contains("traffaret")) return "traffaret";
        if (h.contains("adriver")) return "adriver";
        if (h.contains("video-mech")) return "video-mech";
        if (h.contains("betweendigital")) return "betweendigital";
        if (h.contains("vqserve")) return "vqserve";
        if (h.contains("moe.video")) return "moe.video";
        if (h.contains("aj2635")) return "aj2635";
        if (h.contains("prestreamnos")) return "prestreamnos";
        if (h.contains("ussp.io")) return "ussp.io";
        if (h.contains("showjet")) return "showjet";
        if (h.contains("skcrtxr")) return "skcrtxr";
        if (h.contains("adlook")) return "adlook";
        if (h.contains("bidster")) return "bidster";
        String[] parts = h.split("\\.");
        if (parts.length >= 2) {
            String base = parts[parts.length - 2] + "." + parts[parts.length - 1];
            if (base.contains("stravers") || base.contains("alloha") || base.contains("kodik")) return "";
            return base;
        }
        return h;
    }

    /** Hide parent-frame iframes matching needle and optional positioned wrappers; never Stravers. */
    private void hideParentAdIframes(WebView web, String needle, String ruleId) {
        if (web == null || needle == null || needle.isEmpty()) return;
        String js = "(function(){try{" +
                "var needle=" + JSONObject.quote(needle.toLowerCase(Locale.US)) + ";" +
                "var ruleId=" + JSONObject.quote(ruleId == null ? "" : ruleId) + ";" +
                "function playerUrl(u){u=String(u||'').toLowerCase();return /stravers\\.live|bulkikim|villybizy|alloha|kodik|voidboost|videocdn|collaps|hdvb|nextembed\\.ws|shortener2\\.link|videotoday\\.site|obrut\\.show/.test(u);}" +
                "function hide(e){try{if(!e)return 0;e.setAttribute('data-sidi-manual-hidden','1');if(ruleId)e.setAttribute('data-sidi-manual-rule',ruleId);" +
                "e.style.setProperty('display','none','important');e.style.setProperty('visibility','hidden','important');e.style.setProperty('pointer-events','none','important');return 1;}catch(x){return 0;}}" +
                "var n=0;document.querySelectorAll('iframe[src]').forEach(function(f){var s=String(f.src||f.getAttribute('src')||'').toLowerCase();" +
                "if(playerUrl(s)||s.indexOf(needle)<0)return;n+=hide(f);var p=f.parentElement,i=0;" +
                "while(p&&p!==document.body&&i<5){try{var c=getComputedStyle(p),r=p.getBoundingClientRect();" +
                "var pos=c.position==='fixed'||c.position==='sticky'||c.position==='absolute';" +
                "var a=r.width*r.height,screen=Math.max(1,innerWidth*innerHeight);" +
                "var rid=String(p.id||'');var randomId=/^[A-Za-z]{5}[0-9]{10,22}$/.test(rid);" +
                "if((pos||randomId)&&a<screen*0.98&&a>40*40){n+=hide(p);break;}}catch(z){}p=p.parentElement;i++;}});" +
                "return n;}catch(e){return -1;}})();";
        try {
            web.evaluateJavascript(js, value -> Log.i("SidiAdPicker",
                    "AD_FRAME_PARENT_HIDE needle=" + needle + " hidden=" + value));
        } catch (Throwable ignored) {
        }
    }

    private final class PlayerChoiceBridge {
        private final BrowserTab ownerTab;
        private final WebView ownerWeb;

        PlayerChoiceBridge(BrowserTab ownerTab, WebView ownerWeb) {
            this.ownerTab = ownerTab;
            this.ownerWeb = ownerWeb;
        }

        @JavascriptInterface
        public void save(int index, String label) {
            runOnUiThread(() -> {
                if (ownerTab.webView != ownerWeb || index < 0 || index > 50) return;
                MovieRecord record = store.get(ownerTab.key);
                if (record == null) return;
                String clean = label == null ? "" : label.replace('\n', ' ').replace('\r', ' ').trim();
                if (clean.length() > 120) clean = clean.substring(0, 120);
                record.playerIndex = index;
                if (!clean.isEmpty()) record.playerLabel = clean;
                ownerTab.lastManualPlayerChoiceAt = SystemClock.elapsedRealtime();
                ownerTab.playerChoiceRestoredGeneration = ownerTab.playerLoadGeneration;
                store.update(record);
                Log.i("SidiPlayerChoice", "PLAYER_CHOICE_SAVED key=" + ownerTab.key
                        + " index=" + index + " label=" + clean);
            });
        }
    }

    private final class AdPickerBridge {
        private final BrowserTab ownerTab;
        private final WebView ownerWeb;

        AdPickerBridge(BrowserTab ownerTab, WebView ownerWeb) {
            this.ownerTab = ownerTab;
            this.ownerWeb = ownerWeb;
        }

        @JavascriptInterface
        public boolean isActive() {
            return adRemovalMode && ownerTab.webView == ownerWeb;
        }

        @JavascriptInterface
        public int decision(String token) {
            Integer value = token == null ? null : adPickerDecisions.get(token);
            return value == null ? 0 : value;
        }

        @JavascriptInterface
        public String rulesForHost(String host) {
            try {
                String normalized = host == null ? "" : host.toLowerCase(Locale.US).replaceFirst("^www\\.", "");
                JSONArray out = new JSONArray();
                for (AdRemovalStore.Rule rule : adRemovalStore.forHost(normalized)) {
                    JSONObject item = new JSONObject();
                    item.put("id", rule.id);
                    item.put("selector", rule.selector);
                    item.put("tag", rule.tag);
                    item.put("href", rule.href);
                    item.put("text", rule.text);
                    out.put(item);
                }
                return out.toString();
            } catch (Throwable error) {
                return "[]";
            }
        }

        @JavascriptInterface
        public void pick(String host, String selector, String token, String tag, String href, String text) {
            runOnUiThread(() -> {
                if (!adRemovalMode || ownerTab.webView != ownerWeb) return;
                if (adPickerPromptOpen) {
                    adPickerDecisions.put(token, -1);
                    handler.postDelayed(() -> adPickerDecisions.remove(token), 30000L);
                    return;
                }
                if (!isSafeManualSelection(selector, tag, href)) {
                    adPickerDecisions.put(token, -1);
                    toast("Этот элемент нельзя блокировать: он может быть частью плеера");
                    handler.postDelayed(() -> adPickerDecisions.remove(token), 30000L);
                    return;
                }
                adPickerPromptOpen = true;
                String label = text == null ? "" : text.trim();
                if (label.length() > 90) label = label.substring(0, 90) + "…";
                String message = (label.isEmpty() ? "" : label + "\n\n") + selector;
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Удалить этот элемент?")
                        .setMessage(message)
                        .setNegativeButton("Отмена", (d, which) -> {
                            adPickerDecisions.put(token, -1);
                            handler.postDelayed(() -> adPickerDecisions.remove(token), 30000L);
                        })
                        .setPositiveButton("Удалить", (d, which) -> {
                            try {
                                AdRemovalStore.Rule saved = adRemovalStore.add(host, selector, tag, href, text);
                                Log.i("SidiAdPicker", "MANUAL_AD_RULE_ADDED host=" + saved.host
                                        + " selector=" + saved.selector + " tag=" + saved.tag
                                        + " href=" + saved.href
                                        + " text=" + (saved.text == null ? "" : saved.text.replace('\n', ' ').replace('\r', ' ')));
                                adPickerDecisions.put(token, 1);
                                handler.postDelayed(() -> adPickerDecisions.remove(token), 30000L);
                                applySavedAdRules(ownerWeb);
                                injectPlayerSafeLayout(ownerWeb);
                                restorePlayerViewport(ownerWeb);
                                autoHideKnownAdPopups(ownerWeb, "after-manual");
                                toast("Реклама скрыта. Режим выбора остаётся включён");
                            } catch (Throwable error) {
                                Log.e("SidiAdPicker", "MANUAL_AD_RULE_SAVE_FAILED", error);
                                adPickerDecisions.put(token, -1);
                                toast("Не удалось сохранить правило");
                            }
                        })
                        .create();
                dialog.setOnDismissListener(d -> {
                    adPickerPromptOpen = false;
                    // Dialog can leave focus/layout odd in fullscreen — restore player hit targets.
                    if (ownerTab.webView == ownerWeb) {
                        injectPlayerSafeLayout(ownerWeb);
                        restorePlayerViewport(ownerWeb);
                    }
                });
                dialog.show();
            });
        }

        @JavascriptInterface
        public void candidate(String reason) {
            Log.i("SidiAdPicker", "PICKER_CANDIDATE " + reason);
        }

        @JavascriptInterface
        public void rejected(String reason) {
            Log.i("SidiAdPicker", "PICKER_REJECTED reason=" + reason);
            runOnUiThread(() -> toast("Не удалось выбрать рекламный слой в этой точке"));
        }

        @JavascriptInterface
        public void deferred(String reason) {
            Log.i("SidiAdPicker", "PICKER_DEFER_TO_FRAME reason=" + reason);
        }

        /**
         * Ad iframe (obrut/moviead55/…) clicked VIDEO before parent overlay exists.
         * Persist a parent-frame iframe[src*=…] rule — never Stravers.
         */
        @JavascriptInterface
        public void blockAdFrame(String frameHost) {
            runOnUiThread(() -> {
                if (!adRemovalMode || ownerTab.webView != ownerWeb) return;
                String needle = adFrameSrcNeedle(frameHost);
                if (needle.isEmpty()) {
                    Log.i("SidiAdPicker", "AD_FRAME_BLOCK_SKIP host=" + frameHost + " reason=not-clear-ad");
                    toast("Этот элемент нельзя блокировать: он может быть частью плеера");
                    return;
                }
                String pageHost = "";
                try {
                    String url = ownerWeb.getUrl();
                    if (url != null) {
                        String h = Uri.parse(url).getHost();
                        pageHost = h == null ? "" : h.toLowerCase(Locale.US).replaceFirst("^www\\.", "");
                    }
                } catch (Throwable ignored) {
                }
                if (pageHost.isEmpty()) {
                    toast("Не удалось определить страницу для правила");
                    return;
                }
                final String pageHostFinal = pageHost;
                final String needleFinal = needle;
                final String frameHostFinal = frameHost == null ? "" : frameHost;
                final String selector = "iframe[src*=\"" + needleFinal + "\"]";
                if (!isSafeManualSelection(selector, "iframe", "https://" + needleFinal + "/")) {
                    Log.i("SidiAdPicker", "AD_FRAME_BLOCK_UNSAFE host=" + frameHostFinal + " sel=" + selector);
                    toast("Этот элемент нельзя блокировать: он может быть частью плеера");
                    return;
                }
                if (adPickerPromptOpen) return;
                adPickerPromptOpen = true;
                String message = "Рекламный iframe\n" + frameHostFinal + "\n\n" + selector;
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Удалить этот элемент?")
                        .setMessage(message)
                        .setNegativeButton("Отмена", (d, which) -> { })
                        .setPositiveButton("Удалить", (d, which) -> {
                            try {
                                AdRemovalStore.Rule saved = adRemovalStore.add(
                                        pageHostFinal, selector, "iframe", "", "");
                                Log.i("SidiAdPicker", "MANUAL_AD_RULE_ADDED host=" + saved.host
                                        + " selector=" + saved.selector + " tag=" + saved.tag
                                        + " href= text= via=ad-frame-block frameHost=" + frameHostFinal);
                                hideParentAdIframes(ownerWeb, needleFinal, saved.id);
                                applySavedAdRules(ownerWeb);
                                injectPlayerSafeLayout(ownerWeb);
                                restorePlayerViewport(ownerWeb);
                                autoHideKnownAdPopups(ownerWeb, "after-ad-frame");
                                toast("Реклама скрыта. Режим выбора остаётся включён");
                            } catch (Throwable error) {
                                Log.e("SidiAdPicker", "AD_FRAME_BLOCK_SAVE_FAILED", error);
                                toast("Не удалось сохранить правило");
                            }
                        })
                        .create();
                dialog.setOnDismissListener(d -> {
                    adPickerPromptOpen = false;
                    if (ownerTab.webView == ownerWeb) {
                        injectPlayerSafeLayout(ownerWeb);
                        restorePlayerViewport(ownerWeb);
                    }
                });
                dialog.show();
            });
        }
    }


    private void markCurrentVideoAdvertisement(BrowserTab tab) {
        if (tab == null || tab.webView == null) {
            toast("Сначала откройте фильм или сериал");
            return;
        }
        final String pageUrl = tab.webView.getUrl();
        final long markerId = System.currentTimeMillis();
        Log.i("SidiVideoAd", "VIDEO_AD_MARK_BEGIN id=" + markerId + " page=" + (pageUrl == null ? "" : pageUrl));
        if (adBlocker != null) adBlocker.dumpRecentRequests(markerId, pageUrl);
        tab.adsSuppressSticky = true;
        try {
            tab.webView.evaluateJavascript("(function(){try{var v=document.querySelector('video');if(!v)return JSON.stringify({video:false,href:location.href});return JSON.stringify({video:true,href:location.href,src:v.currentSrc||v.src||'',currentTime:v.currentTime||0,duration:isFinite(v.duration)?v.duration:-1,paused:!!v.paused,readyState:v.readyState,networkState:v.networkState});}catch(e){return JSON.stringify({error:String(e&&e.message||e),href:location.href});}})();",
                    result -> {
                        Log.i("SidiVideoAd", "VIDEO_AD_MARK_DOM id=" + markerId + " data=" + result);
                        String videoSrc = "";
                        try {
                            String raw = result;
                            if (raw != null && raw.length() >= 2 && raw.charAt(0) == '"') {
                                raw = new JSONArray("[" + raw + "]").getString(0);
                            }
                            if (raw != null && raw.startsWith("{")) {
                                videoSrc = new JSONObject(raw).optString("src", "");
                            }
                        } catch (Throwable ignored) {
                        }
                        int learned = 0;
                        if (adBlocker != null) {
                            learned = adBlocker.learnFromVideoAdMark(pageUrl, videoSrc);
                            adBlocker.markPrimaryPlaybackSeen();
                        }
                        hideResumeAdContainers(tab.webView);
                        autoHideKnownAdPopups(tab.webView, "video-ad-mark");
                        final int n = learned;
                        runOnUiThread(() -> toast(n > 0
                                ? ("Видеореклама запомнена (" + n + "). Будет блокироваться на следующих запусках.")
                                : "Видеореклама помечена: suppress включён (хосты из трафика сохраним при повторе)."));
                        Log.i("SidiVideoAd", "VIDEO_AD_MARK_APPLIED id=" + markerId + " learnedHosts=" + n
                                + " sticky=true");
                    });
        } catch (Throwable error) {
            Log.w("SidiVideoAd", "VIDEO_AD_MARK_DOM_FAILED id=" + markerId, error);
            if (adBlocker != null) {
                int learned = adBlocker.learnFromVideoAdMark(pageUrl, "");
                adBlocker.markPrimaryPlaybackSeen();
                toast(learned > 0 ? ("Видеореклама запомнена (" + learned + ").") : "Suppress включён.");
            }
        }
        handler.postDelayed(() -> {
            if (adBlocker != null) {
                adBlocker.dumpRecentRequests(markerId, tab.webView == null ? pageUrl : tab.webView.getUrl());
                // Second pass: more requests may have arrived during the ad.
                adBlocker.learnFromVideoAdMark(pageUrl, "");
            }
            if (tab.webView != null) hideResumeAdContainers(tab.webView);
            Log.i("SidiVideoAd", "VIDEO_AD_MARK_END id=" + markerId);
        }, 5000L);
    }

    private void undoManualAdRemoval() {
        if (adRemovalStore == null) return;
        AdRemovalStore.Rule removed = adRemovalStore.undoLast();
        if (removed == null) {
            toast("Нет сохранённых удалений");
            return;
        }
        String ruleSelector = "[data-sidi-manual-rule=\"" + removed.id + "\"]";
        String restore = "(function(){try{document.querySelectorAll(" + JSONObject.quote(ruleSelector) + ").forEach(function(e){e.style.removeProperty('display');e.removeAttribute('data-sidi-manual-hidden');e.removeAttribute('data-sidi-manual-rule');});}catch(x){}})();";
        for (BrowserTab tab : tabs) {
            if (tab.webView == null || tab.webView.getUrl() == null) continue;
            try {
                String host = Uri.parse(tab.webView.getUrl()).getHost();
                String normalized = host == null ? "" : host.toLowerCase(Locale.US).replaceFirst("^www\\.", "");
                if (removed.host.equals(normalized)) tab.webView.evaluateJavascript(restore, null);
            } catch (Throwable ignored) {
            }
        }
        Log.i("SidiAdPicker", "MANUAL_AD_RULE_REMOVED host=" + removed.host
                + " selector=" + removed.selector + " id=" + removed.id);
        toast("Последнее удаление отменено");
    }


    private void showAddressReplacementDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(4), dp(12), dp(8));
        android.widget.RadioGroup group = new android.widget.RadioGroup(this);
        group.setOrientation(android.widget.RadioGroup.VERTICAL);
        Map<Integer, String> ruleIds = new LinkedHashMap<>();
        String activeId = playerUrlRules.activeRuleId();

        android.widget.RadioButton none = new android.widget.RadioButton(this);
        none.setId(View.generateViewId());
        none.setText("без подмены");
        none.setTextColor(TEXT);
        none.setChecked(activeId.isEmpty());
        none.setTag("");
        group.addView(none);
        ruleIds.put(none.getId(), "");

        List<PlayerUrlRules.Rule> rules = playerUrlRules.allRules();
        for (PlayerUrlRules.Rule rule : rules) {
            android.widget.RadioButton radio = new android.widget.RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setText(rule.label);
            radio.setTextColor(TEXT);
            radio.setChecked(rule.id.equals(activeId));
            radio.setTag(rule.id);
            group.addView(radio);
            ruleIds.put(radio.getId(), rule.id);
        }
        box.addView(group);

        Button add = settingsButton("+ Добавить правило", "");
        Button delete = settingsButton("Удалить выбранное пользовательское правило", "");
        box.addView(add, settingsCardButtonParams());
        box.addView(delete, settingsCardButtonParams());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Плеер → Подмена адреса")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();

        Runnable updateDelete = () -> {
            String selected = playerUrlRules.activeRuleId();
            boolean custom = false;
            for (PlayerUrlRules.Rule rule : playerUrlRules.allRules()) {
                if (rule.id.equals(selected)) { custom = !rule.builtIn; break; }
            }
            delete.setEnabled(custom);
        };

        group.setOnCheckedChangeListener((g, checkedId) -> {
            String selected = ruleIds.get(checkedId);
            if (selected == null) return;
            String before = playerUrlRules.activeRuleId();
            playerUrlRules.setActiveRule(selected);
            updateDelete.run();
            if (!before.equals(playerUrlRules.activeRuleId())) reloadActivePlayerForReplacementRule();
        });
        add.setOnClickListener(v -> {
            dialog.dismiss();
            showAddAddressRuleDialog();
        });
        delete.setOnClickListener(v -> {
            String selected = playerUrlRules.activeRuleId();
            if (selected.isEmpty()) return;
            if (playerUrlRules.deleteCustomRule(selected)) {
                reloadActivePlayerForReplacementRule();
                dialog.dismiss();
                showAddressReplacementDialog();
            }
        });
        updateDelete.run();
        dialog.show();
    }

    private void showAddAddressRuleDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);
        EditText from = formField("Что заменить", "", true);
        EditText to = formField("На что заменить", "", true);
        box.addView(fieldBlock("Что заменить:", from));
        box.addView(fieldBlock("На что заменить:", to));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Добавить правило")
                .setView(box)
                .setNegativeButton("Отмена", (d, w) -> showAddressReplacementDialog())
                .setPositiveButton("Добавить", null)
                .create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String a = from.getText().toString().trim();
            String b = to.getText().toString().trim();
            if (a.isEmpty()) { from.setError("Введите, что заменить"); return; }
            if (b.isEmpty()) { to.setError("Введите, на что заменить"); return; }
            try {
                playerUrlRules.addCustomRule(a, b);
                reloadActivePlayerForReplacementRule();
                dialog.dismiss();
                showAddressReplacementDialog();
            } catch (Throwable error) {
                toast("Не удалось сохранить правило");
            }
        }));
        dialog.show();
    }

    private void reloadActivePlayerForReplacementRule() {
        BrowserTab tab = activeTab;
        if (tab == null || tab.webView == null) return;
        MovieRecord record = store.get(tab.key);
        String next = playerUrlFor(record);
        if (next.isEmpty()) return;
        tab.mainFrameRetryCount = 0;
        tab.playerReady = false;
        tab.playbackResourceSeen = false;
        tab.playerIframeSeen = false;
        tab.directPlayerPromotionTried = false;
        tab.directPlayerFallbackUsed = false;
        showPlayerShield(tab, "Загружаем плеер…");
        showPlayerLoading(tab);
        tab.webView.loadUrl(next);
    }

    private String filterUpdateLabel() {
        return "13.8";
    }

    private void showThemeDialog() {
        String[] labels = {"День", "Ночь", "Системный"};
        String[] values = {"day", "night", "system"};
        String current = getThemeMode();
        int selected = "day".equals(current) ? 0 : ("night".equals(current) ? 1 : 2);
        new AlertDialog.Builder(this)
                .setTitle("Режим оформления")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    dialog.dismiss();
                    setThemeMode(values[which]);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить историю?")
                .setMessage("Будет удалён список недавних просмотров. Избранное и заметки останутся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> {
                    int count = store.clearHistory();
                    refreshHome();
                    toast("Удалено из истории: " + count);
                })
                .show();
    }

    private void confirmClearFavorites() {
        new AlertDialog.Builder(this)
                .setTitle("Очистить избранное?")
                .setMessage("Отметка избранного будет снята со всех фильмов.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, which) -> {
                    int count = store.clearFavorites();
                    refreshHome();
                    refreshPlayerToolbars();
                    toast("Удалено из избранного: " + count);
                })
                .show();
    }

    private void showTransferDataDialog() {
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Передача данных")
                .setMessage("Создаю резервную копию и локальную ссылку…")
                .setCancelable(false)
                .create();
        progress.show();
        try {
            String json = store.exportData().toString();
            DataTransferManager.serve(this, json, new DataTransferManager.Callback() {
                @Override
                public void onSuccess(DataTransferManager.Result result) {
                    progress.dismiss();
                    if (activeTransferResult != null) activeTransferResult.stop();
                    activeTransferResult = result;
                    showTransferReadyDialog(result);
                }

                @Override
                public void onError(String message, Throwable error) {
                    progress.dismiss();
                    Log.e("SidiTransfer", message, error);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Не удалось передать данные")
                            .setMessage(message + "\n\n" + (error == null ? "Неизвестная ошибка" : error.getMessage()))
                            .setPositiveButton("Закрыть", null)
                            .show();
                }
            });
        } catch (Throwable error) {
            progress.dismiss();
            toast("Не удалось создать резервную копию");
        }
    }

    private void showTransferReadyDialog(DataTransferManager.Result result) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(4), dp(12), dp(4));
        int qrSize = dp(compact ? 150 : 180);
        ImageView qr = new ImageView(this);
        qr.setBackgroundColor(Color.WHITE);
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        try {
            qr.setImageBitmap(createQrBitmap(result.transferUrl, qrSize));
        } catch (Throwable error) {
            Log.e("SidiTransfer", "QR generation failed", error);
        }
        box.addView(qr, new LinearLayout.LayoutParams(qrSize, qrSize));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(12), 0, 0, 0);
        TextView link = text(result.transferUrl, 10, TEXT);
        link.setTextIsSelectable(true);
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setPadding(dp(8), dp(6), dp(8), dp(6));
        link.setBackground(rounded(PANEL_2, 8));
        right.addView(link, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));
        Button copy = accentButton("Скопировать ссылку");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        cp.setMargins(0, dp(8), 0, 0);
        right.addView(copy, cp);
        box.addView(right, new LinearLayout.LayoutParams(0, qrSize, 1f));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Передача готова")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .setNeutralButton("Остановить", (d, which) -> {
                    result.stop();
                    if (activeTransferResult == result) activeTransferResult = null;
                })
                .create();
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Сиди и смотри — передача", result.transferUrl));
            toast("Ссылка скопирована");
        });
        dialog.show();
    }

    private void showReceiveDataDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText linkInput = formField("http://192.168…/backup.json", "", true);
        box.addView(linkInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        Button importLink = accentButton("Принять по ссылке");
        Button scan = smallButton("Сканировать QR");
        Button paste = smallButton("Вставить ссылку");
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ap1 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        ap1.setMargins(0, dp(8), dp(4), 0);
        LinearLayout.LayoutParams ap2 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        ap2.setMargins(dp(4), dp(8), dp(4), 0);
        LinearLayout.LayoutParams ap3 = new LinearLayout.LayoutParams(0, dp(42), 1f);
        ap3.setMargins(dp(4), dp(8), 0, 0);
        actions.addView(importLink, ap1);
        actions.addView(scan, ap2);
        actions.addView(paste, ap3);
        box.addView(actions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Принять данные")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();
        importLink.setOnClickListener(v -> importFromInput(linkInput.getText().toString(), dialog));
        scan.setOnClickListener(v -> {
            dialog.dismiss();
            startQrScanner();
        });
        paste.setOnClickListener(v -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
                    CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                    linkInput.setText(value);
                }
            } catch (Throwable ignored) {
            }
        });
        dialog.show();
    }

    private void importFromInput(String value, AlertDialog parentDialog) {
        String input = value == null ? "" : value.trim();
        if (input.isEmpty()) {
            toast("Введите ссылку или отсканируйте QR");
            return;
        }
        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            try {
                importBackupValue(input);
                if (parentDialog != null) parentDialog.dismiss();
            } catch (Throwable error) {
                toast("Некорректная резервная копия");
            }
            return;
        }
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Получение данных")
                .setMessage("Загружаю резервную копию…")
                .setCancelable(false)
                .create();
        progress.show();
        ioPool.execute(() -> {
            try {
                String json = downloadBackupText(input);
                runOnUiThread(() -> {
                    progress.dismiss();
                    try {
                        importBackupValue(json);
                        if (parentDialog != null) parentDialog.dismiss();
                    } catch (Throwable error) {
                        toast("Ссылка не содержит резервную копию «Сиди и смотри»");
                    }
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Не удалось получить данные")
                            .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                            .setPositiveButton("Закрыть", null)
                            .show();
                });
            }
        });
    }

    private String downloadBackupText(String rawUrl) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(rawUrl).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("Сервер вернул HTTP " + code);
            int max = 5 * 1024 * 1024;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > max) throw new IllegalStateException("Резервная копия слишком большая");
                    output.write(buffer, 0, read);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showDataDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));

        ImageView qrImage = new ImageView(this);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrImage.setBackgroundColor(Color.WHITE);
        int qrSize = dp(compact ? 260 : 320);
        box.addView(qrImage, new LinearLayout.LayoutParams(qrSize, qrSize));

        TextView info = text("QR содержит резервную копию истории, избранного, заметок и прогресса.", 13, MUTED);
        info.setGravity(Gravity.CENTER);
        info.setPadding(0, dp(12), 0, 0);
        box.addView(info, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button copy = accentButton("Скопировать резервную копию");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        cp.setMargins(0, dp(14), 0, 0);
        box.addView(copy, cp);

        Button scan = accentButton("Сканировать QR-код");
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        sp.setMargins(0, dp(10), 0, 0);
        box.addView(scan, sp);

        Button paste = smallButton("Импортировать из буфера");
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        pp.setMargins(0, dp(10), 0, 0);
        box.addView(paste, pp);

        Button diagnostics = smallButton("Собрать диагностику");
        diagnostics.setCompoundDrawablePadding(dp(8));
        LinearLayout.LayoutParams dpParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        dpParams.setMargins(0, dp(10), 0, 0);
        box.addView(diagnostics, dpParams);

        final String[] payload = {""};
        try {
            String json = store.exportData().toString();
            payload[0] = buildQrPayload(json);
            qrImage.setImageBitmap(createQrBitmap(payload[0], qrSize));
        } catch (Exception e) {
            qrImage.setImageDrawable(null);
            info.setText("Данных слишком много для одного QR. Резервную копию можно скопировать кнопкой ниже.");
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Передача данных")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();

        copy.setOnClickListener(v -> {
            try {
                String value = payload[0].isEmpty() ? store.exportData().toString() : payload[0];
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Сиди и смотри — резервная копия", value));
                toast("Резервная копия скопирована");
            } catch (Exception e) {
                toast("Не удалось создать копию");
            }
        });

        scan.setOnClickListener(v -> {
            dialog.dismiss();
            startQrScanner();
        });

        paste.setOnClickListener(v -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (!clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null) throw new IllegalStateException();
                CharSequence value = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                importBackupValue(value.toString());
            } catch (Exception e) {
                toast("В буфере нет корректной копии «Сиди и смотри»");
            }
        });

        diagnostics.setOnClickListener(v -> {
            dialog.dismiss();
            startDiagnosticsCollection();
        });
        dialog.show();
    }

    private void startDiagnosticsCollection() {
        AlertDialog progress = new AlertDialog.Builder(this)
                .setTitle("Диагностика")
                .setMessage("Собираю системный лог, сведения о WebView, плеере и устройстве…")
                .setCancelable(false)
                .create();
        progress.show();

        DiagnosticsManager.collectAndServe(this, new DiagnosticsManager.Callback() {
            @Override
            public void onSuccess(DiagnosticsManager.Result result) {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    result.stop();
                    return;
                }
                progress.dismiss();
                if (activeDiagnosticsResult != null) activeDiagnosticsResult.stop();
                activeDiagnosticsResult = result;
                showDiagnosticsDownloadDialog(result);
            }

            @Override
            public void onError(String message, Throwable error) {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                progress.dismiss();
                Log.e("SidiDiagnostics", message, error);
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Не удалось собрать лог")
                        .setMessage(message + "\n\n" + (error == null ? "Неизвестная ошибка" : error.getMessage()))
                        .setPositiveButton("Закрыть", null)
                        .show();
            }
        });
    }

    private void showDiagnosticsDownloadDialog(DiagnosticsManager.Result result) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(4), dp(12), dp(4));

        int qrSize = dp(compact ? 150 : 180);
        ImageView qrImage = new ImageView(this);
        qrImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        qrImage.setBackgroundColor(Color.WHITE);
        try {
            qrImage.setImageBitmap(createQrBitmap(result.downloadUrl, qrSize));
        } catch (Exception error) {
            Log.e("SidiDiagnostics", "QR generation failed", error);
        }
        box.addView(qrImage, new LinearLayout.LayoutParams(qrSize, qrSize));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(12), 0, 0, 0);
        TextView status = text(result.fullSystemLog ? "Полный лог" : "Частичный лог",
                11, result.fullSystemLog ? ACCENT : Color.rgb(255, 184, 77));
        right.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));
        TextView instruction = text(
                "По этому QR-коду или ссылке можно скачать архив на телефон. " +
                        "После этого архив нужно отправить на почту " +
                        "freemoviesforfreepeople@proton.me или в Telegram разработчику.",
                12, TEXT);
        instruction.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        instruction.setLineSpacing(0, 1.08f);
        right.addView(instruction, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        TextView url = text(result.downloadUrl, 10, TEXT);
        url.setTextIsSelectable(true);
        url.setGravity(Gravity.CENTER_VERTICAL);
        url.setPadding(dp(8), dp(4), dp(8), dp(4));
        url.setBackground(rounded(PANEL_2, 8));
        right.addView(url, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        Button copyLink = accentButton("Скопировать ссылку");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        cp.setMargins(0, dp(5), 0, 0);
        right.addView(copyLink, cp);
        box.addView(right, new LinearLayout.LayoutParams(0, qrSize, 1f));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Диагностика готова")
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .setNeutralButton("Остановить", (d, which) -> {
                    result.stop();
                    if (activeDiagnosticsResult == result) activeDiagnosticsResult = null;
                })
                .create();

        copyLink.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Сиди и смотри — диагностика", result.downloadUrl));
            toast("Ссылка скопирована");
        });
        dialog.show();
    }

    private void sendDiagnosticsByEmail(DiagnosticsManager.Result result) {
        try {
            Uri attachment = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", result.zipFile);
            Intent email = new Intent(Intent.ACTION_SEND);
            email.setType("application/zip");
            email.putExtra(Intent.EXTRA_EMAIL,
                    new String[]{"freemoviesforfreepeople@proton.me"});
            email.putExtra(Intent.EXTRA_SUBJECT,
                    "Сиди и смотри — лог версии " + shortAppVersion());
            email.putExtra(Intent.EXTRA_TEXT, "Диагностический лог приложения.");
            email.putExtra(Intent.EXTRA_STREAM, attachment);
            email.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(email, "Отправить лог"));
        } catch (Throwable error) {
            toast("Не найдено приложение для отправки почты");
        }
    }

    private void startQrScanner() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            toast("На этом устройстве нет камеры. Введите локальную ссылку вручную.");
            return;
        }
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(java.util.Collections.singleton(IntentIntegrator.QR_CODE));
        integrator.setPrompt("Наведите камеру на QR-код «Сиди и смотри»");
        integrator.setBeepEnabled(false);
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    private void importBackupValue(String value) throws Exception {
        String json = decodeQrPayload(value);
        JSONObject root = new JSONObject(json);
        if (root.optInt("version", 0) <= 0 || root.optJSONArray("items") == null) {
            throw new IllegalArgumentException("Invalid backup");
        }
        int changed = store.importData(root);
        refreshHome();
        refreshPlayerToolbars();
        toast("Импортировано записей: " + changed);
    }

    private String buildQrPayload(String json) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(output)) {
            deflater.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return "SIDI1:" + Base64.encodeToString(output.toByteArray(),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private String decodeQrPayload(String value) throws Exception {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("SIDI1:")) return trimmed;
        byte[] compressed = Base64.decode(trimmed.substring(6), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        java.util.zip.InflaterInputStream inflater = new java.util.zip.InflaterInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inflater.read(buffer)) != -1) output.write(buffer, 0, read);
        inflater.close();
        return output.toString(StandardCharsets.UTF_8.name());
    }

    private Bitmap createQrBitmap(String value, int size) throws WriterException {
        Map<EncodeHintType, Object> hints = new LinkedHashMap<>();
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, size, size, hints);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        return bitmap;
    }

    private void refreshPlayerToolbars() {
        for (BrowserTab tab : tabs) refreshPlayerToolbar(tab);
        rebuildTabButtons();
    }

    private void refreshPlayerToolbar(BrowserTab tab) {
        MovieRecord record = store.get(tab.key);
        if (record == null) return;
        if (tab.toolbarTitle != null) {
            String progress = record.progressLabel();
            boolean controlsVisible = tab.controlsGroup != null
                    && tab.controlsGroup.getVisibility() == View.VISIBLE;
            tab.toolbarTitle.setText(controlsVisible || progress.isEmpty()
                    ? record.title : record.title + "  ·  " + progress);
        }
        if (tab.favoriteButton != null) {
            tab.favoriteButton.setCompoundDrawablesWithIntrinsicBounds(
                    record.favorite ? R.drawable.ic_star_filled : R.drawable.ic_star_outline, 0, 0, 0);
        }
    }

    private void updateAdblockStatus() {
        // Счётчик фильтров намеренно не показывается в интерфейсе.
    }

    private void saveTabs() {
        List<String> keys = new ArrayList<>();
        for (BrowserTab tab : tabs) keys.add(tab.key);
        store.saveTabs(keys, activeTab == null ? "" : activeTab.key);
    }

    private boolean hasActivePlayerContext() {
        if (activeTab == null || activeTab.webView == null || activeTab.root == null) return false;
        String url = activeTab.webView.getUrl();
        return activeTab.root.isShown()
                && (activeTab.playerReady || activeTab.playerIsolated || isDirectPlayerPage(url));
    }

    private void onPlayerFullscreenChanged(boolean fullscreen) {
        embeddedFullScreen = fullscreen;
        BrowserTab tab = activeTab;
        if (tab != null) {
            tab.pointerInsidePlayer = false;
            tab.lastPointerAt = 0L;
        }
        Log.i("SidiInput", fullscreen
                ? "FULLSCREEN_ENTERED " + inputSnapshot()
                : "FULLSCREEN_EXITED " + inputSnapshot());
        if (tab != null && tab.webView != null) {
            // Never attach a touch-stealing native overlay on bm0 (13.12 regression).
            Log.i("SidiAdPicker", "PICKER_FULLSCREEN_OVERLAY disabled-13.13");
            handler.post(() -> {
                autoHideKnownAdPopups(tab.webView, "fullscreen-" + fullscreen);
                if (adRemovalMode) activateAdPickerRuntime(tab.webView, "fullscreen-" + fullscreen);
                if (!fullscreen) {
                    injectPlayerSafeLayout(tab.webView);
                    restorePlayerViewport(tab.webView);
                }
            });
        }
        if (!fullscreen) handler.postDelayed(this::enableImmersiveMode, 120L);
    }

    private void enterNativeFullScreen(View view, WebChromeClient.CustomViewCallback callback,
                                       BrowserTab tab) {
        Log.i("SidiInput", "FULLSCREEN_ENTER_REQUEST " + inputSnapshot());
        if (playerHost == null) {
            if (callback != null) callback.onCustomViewHidden();
            return;
        }
        playerHost.show(view, callback);
    }

    private void exitNativeFullScreenFromWeb() {
        if (playerHost != null) playerHost.hideFromWebView();
    }

    // Common exit point used by Back, tab switches, timers and renderer recovery.
    private void exitEmbeddedFullScreen() {
        Log.i("SidiInput", "FULLSCREEN_EXIT_REQUEST " + inputSnapshot());
        if (playerHost != null) playerHost.hideFromApp();
        else embeddedFullScreen = false;
    }

    private void setWebCursorHidden(boolean hidden) {
        // 12.0: Chromium/Android own the system pointer. No cursor injection/hiding.
    }

    private void checkModernWebView() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info == null || info.versionName == null) return;
            String version = info.versionName.trim();
            int dot = version.indexOf('.');
            String majorText = dot > 0 ? version.substring(0, dot) : version;
            int major = Integer.parseInt(majorText);
            Log.i("SidiPlayer", "WebView provider=" + info.packageName + " version=" + version);
            if (major >= 120) return;
            new AlertDialog.Builder(this)
                    .setTitle("Нужно обновить Android System WebView")
                    .setMessage("Установлен WebView " + version
                            + ". Для стабильной работы плеера и fullscreen нужен WebView 120 или новее.")
                    .setPositiveButton("Открыть Google Play", (d, w) -> {
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=com.google.android.webview")));
                        } catch (Throwable ignored) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                                        "https://play.google.com/store/apps/details?id=com.google.android.webview")));
                            } catch (Throwable ignoredAgain) { }
                        }
                    })
                    .setNegativeButton("Продолжить", null)
                    .show();
        } catch (Throwable error) {
            Log.w("SidiPlayer", "WebView version check failed", error);
        }
    }

    private String sleepTimerSettingsLabel() {
        if (sleepTimerEndsAt <= 0L) return "Таймер сна";
        long remaining = Math.max(0L, sleepTimerEndsAt - SystemClock.elapsedRealtime());
        long minutes = Math.max(1L, (remaining + 59_999L) / 60_000L);
        return "Таймер сна: " + minutes + " мин";
    }

    private void showSleepTimerDialog() {
        String[] choices = {"15 минут", "30 минут", "45 минут", "60 минут",
                "Указать время…", "Отключить таймер"};
        new AlertDialog.Builder(this)
                .setTitle("Таймер сна")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) setSleepTimerMinutes(15);
                    else if (which == 1) setSleepTimerMinutes(30);
                    else if (which == 2) setSleepTimerMinutes(45);
                    else if (which == 3) setSleepTimerMinutes(60);
                    else if (which == 4) showCustomSleepTimerDialog();
                    else cancelSleepTimer(true);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showCustomSleepTimerDialog() {
        EditText minutes = new EditText(this);
        minutes.setHint("Минуты");
        minutes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        minutes.setSingleLine(true);
        minutes.setPadding(dp(18), 0, dp(18), 0);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(minutes, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        new AlertDialog.Builder(this)
                .setTitle("Указать таймер")
                .setView(box)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Запустить", (dialog, which) -> {
                    try {
                        int value = Integer.parseInt(minutes.getText().toString().trim());
                        if (value < 1 || value > 1440) throw new IllegalArgumentException();
                        setSleepTimerMinutes(value);
                    } catch (Throwable error) {
                        toast("Введите число от 1 до 1440 минут");
                    }
                })
                .show();
    }

    private void setSleepTimerMinutes(int minutes) {
        sleepTimerEndsAt = SystemClock.elapsedRealtime() + minutes * 60_000L;
        sleepTimerWarningShown = false;
        handler.removeCallbacks(sleepTimerTicker);
        handler.post(sleepTimerTicker);
        toast("Таймер сна: " + minutes + " мин");
    }

    private void cancelSleepTimer(boolean notify) {
        sleepTimerEndsAt = 0L;
        sleepTimerWarningShown = false;
        handler.removeCallbacks(sleepTimerTicker);
        if (notify) toast("Таймер отключён");
    }

    private void showSleepTimerWarning() {
        if (isFinishing() || !foreground) return;
        new AlertDialog.Builder(this)
                .setTitle("Таймер сна")
                .setMessage("Воспроизведение остановится через минуту.")
                .setPositiveButton("Продолжить просмотр", (dialog, which) -> cancelSleepTimer(false))
                .setNegativeButton("Остановить сейчас", (dialog, which) -> expireSleepTimer())
                .show();
    }

    private void expireSleepTimer() {
        if (sleepTimerEndsAt <= 0L) return;
        sleepTimerEndsAt = 0L;
        sleepTimerWarningShown = false;
        handler.removeCallbacks(sleepTimerTicker);
        BrowserTab tab = activeTab;
        if (tab != null) capturePlayerState(tab);
        WebView web = tab == null ? null : tab.webView;
        if (web != null) {
            try { web.onPause(); } catch (Throwable ignored) { }
        }
        handler.postDelayed(() -> {
            if (embeddedFullScreen) exitEmbeddedFullScreen();
            toast("Таймер сна остановил просмотр");
        }, 250L);
    }

    private void showPlayerProblemDialog() {
        String[] issues = {"Не загружается плеер", "Не работает Play/Pause",
                "Нет звука", "Не открывается полный экран", "Другая проблема"};
        new AlertDialog.Builder(this)
                .setTitle("Проблема с плеером")
                .setSingleChoiceItems(issues, -1, (dialog, which) -> {
                    dialog.dismiss();
                    collectPlayerProblemReport(issues[which]);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void collectPlayerProblemReport(String issue) {
        BrowserTab tab = activeTab;
        MovieRecord record = tab == null ? null : store.get(tab.key);
        String url = tab == null || tab.webView == null ? "" : tab.webView.getUrl();
        String context = "ISSUE=" + issue + "\n"
                + "FULLSCREEN=" + embeddedFullScreen + "\n"
                + "URL=" + (url == null ? "" : url) + "\n"
                + "MOVIE=" + (record == null ? "" : record.title) + "\n"
                + "PLAYER_URL=" + (record == null ? "" : playerUrlFor(record)) + "\n"
                + "RECENT_MEDIA_COMMANDS:\n" + PlayerMediaController.recentCommands();
        Log.e("SidiPlayerReport", context.replace('\n', ' '));
        toast("Собираю отчёт о плеере…");
        DiagnosticsManager.collectAndServe(this, context, new DiagnosticsManager.Callback() {
            @Override
            public void onSuccess(DiagnosticsManager.Result result) {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) {
                    result.stop();
                    return;
                }
                if (activeDiagnosticsResult != null) activeDiagnosticsResult.stop();
                activeDiagnosticsResult = result;
                showDiagnosticsDownloadDialog(result);
            }

            @Override
            public void onError(String message, Throwable error) {
                if (isFinishing() || (Build.VERSION.SDK_INT >= 17 && isDestroyed())) return;
                Log.e("SidiDiagnostics", message, error);
                toast("Не удалось собрать отчёт");
            }
        });
    }

    private void dismissKeyboard() {
        try {
            View focused = getCurrentFocus();
            if (focused == null) focused = queryInput;
            InputMethodManager input = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (input != null && focused != null) input.hideSoftInputFromWindow(focused.getWindowToken(), 0);
            if (queryInput != null) queryInput.clearFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        } catch (Throwable ignored) {
        }
    }

    private String canonicalKinopoiskUrl(String source) {
        Uri uri = Uri.parse(source.trim());
        String host = uri.getHost();
        if (host == null || !(host.equalsIgnoreCase("kinopoisk.ru") || host.equalsIgnoreCase("www.kinopoisk.ru"))) {
            throw new IllegalArgumentException("Это не поддерживаемая ссылка");
        }
        String path = uri.getPath();
        if (path == null || !(path.matches("/(film|series)/\\d+/?"))) {
            throw new IllegalArgumentException("Нужна ссылка вида /film/123/ или /series/123/");
        }
        if (!path.endsWith("/")) path += "/";
        return "https://www.kinopoisk.ru" + path;
    }

    private boolean looksLikeKinopoiskUrl(String value) {
        if (value == null) return false;
        String s = value.toLowerCase(Locale.US).trim();
        return s.contains("kinopoisk.ru/film/") || s.contains("kinopoisk.ru/series/");
    }

    private String extractFallbackTitle(String url) {
        try {
            List<String> parts = Uri.parse(url).getPathSegments();
            if (parts.size() >= 2) return ("series".equals(parts.get(0)) ? "Сериал " : "Фильм ") + parts.get(1);
        } catch (Exception ignored) {
        }
        return "Видео";
    }

    private void loadImage(String rawUrl, String sourceUrl, ImageView target) {
        String id = extractNumericId(sourceUrl);
        String normalized = normalizeImageUrl(rawUrl);
        String cacheKey = normalized.isEmpty() ? (id.isEmpty() ? sourceUrl : "poster:" + id) : normalized;
        Bitmap cached = cacheKey == null ? null : imageCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }
        imagePool.execute(() -> {
            Bitmap bitmap = normalized.isEmpty() ? null : downloadBitmap(normalized);

            // Fallback when the search page did not expose the poster URL.
            if (bitmap == null && !id.isEmpty()) {
                bitmap = downloadBitmap("https://st.kp.yandex.net/images/film_big/" + id + ".jpg");
            }

            if (bitmap != null) {
                long fingerprint = bitmapFingerprint(bitmap);
                String owner = posterFingerprintOwners.putIfAbsent(fingerprint, id.isEmpty() ? normalized : id);
                String currentOwner = id.isEmpty() ? normalized : id;
                if (owner != null && !owner.equals(currentOwner)) {
                    bitmap.recycle();
                    bitmap = null;
                }
            }

            Bitmap ready = bitmap;
            if (ready != null) {
                if (cacheKey != null && !cacheKey.isEmpty()) imageCache.put(cacheKey, ready);
                handler.post(() -> target.setImageBitmap(ready));
            }
        });
    }

    private long bitmapFingerprint(Bitmap bitmap) {
        if (bitmap == null) return 0;
        int w = Math.max(1, bitmap.getWidth());
        int h = Math.max(1, bitmap.getHeight());
        long hash = 1469598103934665603L ^ w ^ ((long) h << 32);
        for (int y = 1; y <= 5; y++) {
            for (int x = 1; x <= 5; x++) {
                int px = Math.min(w - 1, (w * x) / 6);
                int py = Math.min(h - 1, (h * y) / 6);
                hash ^= bitmap.getPixel(px, py);
                hash *= 1099511628211L;
            }
        }
        return hash;
    }

    private Bitmap downloadBitmap(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(9000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            connection.setRequestProperty("Referer", "https://www.kinopoisk.ru/");
            connection.setRequestProperty("Accept", "image/webp,image/apng,image/*,*/*;q=0.8");
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) connection.setRequestProperty("Cookie", cookies);
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;
            try (InputStream in = connection.getInputStream()) {
                return BitmapFactory.decodeStream(in);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String normalizeImageUrl(String rawUrl) {
        if (rawUrl == null) return "";
        String value = rawUrl.trim().replace("&amp;", "&");
        int comma = value.indexOf(',');
        if (comma > 0) value = value.substring(0, comma);
        value = value.trim().split("\\s+")[0];
        if (value.startsWith("//")) value = "https:" + value;
        if (value.startsWith("data:") || value.startsWith("blob:")) return "";
        return value.startsWith("http://") || value.startsWith("https://") ? value : "";
    }

    private String extractNumericId(String value) {
        if (value == null) return "";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:iphone360_|/film/|/series/)(\\d{2,10})").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private TextView text(String value, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void enableImmersiveMode() {
        if (getResources().getConfiguration().screenWidthDp < 600) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.view.WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                    controller.setSystemBarsBehavior(
                            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                                View.SYSTEM_UI_FLAG_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        windowFocused = hasFocus;
        Log.i("SidiInput", "WINDOW_FOCUS hasFocus=" + hasFocus + " " + inputSnapshot());
        if (hasFocus) {
            handler.postDelayed(this::enableImmersiveMode, 180L);
            // DOM polling стартует только после стабилизации окна.
            handler.postDelayed(this::startForegroundWorkIfReady, 220L);
        } else {
            stopForegroundPollingOnly();
        }
    }

    private void startForegroundWorkIfReady() {
        if (!foreground || !windowFocused || !hasWindowFocus()) return;
        if (foregroundWorkStarted) return;
        foregroundWorkStarted = true;
        handler.removeCallbacks(playerStateTicker);
        handler.removeCallbacks(playerControlsTicker);
        handler.postDelayed(playerStateTicker, 800L);
        // Меню озвучки/сезона/серии читаются после загрузки страницы и после выбора.
        // Постоянный DOM polling больше не запускаем: он не нужен для playback и
        // раньше совпадал по времени с input/focus transitions.
    }

    private void stopForegroundPollingOnly() {
        foregroundWorkStarted = false;
        handler.removeCallbacks(playerStateTicker);
        handler.removeCallbacks(playerControlsTicker);
    }

    private ImageButton imageButton(int drawableRes, boolean primary) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableRes);
        button.setColorFilter(primary ? Color.WHITE : (lightTheme ? ACCENT : TEXT));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(primary ? 11 : 10), dp(primary ? 11 : 10), dp(primary ? 11 : 10), dp(primary ? 11 : 10));
        button.setBackground(primary
                ? gradient(Color.rgb(28, 190, 255), Color.rgb(37, 92, 255), 13)
                : roundedStroke(PANEL_2, BORDER, 13, 1));
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(100).start();
            button.setBackground(primary
                    ? gradient(focused ? Color.rgb(72, 211, 255) : Color.rgb(28, 190, 255),
                    focused ? Color.rgb(54, 116, 255) : Color.rgb(37, 92, 255), 13)
                    : roundedStroke(focused ? FOCUS_BG : PANEL_2,
                    focused ? ACCENT : BORDER, 13, focused ? 2 : 1));
        });
        return button;
    }

    private Button playerControlButton(String label) {
        Button button = smallButton(label + "  ▾");
        button.setSingleLine(true);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(12), 0, dp(10), 0);
        button.setVisibility(View.GONE);
        return button;
    }

    private Button actionButton(String label, int drawableRes) {
        Button button = baseButton(label, PANEL_2);
        button.setTextSize(compact ? 12 : 13);
        button.setTextColor(TEXT);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(compact ? 10 : 13), 0, dp(compact ? 10 : 13), 0);
        button.setCompoundDrawablesWithIntrinsicBounds(drawableRes, 0, 0, 0);
        button.setCompoundDrawablePadding(label == null || label.isEmpty() ? 0 : dp(7));
        button.setBackground(roundedStroke(PANEL_2, BORDER, 12, 1));
        button.setOnFocusChangeListener((v, focused) -> button.setBackground(roundedStroke(
                focused ? FOCUS_BG : PANEL_2,
                focused ? ACCENT : BORDER, 12, focused ? 2 : 1)));
        return button;
    }

    private View createTabChip(String title, boolean selected, boolean search, MovieRecord record,
                               View.OnClickListener click, View.OnClickListener closeClick) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(12), 0, closeClick == null ? dp(14) : dp(5), 0);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setOnClickListener(click);
        applyTabChipStyle(chip, selected, false);

        ImageView icon = new ImageView(this);
        if (search || record == null) {
            icon.setImageResource(R.drawable.ic_search);
            icon.setColorFilter(lightTheme ? ACCENT : (selected ? TEXT : MUTED));
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            chip.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        } else {
            icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
            icon.setBackground(rounded(Color.rgb(17, 35, 52), 4));
            icon.setClipToOutline(true);
            loadImage(record.posterUrl, record.sourceUrl, icon);
            LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(dp(22), dp(30));
            chip.addView(icon, posterParams);
        }
        icon.setAlpha(selected ? 1f : 0.86f);

        TextView label = text(title, compact ? 11 : 12, selected ? TEXT : Color.rgb(194, 204, 217));
        label.setSingleLine(true);
        label.setPadding(dp(8), 0, dp(closeClick == null ? 0 : 6), 0);
        chip.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        if (closeClick != null) {
            ImageButton close = new ImageButton(this);
            close.setImageResource(R.drawable.ic_close);
            close.setBackgroundColor(Color.TRANSPARENT);
            close.setPadding(dp(8), dp(8), dp(8), dp(8));
            close.setContentDescription("Закрыть " + title);
            close.setFocusable(true);
            close.setOnClickListener(closeClick);
            close.setOnFocusChangeListener((v, focused) -> close.setBackground(
                    rounded(focused ? Color.rgb(15, 54, 82) : Color.TRANSPARENT, 10)));
            chip.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        }

        chip.setOnFocusChangeListener((v, focused) -> applyTabChipStyle(chip, selected, focused));
        return chip;
    }

    private void applyTabChipStyle(View chip, boolean selected, boolean focused) {
        int fill = focused ? FOCUS_BG
                : (selected ? FOCUS_BG : PANEL_2);
        int stroke = focused || selected ? ACCENT : BORDER;
        chip.setBackground(roundedStroke(fill, stroke, 13, focused ? 2 : 1));
    }

    private Button accentButton(String value) {
        Button b = baseButton(value, ACCENT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f).setDuration(100).start();
            v.setBackground(rounded(focused ? Color.rgb(103, 184, 255) : ACCENT, 12));
        });
        return b;
    }

    private Button smallButton(String value) {
        Button b = baseButton(value, PANEL_2);
        b.setTextSize(compact ? 12 : 13);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(roundedStroke(PANEL_2, BORDER, 10, 1));
        b.setOnFocusChangeListener((v, focused) -> b.setBackground(roundedStroke(
                focused ? FOCUS_BG : PANEL_2,
                focused ? ACCENT : BORDER, 10, focused ? 2 : 1)));
        return b;
    }

    private Button tabButton(String value, boolean selected) {
        Button b = baseButton(value, selected ? FOCUS_BG : PANEL_2);
        b.setTextColor(selected ? TEXT : MUTED);
        b.setTextSize(compact ? 11 : 12);
        b.setPadding(dp(13), 0, dp(13), 0);
        b.setBackground(roundedStroke(selected ? FOCUS_BG : PANEL_2, selected ? ACCENT_DARK : BORDER, 18, 1));
        b.setOnFocusChangeListener((v, focused) -> b.setBackground(roundedStroke(
                focused ? FOCUS_BG : (selected ? FOCUS_BG : PANEL_2),
                focused ? ACCENT : (selected ? ACCENT_DARK : BORDER), 18, focused ? 2 : 1)));
        return b;
    }

    private View navTile(String icon, String label, boolean selected, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnClickListener(listener);
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        tile.setBackground(rounded(selected ? Color.rgb(48, 24, 15) : Color.TRANSPARENT, 14));
        TextView i = text(icon, 22, selected ? ACCENT : Color.rgb(132, 135, 143));
        i.setGravity(Gravity.CENTER);
        tile.addView(i, new LinearLayout.LayoutParams(dp(46), dp(28)));
        TextView l = text(label, 9, selected ? Color.rgb(255, 162, 112) : Color.rgb(126, 129, 137));
        l.setGravity(Gravity.CENTER);
        tile.addView(l, new LinearLayout.LayoutParams(dp(64), dp(18)));
        tile.setOnFocusChangeListener((v, focused) -> tile.setBackground(rounded(focused ? Color.rgb(55, 29, 19) : (selected ? Color.rgb(48, 24, 15) : Color.TRANSPARENT), 14)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(68), dp(62));
        p.setMargins(0, 0, 0, dp(8));
        tile.setLayoutParams(p);
        return tile;
    }

    private View bottomNavTile(String icon, String label, boolean selected, View.OnClickListener listener) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnClickListener(listener);
        TextView i = text(icon, 20, selected ? ACCENT : Color.rgb(126, 129, 137));
        i.setGravity(Gravity.CENTER);
        tile.addView(i, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
        TextView l = text(label, 9, selected ? Color.rgb(255, 155, 105) : Color.rgb(126, 129, 137));
        l.setGravity(Gravity.CENTER);
        tile.addView(l, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
        return tile;
    }

    private Button topNavButton(String value, boolean selected) {
        Button b = baseButton(value, Color.TRANSPARENT);
        b.setTextSize(12);
        b.setTextColor(selected ? Color.rgb(255, 151, 96) : Color.rgb(150, 153, 161));
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(rounded(selected ? Color.rgb(38, 22, 16) : Color.TRANSPARENT, 12));
        return b;
    }

    private Button iconButton(String value) {
        Button b = baseButton(value, PANEL_2);
        b.setTextSize(compact ? 17 : 18);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(roundedStroke(PANEL_2, BORDER, 12, 1));
        b.setOnFocusChangeListener((v, focused) -> b.setBackground(roundedStroke(
                focused ? FOCUS_BG : PANEL_2,
                focused ? ACCENT : BORDER, 12, focused ? 2 : 1)));
        return b;
    }

    private Button baseButton(String value, int color) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextColor(TEXT);
        b.setTextSize(compact ? 14 : 16);
        b.setAllCaps(false);
        b.setFocusable(true);
        b.setMinWidth(0);
        b.setMinHeight(0);
        b.setBackground(rounded(color, 11));
        return b;
    }

    private GradientDrawable gradient(int startColor, int endColor, int radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private GradientDrawable roundedStroke(int color, int strokeColor, int radiusDp, int strokeDp) {
        GradientDrawable d = rounded(color, radiusDp);
        d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String shortTitle(String value) {
        String s = value == null || value.trim().isEmpty() ? "Видео" : value.trim();
        int limit = compact ? 18 : 25;
        return s.length() <= limit ? s : s.substring(0, limit - 1) + "…";
    }

    private int parsePositiveInt(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception e) {
            return 0;
        }
    }

    private void toast(String value) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show();
    }

    private static WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult != null) {
            String contents = scanResult.getContents();
            if (contents == null || contents.trim().isEmpty()) {
                toast("Сканирование отменено");
            } else {
                importFromInput(contents, null);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (embeddedFullScreen) {
            exitEmbeddedFullScreen();
            return;
        }
        if (activeTab != null) {
            if (activeTab.webView != null && activeTab.webView.canGoBack()) activeTab.webView.goBack();
            else showSearch();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        windowFocused = hasWindowFocus();
        UpdateManager.resumePendingInstall(this);
        // Возобновляем движок только для активной вкладки. Остальные WebView остаются
        // на паузе, пока их не покажут.
        if (activeTab != null && activeTab.webView != null) {
            try {
                activeTab.webView.onResume();
                activeTab.webView.resumeTimers();
            } catch (Throwable ignored) {
            }
        }
        handler.postDelayed(this::startForegroundWorkIfReady, 220L);
        // Таймер сна привязан к абсолютному времени (elapsedRealtime), поэтому после
        // возврата из фона тик сразу пересчитает остаток и при необходимости сработает.
        if (sleepTimerEndsAt > 0L) {
            handler.removeCallbacks(sleepTimerTicker);
            handler.post(sleepTimerTicker);
        }
    }

    @Override
    protected void onPause() {
        foreground = false;
        windowFocused = false;
        stopForegroundPollingOnly();
        if (activeTab != null) capturePlayerState(activeTab);
        saveTabs();
        CookieManager.getInstance().flush();
        // Иначе видео/звук продолжают проигрываться за кадром после HOME, а
        // playerStateTicker вхолостую гоняет evaluateJavascript в фоне.
        handler.removeCallbacks(playerStateTicker);
        handler.removeCallbacks(playerControlsTicker);
        // Таймер сна тоже снимаем: его тик показывает AlertDialog, а из фоновой
        // активности это ловит BadTokenException. Остаток восстановится в onResume.
        handler.removeCallbacks(sleepTimerTicker);
        // resumeTimers() глобален для процесса, поэтому паузим таймеры только когда
        // приложение реально уходит в фон, а не при простом showTab.
        if (activeTab != null && activeTab.webView != null) {
            try {
                activeTab.webView.onPause();
                activeTab.webView.pauseTimers();
            } catch (Throwable ignored) {
            }
        }
        super.onPause();
    }

    private void installInputDiagnostics() {
        final View decor = getWindow().getDecorView();
        ViewTreeObserver observer = decor.getViewTreeObserver();
        inputFocusListener = (oldFocus, newFocus) -> Log.i("SidiInput",
                "FOCUS_CHANGE old=" + describeView(oldFocus)
                        + " new=" + describeView(newFocus)
                        + " " + inputSnapshot());
        inputTouchModeListener = inTouchMode -> Log.i("SidiInput",
                "TOUCH_MODE changed=" + inTouchMode + " " + inputSnapshot());
        try {
            observer.addOnGlobalFocusChangeListener(inputFocusListener);
            observer.addOnTouchModeChangeListener(inputTouchModeListener);
        } catch (Throwable error) {
            Log.w("SidiInput", "cannot install focus/touch-mode diagnostics", error);
        }
        Log.i("SidiInput", "DIAGNOSTICS_INSTALLED " + inputSnapshot());
    }

    private void removeInputDiagnostics() {
        try {
            View decor = getWindow().getDecorView();
            ViewTreeObserver observer = decor.getViewTreeObserver();
            if (observer.isAlive()) {
                if (inputFocusListener != null) observer.removeOnGlobalFocusChangeListener(inputFocusListener);
                if (inputTouchModeListener != null) observer.removeOnTouchModeChangeListener(inputTouchModeListener);
            }
        } catch (Throwable ignored) {
        }
        inputFocusListener = null;
        inputTouchModeListener = null;
    }

    private String inputSnapshot() {
        View decor = null;
        try { decor = getWindow().getDecorView(); } catch (Throwable ignored) {}
        BrowserTab tab = activeTab;
        WebView web = tab == null ? null : tab.webView;
        View current = null;
        try { current = getCurrentFocus(); } catch (Throwable ignored) {}
        return "focus=" + describeView(current)
                + " windowFocus=" + (decor != null && decor.hasWindowFocus())
                + " touchMode=" + (decor != null && decor.isInTouchMode())
                + " embeddedFullscreen=" + embeddedFullScreen
                + " web=" + describeView(web)
                + " webHasFocus=" + (web != null && web.hasFocus())
                + " pointerInside=" + (tab != null && tab.pointerInsidePlayer)
                + " pointerAgeMs=" + (tab == null || tab.lastPointerAt <= 0L
                    ? -1L : Math.max(0L, SystemClock.elapsedRealtime() - tab.lastPointerAt));
    }

    private String describeView(View view) {
        if (view == null) return "null";
        String id = "no-id";
        try {
            if (view.getId() != View.NO_ID) id = getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            try { id = String.valueOf(view.getId()); } catch (Throwable ignored2) {}
        }
        return view.getClass().getSimpleName() + "#" + id
                + "[shown=" + view.isShown()
                + ",focused=" + view.isFocused()
                + ",focusable=" + view.isFocusable()
                + ",focusableTouch=" + view.isFocusableInTouchMode() + "]";
    }

    private void logKey(String phase, KeyEvent event, Boolean handled, String route) {
        if (event == null) return;
        if (!VERBOSE_INPUT_LOGS
                && event.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                && event.getKeyCode() != KeyEvent.KEYCODE_BACK) return;
        String action = event.getAction() == KeyEvent.ACTION_DOWN ? "DOWN"
                : event.getAction() == KeyEvent.ACTION_UP ? "UP" : String.valueOf(event.getAction());
        Log.i("SidiInput", phase
                + " code=" + event.getKeyCode()
                + " name=" + KeyEvent.keyCodeToString(event.getKeyCode())
                + " action=" + action
                + " repeat=" + event.getRepeatCount()
                + " device=" + event.getDeviceId()
                + " source=0x" + Integer.toHexString(event.getSource())
                + (handled == null ? "" : " handled=" + handled)
                + (route == null || route.isEmpty() ? "" : " " + route)
                + " " + inputSnapshot());
    }

    private void logMotion(String phase, MotionEvent event, Boolean handled) {
        if (event == null || !VERBOSE_INPUT_LOGS) return;
        int action = event.getActionMasked();
        int tool = event.getPointerCount() > 0 ? event.getToolType(0) : MotionEvent.TOOL_TYPE_UNKNOWN;
        float x = event.getPointerCount() > 0 ? event.getX(0) : -1f;
        float y = event.getPointerCount() > 0 ? event.getY(0) : -1f;
        Log.i("SidiInput", phase
                + " action=" + MotionEvent.actionToString(action)
                + " source=0x" + Integer.toHexString(event.getSource())
                + " tool=" + tool
                + " buttons=0x" + Integer.toHexString(event.getButtonState())
                + " xy=" + x + "," + y
                + (handled == null ? "" : " handled=" + handled)
                + " " + inputSnapshot());
    }

    private final class InputDiagnosticWebView extends WebView {
        private final BrowserTab diagnosticTab;

        InputDiagnosticWebView(Context context, BrowserTab tab) {
            super(context);
            diagnosticTab = tab;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW));
            }
        }

        @Override
        public PointerIcon onResolvePointerIcon(MotionEvent event, int pointerIndex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && event != null
                    && (event.getSource() & android.view.InputDevice.SOURCE_MOUSE)
                    == android.view.InputDevice.SOURCE_MOUSE) {
                return PointerIcon.getSystemIcon(getContext(), PointerIcon.TYPE_ARROW);
            }
            return super.onResolvePointerIcon(event, pointerIndex);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            logKey("WEB_KEY_IN", event, null, "tab=" + (diagnosticTab == null ? "null" : diagnosticTab.key));
            boolean handled = super.dispatchKeyEvent(event);
            logKey("WEB_KEY_OUT", event, handled, "tab=" + (diagnosticTab == null ? "null" : diagnosticTab.key));
            return handled;
        }

        @Override
        public boolean onHoverEvent(MotionEvent event) {
            if (event != null && diagnosticTab != null) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
                    diagnosticTab.pointerInsidePlayer = true;
                    diagnosticTab.lastPointerX = event.getX();
                    diagnosticTab.lastPointerY = event.getY();
                    diagnosticTab.lastPointerAt = SystemClock.elapsedRealtime();
                } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
                    diagnosticTab.pointerInsidePlayer = false;
                }
            }
            boolean shouldLog = VERBOSE_INPUT_LOGS && event != null
                    && (event.getActionMasked() != MotionEvent.ACTION_HOVER_MOVE
                    || SystemClock.elapsedRealtime() - lastInputHoverLogAt >= 500L);
            if (shouldLog) {
                lastInputHoverLogAt = SystemClock.elapsedRealtime();
                logMotion("WEB_HOVER_IN", event, null);
            }
            boolean handled = super.onHoverEvent(event);
            if (shouldLog) logMotion("WEB_HOVER_OUT", event, handled);
            return handled;
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            boolean interesting = event != null && event.getActionMasked() != MotionEvent.ACTION_HOVER_MOVE;
            if (interesting) logMotion("WEB_GENERIC_IN", event, null);
            boolean handled = super.onGenericMotionEvent(event);
            if (interesting) logMotion("WEB_GENERIC_OUT", event, handled);
            return handled;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean interesting = event != null && event.getActionMasked() != MotionEvent.ACTION_MOVE;
            if (interesting) logMotion("WEB_TOUCH_IN", event, null);
            boolean handled = super.onTouchEvent(event);
            if (interesting) logMotion("WEB_TOUCH_OUT", event, handled);
            return handled;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) logKey("ACTIVITY_KEY_IN", event, null, "");
        if (event != null && consumeDuplicateMediaKey(event)) {
            logKey("ACTIVITY_KEY_OUT", event, true, "route=media_normalizer");
            return true;
        }
        boolean handled = super.dispatchKeyEvent(event);
        if (event != null) logKey("ACTIVITY_KEY_OUT", event, handled, "route=super");
        return handled;
    }

    private boolean consumeDuplicateMediaKey(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false;

        final long now = SystemClock.elapsedRealtime();
        final long downTime = event.getDownTime();
        final int source = event.getSource();

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            // Hardware long-press repeats are never a second toggle.
            if (event.getRepeatCount() > 0) {
                suppressedMediaDownTime = downTime;
                return true;
            }

            // On this box Button Mapper emits the real media key first (source 0x301)
            // and then a second synthetic source=0 cycle ~0.7 s later. Suppress that
            // second cycle without delaying the genuine event.
            if (source == 0
                    && lastAcceptedMediaSource != 0
                    && now - lastAcceptedMediaDownAt < MEDIA_SYNTHETIC_DUPLICATE_WINDOW_MS) {
                suppressedMediaDownTime = downTime;
                Log.i("SidiInput", "MEDIA_SYNTHETIC_DUPLICATE_DOWN suppressed downTime="
                        + downTime + " age=" + (now - lastAcceptedMediaDownAt));
                return true;
            }

            // Protect against a fast duplicate hardware DOWN while still allowing a
            // deliberate second press after a normal human interval.
            if (source != 0
                    && lastAcceptedMediaSource != 0
                    && now - lastAcceptedMediaDownAt < MEDIA_HARDWARE_BOUNCE_WINDOW_MS) {
                suppressedMediaDownTime = downTime;
                return true;
            }

            lastAcceptedMediaDownAt = now;
            lastAcceptedMediaSource = source;
            acceptedMediaDownTime = downTime;
            suppressedMediaDownTime = -1L;
            return false;
        }

        if (event.getAction() == KeyEvent.ACTION_UP) {
            if (downTime == suppressedMediaDownTime) {
                suppressedMediaDownTime = -1L;
                return true;
            }

            // A source=0 UP belonging to Button Mapper's delayed duplicate must not
            // reach Chromium if its DOWN was not accepted.
            if (source == 0
                    && lastAcceptedMediaSource != 0
                    && downTime != acceptedMediaDownTime
                    && now - lastAcceptedMediaDownAt < MEDIA_SYNTHETIC_DUPLICATE_WINDOW_MS + 250L) {
                return true;
            }

            if (downTime == acceptedMediaDownTime) {
                acceptedMediaDownTime = -1L;
                // After real pause/resume, hide VPAID/DOM ad shells that Stravers restarts (adtagstartloading).
                BrowserTab tab = activeTab;
                if (tab != null && tab.webView != null) {
                    handler.postDelayed(() -> hideResumeAdContainers(tab.webView), 250L);
                    handler.postDelayed(() -> hideResumeAdContainers(tab.webView), 1200L);
                }
            }
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        if (playerHost != null) {
            playerHost.destroy();
            playerHost = null;
        }
        removeInputDiagnostics();
        searchGeneration++;
        handler.removeCallbacks(playerStateTicker);
        handler.removeCallbacks(playerControlsTicker);
        handler.removeCallbacks(sleepTimerTicker);
        if (searchWebView != null) {
            searchWebView.stopLoading();
            searchWebView.destroy();
        }
        for (BrowserTab tab : tabs) tab.destroy();
        imagePool.shutdownNow();
        ioPool.shutdownNow();
        if (adBlocker != null) adBlocker.shutdown();
        if (activeDiagnosticsResult != null) {
            activeDiagnosticsResult.stop();
            activeDiagnosticsResult = null;
        }
        if (activeTransferResult != null) {
            activeTransferResult.stop();
            activeTransferResult = null;
        }
        super.onDestroy();
    }

    private final class BrowserTab {
        final String key;
        LinearLayout root;
        LinearLayout toolbar;
        TextView toolbarTitle;
        LinearLayout controlsGroup;
        Button translationControl;
        Button seasonControl;
        Button episodeControl;
        Button favoriteButton;
        Button adPickerButton;
        Button markVideoAdButton;
        Button undoAdButton;
        PlayerControlsBridge.State controlsState;
        String controlsSignature = "";
        boolean controlsBridgeInstalled;
        boolean controlsReadInFlight;
        int controlsMissCount;
        WebView webView;
        ProgressBar loadingIndicator;
        TextView interactionShield;
        boolean playerIsolated;
        boolean playerReady;
        boolean playbackResourceSeen;
        /** Keep AD_RESUME / midroll suppress across in-tab page refresh. */
        boolean adsSuppressSticky;
        boolean playerIframeSeen;
        String playerReadyUrl = "";
        int playerLoadGeneration;
        int playerChoiceRestoredGeneration = -1;
        long lastManualPlayerChoiceAt;
        String wrapperUrl = "";
        String directPlayerUrl = "";
        boolean directPlayerPromotionTried;
        boolean directPlayerFallbackUsed;
        long openedAt;
        int mainFrameRetryCount;
        boolean compatibilityFallbackTried;
        boolean pointerInsidePlayer;
        float lastPointerX = -1f;
        float lastPointerY = -1f;
        long lastPointerAt;
        float tapDownX = -1f;
        float tapDownY = -1f;
        long tapDownAt;
        boolean consumeVideoTap;

        BrowserTab(String key) {
            this.key = key;
        }

        View getOrCreateView() {
            if (root == null) return createPlayerView(this);
            refreshPlayerToolbar(this);
            return root;
        }

        void hibernate() {
            if (webView != null) {
                try { webView.onPause(); } catch (Throwable ignored) { }
                ViewGroup parent = (ViewGroup) webView.getParent();
                if (parent != null) parent.removeView(webView);
                try { webView.stopLoading(); } catch (Throwable ignored) { }
                try { webView.destroy(); } catch (Throwable ignored) { }
                webView = null;
            }
            root = null;
            toolbar = null;
            toolbarTitle = null;
            controlsGroup = null;
            translationControl = null;
            seasonControl = null;
            episodeControl = null;
            favoriteButton = null;
            adPickerButton = null;
            markVideoAdButton = null;
            undoAdButton = null;
            controlsState = null;
            controlsSignature = "";
            controlsBridgeInstalled = false;
            controlsReadInFlight = false;
            controlsMissCount = 0;
            loadingIndicator = null;
            interactionShield = null;
            playerIsolated = false;
            playerReady = false;
            playbackResourceSeen = false;
            adsSuppressSticky = false;
            playerIframeSeen = false;
            playerReadyUrl = "";
            playerLoadGeneration = 0;
            wrapperUrl = "";
            directPlayerUrl = "";
            directPlayerPromotionTried = false;
            directPlayerFallbackUsed = false;
            pointerInsidePlayer = false;
            lastPointerX = -1f;
            lastPointerY = -1f;
            lastPointerAt = 0L;
            tapDownX = -1f;
            tapDownY = -1f;
            tapDownAt = 0L;
            consumeVideoTap = false;
        }

        void destroy() {
            hibernate();
        }
    }

    private static final class SearchItem {
        final String title;
        final String year;
        final String url;
        final String poster;
        final String type;

        SearchItem(String title, String year, String url, String poster, String type) {
            this.title = title;
            this.year = year;
            this.url = url;
            this.poster = poster;
            this.type = type;
        }
    }
}
