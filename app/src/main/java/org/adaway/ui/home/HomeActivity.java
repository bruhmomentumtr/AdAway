package org.adaway.ui.home;

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HALF_EXPANDED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN;
import static org.adaway.model.adblocking.AdBlockMethod.UNDEFINED;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.Animations.removeView;
import static org.adaway.ui.Animations.showView;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.TAB;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.VpnService;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.databinding.HomeActivityBinding;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostError;
import org.adaway.model.vpn.VpnStatistics;
import org.adaway.ui.help.HelpActivity;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.lists.ListsActivity;
import org.adaway.ui.log.LogActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.support.SupportActivity;
import org.adaway.ui.update.UpdateActivity;
import org.adaway.ui.welcome.WelcomeActivity;
import org.adaway.AdAwayApplication;
import org.adaway.db.AppDatabase;
import org.adaway.db.dao.HostEntryDao;
import org.adaway.db.entity.ListType;
import org.adaway.model.adblocking.AdBlockModel;
import org.adaway.ui.log.LogEntry;
import org.adaway.util.AppExecutors;
import android.os.Handler;
import android.os.Looper;
import android.widget.AdapterView;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.jvm.functions.Function1;
import timber.log.Timber;

/**
 * This class is the application main activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class HomeActivity extends AppCompatActivity {
    /**
     * The project link.
     */
    private static final String PROJECT_LINK = "https://github.com/bruhmomentumtr/AdAway";

    private HomeActivityBinding binding;
    private BottomSheetBehavior<View> drawerBehavior;
    private OnBackPressedCallback onBackPressedCallback;
    private HomeViewModel homeViewModel;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;

    // Recent logs
    private final LinkedList<LogEntry> recentLogs = new LinkedList<>();
    private int currentFilterMode = 0; // 0=All, 1=Blocked, 2=Allowed

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateNotifications(this);
        Timber.i("Starting main activity");
        this.binding = HomeActivityBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        this.homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        this.homeViewModel.isAdBlocked().observe(this, this::notifyAdBlocked);
        this.homeViewModel.getError().observe(this, this::notifyError);

        applyActionBar();
        bindAppVersion();
        bindHostCounter();
        bindSourceCounter();
        bindStatistics();
        bindRecentLogs();
        bindPending();
        bindState();
        bindClickListeners();
        setUpBottomDrawer();
        bindFab();

        this.binding.navigationView.setNavigationItemSelectedListener(item -> {
            if (showFragment(item.getItemId())) {
                this.drawerBehavior.setState(STATE_HIDDEN);
            }
            return false; // TODO Handle selection
        });

        this.prepareVpnLauncher = registerForActivityResult(new StartActivityForResult(), result -> {

        });

        if (savedInstanceState == null) {
            checkUpdateAtStartup();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkFirstStep();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return showFragment(item.getItemId());
    }

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        Intent prepareIntent;
        if (adBlockMethod == UNDEFINED) {
            // Start welcome activity
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        } else if (adBlockMethod == VPN && (prepareIntent = VpnService.prepare(this)) != null) {
            // Prepare VPN
            this.prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private void checkUpdateAtStartup() {
        boolean checkAppUpdateAtStartup = PreferenceHelper.getUpdateCheckAppStartup(this);
        if (checkAppUpdateAtStartup) {
            this.homeViewModel.checkForAppUpdate();
        }
        boolean checkUpdateAtStartup = PreferenceHelper.getUpdateCheck(this);
        if (checkUpdateAtStartup) {
            this.homeViewModel.update();
        }
    }

    private void applyActionBar() {
        setSupportActionBar(this.binding.bar);
    }

    private void bindAppVersion() {
        TextView versionTextView = this.binding.content.versionTextView;
        versionTextView.setText(this.homeViewModel.getVersionName());
        versionTextView.setOnClickListener(this::showUpdate);

        this.homeViewModel.getAppManifest().observe(
                this,
                manifest -> {
                    if (manifest.updateAvailable) {
                        versionTextView.setTypeface(versionTextView.getTypeface(), Typeface.BOLD);
                        versionTextView.setText(R.string.update_available);
                    }
                });
    }

    private void bindHostCounter() {
        Function1<Integer, CharSequence> stringMapper = count -> Integer.toString(count);

        TextView blockedHostCountTextView = this.binding.content.blockedHostCounterTextView;
        LiveData<Integer> blockedHostCount = this.homeViewModel.getBlockedHostCount();
        Transformations.map(blockedHostCount, stringMapper).observe(this, blockedHostCountTextView::setText);

        TextView allowedHostCountTextView = this.binding.content.allowedHostCounterTextView;
        LiveData<Integer> allowedHostCount = this.homeViewModel.getAllowedHostCount();
        Transformations.map(allowedHostCount, stringMapper).observe(this, allowedHostCountTextView::setText);

        TextView redirectHostCountTextView = this.binding.content.redirectHostCounterTextView;
        LiveData<Integer> redirectHostCount = this.homeViewModel.getRedirectHostCount();
        Transformations.map(redirectHostCount, stringMapper).observe(this, redirectHostCountTextView::setText);
    }

    private void bindSourceCounter() {
        Resources resources = getResources();

        TextView upToDateSourcesTextView = this.binding.content.upToDateSourcesTextView;
        LiveData<Integer> upToDateSourceCount = this.homeViewModel.getUpToDateSourceCount();
        upToDateSourceCount.observe(this, count -> upToDateSourcesTextView
                .setText(resources.getQuantityString(R.plurals.up_to_date_source_label, count, count)));

        TextView outdatedSourcesTextView = this.binding.content.outdatedSourcesTextView;
        LiveData<Integer> outdatedSourceCount = this.homeViewModel.getOutdatedSourceCount();
        outdatedSourceCount.observe(this, count -> outdatedSourcesTextView
                .setText(resources.getQuantityString(R.plurals.outdated_source_label, count, count)));
    }

    private void bindPending() {
        this.homeViewModel.getPending().observe(this, pending -> {
            if (pending) {
                showView(this.binding.content.sourcesProgressBar);
                showView(this.binding.content.stateTextView);
            } else {
                removeView(this.binding.content.sourcesProgressBar);
            }
        });
    }

    private void bindState() {
        this.homeViewModel.getState().observe(this, text -> {
            this.binding.content.stateTextView.setText(text);
            if (text.isEmpty()) {
                removeView(this.binding.content.stateTextView);
            } else {
                showView(this.binding.content.stateTextView);
            }
        });
    }

    private void bindClickListeners() {
        this.binding.content.blockedHostCardView.setOnClickListener(v -> startHostListActivity(BLOCKED_HOSTS_TAB));
        this.binding.content.allowedHostCardView.setOnClickListener(v -> startHostListActivity(ALLOWED_HOSTS_TAB));
        this.binding.content.redirectHostCardView.setOnClickListener(v -> startHostListActivity(REDIRECTED_HOSTS_TAB));
        this.binding.content.sourcesCardView.setOnClickListener(this::startHostsSourcesActivity);
        this.binding.content.checkForUpdateImageView.setOnClickListener(v -> this.homeViewModel.update());
        this.binding.content.updateImageView.setOnClickListener(v -> this.homeViewModel.sync());
        // İstatistik kartına tıklanınca LogActivity aç
        this.binding.content.statisticsCardView.setOnClickListener(this::startDnsLogActivity);

        // Refresh recent logs button (Manual refresh)
        this.binding.content.refreshRecentLogsButton.setOnClickListener(v -> {
            // Re-fetch everything if needed, or just clear/reset
            // For now, let's make it verify the current list
            updateLogDisplay();
        });

        // Reset statistics button
        this.binding.content.resetStatisticsButton.setOnClickListener(v -> resetStatistics());

        // Navigation
        this.binding.content.statisticsCardView.setOnClickListener(this::startDnsLogActivity);

        // Filter Spinner
        this.binding.content.logsFilterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFilterMode = position;
                updateLogDisplay();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setUpBottomDrawer() {
        this.drawerBehavior = BottomSheetBehavior.from(this.binding.bottomDrawer);
        this.drawerBehavior.setState(STATE_HIDDEN);

        this.onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                // Hide drawer if expanded
                HomeActivity.this.drawerBehavior.setState(STATE_HIDDEN);
                HomeActivity.this.onBackPressedCallback.setEnabled(false);
            }
        };
        getOnBackPressedDispatcher().addCallback(this.onBackPressedCallback);

        this.binding.bar.setNavigationOnClickListener(v -> {
            this.drawerBehavior.setState(STATE_HALF_EXPANDED);
            this.onBackPressedCallback.setEnabled(true);
        });
        // this.binding.bar.setNavigationIcon(R.drawable.ic_menu_24dp);
        // this.binding.bar.replaceMenu(R.menu.next_actions);
    }

    private void bindFab() {
        this.binding.fab.setOnClickListener(v -> this.homeViewModel.toggleAdBlocking());
    }

    private boolean showFragment(@IdRes int actionId) {
        if (actionId == R.id.drawer_preferences) {
            startPrefsActivity();
            this.drawerBehavior.setState(STATE_HIDDEN);
            return true;
        } else if (actionId == R.id.drawer_help) {
            startHelpActivity(null);
            this.drawerBehavior.setState(STATE_HIDDEN);
            return true;
        } else if (actionId == R.id.drawer_github_project) {
            showProjectPage();
            this.drawerBehavior.setState(STATE_HIDDEN);
            return true;
        }
        return false;
    }

    /**
     * Start hosts lists activity.
     *
     * @param tab The tab to show.
     */
    private void startHostListActivity(int tab) {
        Intent intent = new Intent(this, ListsActivity.class);
        intent.putExtra(TAB, tab);
        startActivity(intent);
    }

    /**
     * Start hosts source activity.
     *
     * @param view The event source view.
     */
    private void startHostsSourcesActivity(View view) {
        startActivity(new Intent(this, HostsSourcesActivity.class));
    }

    /**
     * Start help activity.
     *
     * @param view The source event view.
     */
    private void startHelpActivity(View view) {
        startActivity(new Intent(this, HelpActivity.class));
    }

    /**
     * Show development project page.
     */
    private void showProjectPage() {
        // Show development page
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_LINK));
        startActivity(browserIntent);
    }

    /**
     * Show support activity.
     *
     * @param view The source event view.
     */
    private void showSupportActivity(View view) {
        startActivity(new Intent(this, SupportActivity.class));
    }

    /**
     * Start preferences activity.
     */
    private void startPrefsActivity() {
        startActivity(new Intent(this, PrefsActivity.class));
    }

    /**
     * Start DNS log activity.
     *
     * @param view The source event view.
     */
    private void startDnsLogActivity(View view) {
        startActivity(new Intent(this, LogActivity.class));
    }

    /**
     * Bind statistics LiveData to UI.
     */
    private void bindStatistics() {
        VpnStatistics vpnStatistics = VpnStatistics.getInstance(this);

        // Total requests
        vpnStatistics.getTotalRequestsLiveData().observe(this,
                count -> this.binding.content.totalRequestsCountTextView.setText(String.format("%,d", count)));

        // Blocked requests
        vpnStatistics.getBlockedRequestsLiveData().observe(this,
                count -> this.binding.content.blockedRequestsCountTextView.setText(String.format("%,d", count)));

        // Block percentage
        vpnStatistics.getBlockPercentageLiveData().observe(this,
                percentage -> this.binding.content.blockPercentageTextView
                        .setText(String.format("%.1f%%", percentage)));
    }

    /**
     * Reset all statistics with confirmation dialog.
     */
    private void resetStatistics() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_statistics_confirm_title)
                .setMessage(R.string.reset_statistics_confirm)
                .setPositiveButton(R.string.button_yes, (dialog, id) -> {
                    VpnStatistics vpnStatistics = VpnStatistics.getInstance(this);
                    vpnStatistics.resetStatistics();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.button_no, (dialog, id) -> dialog.dismiss())
                .create()
                .show();
    }

    private void notifyAdBlocked(boolean adBlocked) {
        int color = adBlocked ? getResources().getColor(R.color.primary, null) : Color.GRAY;
        this.binding.content.headerFrameLayout.setBackgroundColor(color);
        this.binding.fab.setImageResource(adBlocked ? R.drawable.ic_pause_24dp : R.drawable.logo);
    }

    private void notifyError(HostError error) {
        removeView(this.binding.content.stateTextView);
        if (error == null) {
            return;
        }

        String message = getString(error.getDetailsKey()) + "\n\n" + getString(R.string.error_dialog_help);
        new MaterialAlertDialogBuilder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(error.getMessageKey())
                .setMessage(message)
                .setPositiveButton(R.string.button_close, (dialog, id) -> dialog.dismiss())
                .setNegativeButton(R.string.button_help, (dialog, id) -> {
                    dialog.dismiss();
                    startActivity(new Intent(this, HelpActivity.class));
                })
                .create()
                .show();
    }

    private void showUpdate(View view) {
        Intent intent = new Intent(this, UpdateActivity.class);
        startActivity(intent);
    }

    /**
     * Bind recent DNS logs to statistics card with periodic updates.
     */
    /**
     * Bind recent DNS logs to statistics card with real-time updates.
     */
    private void bindRecentLogs() {
        AdBlockModel adBlockModel = ((AdAwayApplication) getApplication()).getAdBlockModel();

        // Observe last log entry for real-time updates
        adBlockModel.getLastLog().observe(this, host -> {
            if (host != null) {
                addLogEntry(host);
            }
        });
    }

    /**
     * Add a process a new log entry.
     */
    private void addLogEntry(String host) {
        AppExecutors.getInstance().diskIO().execute(() -> {
            try {
                HostEntryDao hostEntryDao = AppDatabase.getInstance(this).hostEntryDao();
                ListType type = hostEntryDao.getTypeOfHost(host);

                LogEntry entry = new LogEntry(host, type);

                runOnUiThread(() -> {
                    // Add to beginning
                    recentLogs.addFirst(entry);
                    // Keep max 50
                    if (recentLogs.size() > 50) {
                        recentLogs.removeLast();
                    }
                    updateLogDisplay();
                });
            } catch (Exception e) {
                Timber.e(e, "Error processing log entry");
            }
        });
    }

    /**
     * Update recent logs display based on filter.
     */
    private void updateLogDisplay() {
        // Filter list
        List<LogEntry> filteredList = recentLogs.stream()
                .filter(entry -> {
                    if (currentFilterMode == 1)
                        return entry.getType() == ListType.BLOCKED; // Blocked
                    if (currentFilterMode == 2)
                        return entry.getType() == ListType.ALLOWED || entry.getType() == null; // Allowed
                    return true; // All
                })
                .limit(5)
                .collect(Collectors.toList());

        if (filteredList.isEmpty()) {
            binding.content.recentLogsList.setText(R.string.no_recent_logs);
            return;
        }

        String logText = filteredList.stream()
                .map(e -> {
                    String prefix = (e.getType() == ListType.BLOCKED) ? "🚫 " : "✅ ";
                    return prefix + e.getHost();
                })
                .collect(Collectors.joining("\n"));

        binding.content.recentLogsList.setText(logText);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
