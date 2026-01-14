package org.adaway.model.vpn;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicLong;

/**
 * VPN statistics tracker for monitoring DNS requests.
 * This class tracks total, blocked, allowed, and redirected DNS requests.
 * Statistics are persisted using SharedPreferences and exposed via LiveData.
 *
 * @author AdAway developers
 */
public class VpnStatistics {
    private static final String PREFS_NAME = "vpn_statistics";
    private static final String KEY_TOTAL_REQUESTS = "total_requests";
    private static final String KEY_BLOCKED_REQUESTS = "blocked_requests";
    private static final String KEY_ALLOWED_REQUESTS = "allowed_requests";
    private static final String KEY_REDIRECTED_REQUESTS = "redirected_requests";

    private static VpnStatistics instance;
    private final Context context;
    private final SharedPreferences preferences;

    // Thread-safe counters
    private final AtomicLong totalRequests;
    private final AtomicLong blockedRequests;
    private final AtomicLong allowedRequests;
    private final AtomicLong redirectedRequests;

    // LiveData for UI updates
    private final MutableLiveData<Long> totalRequestsLiveData;
    private final MutableLiveData<Long> blockedRequestsLiveData;
    private final MutableLiveData<Long> allowedRequestsLiveData;
    private final MutableLiveData<Long> redirectedRequestsLiveData;
    private final MutableLiveData<Float> blockPercentageLiveData;

    private VpnStatistics(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Load persisted values
        this.totalRequests = new AtomicLong(preferences.getLong(KEY_TOTAL_REQUESTS, 0));
        this.blockedRequests = new AtomicLong(preferences.getLong(KEY_BLOCKED_REQUESTS, 0));
        this.allowedRequests = new AtomicLong(preferences.getLong(KEY_ALLOWED_REQUESTS, 0));
        this.redirectedRequests = new AtomicLong(preferences.getLong(KEY_REDIRECTED_REQUESTS, 0));

        // Initialize LiveData
        this.totalRequestsLiveData = new MutableLiveData<>(this.totalRequests.get());
        this.blockedRequestsLiveData = new MutableLiveData<>(this.blockedRequests.get());
        this.allowedRequestsLiveData = new MutableLiveData<>(this.allowedRequests.get());
        this.redirectedRequestsLiveData = new MutableLiveData<>(this.redirectedRequests.get());
        this.blockPercentageLiveData = new MutableLiveData<>(calculateBlockPercentage());
    }

    /**
     * Get the singleton instance of VpnStatistics.
     *
     * @param context The application context
     * @return The VpnStatistics instance
     */
    public static synchronized VpnStatistics getInstance(Context context) {
        if (instance == null) {
            instance = new VpnStatistics(context);
        }
        return instance;
    }

    /**
     * Increment total and blocked request counts.
     */
    public void incrementBlockedRequests() {
        long total = totalRequests.incrementAndGet();
        long blocked = blockedRequests.incrementAndGet();

        persistValues();
        notifyLiveData(total, blocked);

        // Notify VpnService to update notification
        Intent intent = new Intent("org.adaway.STATISTICS_UPDATED");
        context.sendBroadcast(intent);
    }

    /**
     * Increment total and allowed request counts.
     */
    public void incrementAllowedRequests() {
        long total = totalRequests.incrementAndGet();
        long allowed = allowedRequests.incrementAndGet();

        persistValues();
        notifyLiveDataAllowed(total, allowed);
    }

    /**
     * Increment total and redirected request counts.
     */
    public void incrementRedirectedRequests() {
        long total = totalRequests.incrementAndGet();
        long redirected = redirectedRequests.incrementAndGet();

        persistValues();
        notifyLiveDataRedirected(total, redirected);
    }

    /**
     * Reset all statistics to zero.
     */
    public void resetStatistics() {
        totalRequests.set(0);
        blockedRequests.set(0);
        allowedRequests.set(0);
        redirectedRequests.set(0);

        persistValues();

        totalRequestsLiveData.postValue(0L);
        blockedRequestsLiveData.postValue(0L);
        allowedRequestsLiveData.postValue(0L);
        redirectedRequestsLiveData.postValue(0L);
        blockPercentageLiveData.postValue(0f);
    }

    /**
     * Get the total number of DNS requests.
     *
     * @return Total request count
     */
    public long getTotalRequests() {
        return totalRequests.get();
    }

    /**
     * Get the number of blocked DNS requests.
     *
     * @return Blocked request count
     */
    public long getBlockedRequests() {
        return blockedRequests.get();
    }

    /**
     * Get the number of allowed DNS requests.
     *
     * @return Allowed request count
     */
    public long getAllowedRequests() {
        return allowedRequests.get();
    }

    /**
     * Get the number of redirected DNS requests.
     *
     * @return Redirected request count
     */
    public long getRedirectedRequests() {
        return redirectedRequests.get();
    }

    /**
     * Calculate the blocking percentage.
     *
     * @return Percentage of blocked requests (0-100)
     */
    public float calculateBlockPercentage() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0f;
        }
        return (blockedRequests.get() * 100f) / total;
    }

    /**
     * Get LiveData for total requests.
     *
     * @return LiveData of total requests
     */
    public LiveData<Long> getTotalRequestsLiveData() {
        return totalRequestsLiveData;
    }

    /**
     * Get LiveData for blocked requests.
     *
     * @return LiveData of blocked requests
     */
    public LiveData<Long> getBlockedRequestsLiveData() {
        return blockedRequestsLiveData;
    }

    /**
     * Get LiveData for allowed requests.
     *
     * @return LiveData of allowed requests
     */
    public LiveData<Long> getAllowedRequestsLiveData() {
        return allowedRequestsLiveData;
    }

    /**
     * Get LiveData for redirected requests.
     *
     * @return LiveData of redirected requests
     */
    public LiveData<Long> getRedirectedRequestsLiveData() {
        return redirectedRequestsLiveData;
    }

    /**
     * Get LiveData for block percentage.
     *
     * @return LiveData of block percentage
     */
    public LiveData<Float> getBlockPercentageLiveData() {
        return blockPercentageLiveData;
    }

    /**
     * Persist current values to SharedPreferences.
     */
    private void persistValues() {
        preferences.edit()
                .putLong(KEY_TOTAL_REQUESTS, totalRequests.get())
                .putLong(KEY_BLOCKED_REQUESTS, blockedRequests.get())
                .putLong(KEY_ALLOWED_REQUESTS, allowedRequests.get())
                .putLong(KEY_REDIRECTED_REQUESTS, redirectedRequests.get())
                .apply();
    }

    /**
     * Notify LiveData observers after blocking a request.
     */
    private void notifyLiveData(long total, long blocked) {
        totalRequestsLiveData.postValue(total);
        blockedRequestsLiveData.postValue(blocked);
        blockPercentageLiveData.postValue(calculateBlockPercentage());
    }

    /**
     * Notify LiveData observers after allowing a request.
     */
    private void notifyLiveDataAllowed(long total, long allowed) {
        totalRequestsLiveData.postValue(total);
        allowedRequestsLiveData.postValue(allowed);
        blockPercentageLiveData.postValue(calculateBlockPercentage());
    }

    /**
     * Notify LiveData observers after redirecting a request.
     */
    private void notifyLiveDataRedirected(long total, long redirected) {
        totalRequestsLiveData.postValue(total);
        redirectedRequestsLiveData.postValue(redirected);
        blockPercentageLiveData.postValue(calculateBlockPercentage());
    }
}
