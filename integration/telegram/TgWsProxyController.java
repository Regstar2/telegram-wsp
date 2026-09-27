package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.github.regstar2.tgwsproxy.core.TgWsProxyActionResult;
import io.github.regstar2.tgwsproxy.core.TgWsProxyAwgProbeResult;
import io.github.regstar2.tgwsproxy.core.TgWsProxyConfig;
import io.github.regstar2.tgwsproxy.core.TgWsProxyConsumerWarpHttpResult;
import io.github.regstar2.tgwsproxy.core.TgWsProxyCore;
import io.github.regstar2.tgwsproxy.core.TgWsProxyOperationResult;
import io.github.regstar2.tgwsproxy.core.TgWsProxyStatus;
import io.github.regstar2.tgwsproxy.core.TgWsProxyWireGuardKeyPair;
import io.github.regstar2.tgwsproxy.core.TgWsProxyWorkerHealthResult;

/**
 * Telegram-specific adapter for the reusable TgWsProxy core.
 *
 * UI code talks only to this class. Native/JNA details stay inside tgwsproxy-core.
 */
public final class TgWsProxyController {
    public static final String ROUTE_AUTO = "auto";
    public static final String ROUTE_CF_PROXY = "cf_proxy";
    public static final String ROUTE_AWG = "awg_warp";
    public static final String ROUTE_WORKER = "cf_worker";
    public static final String ROUTE_DIRECT = "direct";

    private static final String PREFS = "tgwsproxy";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_SECRET = "secret";
    private static final String KEY_RUNTIME_CONFIG = "runtime_config";
    private static final String KEY_MANAGED_PROXY = "managed_proxy";
    private static final String KEY_ROUTE_MODE = "route_mode";
    private static final String KEY_CF_MANUAL = "cf_manual_domains";
    private static final String KEY_CF_CACHED = "cf_cached_domains";
    private static final String KEY_CF_LAST_CHECK = "cf_last_check";
    private static final String KEY_CF_WORKING = "cf_working";
    private static final String KEY_CF_TOTAL = "cf_total";
    private static final String KEY_PROXY_WORKERS = "proxy_workers";
    private static final String KEY_PROXY_WORKERS_WORKING = "proxy_workers_working";
    private static final String KEY_PROXY_WORKERS_LAST_CHECK = "proxy_workers_last_check";
    private static final String KEY_AWG_WORKERS = "awg_workers";
    private static final String KEY_AWG_WORKERS_WORKING = "awg_workers_working";
    private static final String KEY_AWG_WORKERS_LAST_CHECK = "awg_workers_last_check";
    private static final String KEY_AWG_PROFILE_NAME = "awg_profile_name";
    private static final String KEY_AWG_PROFILE_READY = "awg_profile_ready";
    private static final String KEY_AWG_PROFILE_HEALTH = "awg_profile_health";
    private static final String KEY_AWG_PROFILE_LAST_CHECK = "awg_profile_last_check";

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 1443;
    private static final int HTTPS_TIMEOUT_MS = 4_000;
    private static final int SMALL_RESPONSE_LIMIT = 256 * 1024;
    private static final String CF_UPSTREAM_URL =
            "https://raw.githubusercontent.com/Flowseal/tg-ws-proxy/main/.github/cfproxy-domains.txt";
    private static final String TELEGRAM_AWG_PROBE_TARGET = "149.154.175.50:443";
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final Object RUNTIME_LOCK = new Object();

    private static final Set<String> FLOWSEAL_ENCODED = new LinkedHashSet<>(Arrays.asList(
            "virkgj.com", "vmmzovy.com", "mkuosckvso.com", "zaewayzmplad.com", "twdmbzcm.com",
            "awzwsldi.com", "clngqrflngqin.com", "tjacxbqtj.com", "bxaxtxmrw.com", "dmohrsgmohcrwb.com"
    ));

    // Keep these provisioning-only defaults in sync with
    // Regstar2/tg-ws-proxy-android BuiltInWarpProvisioningWorkers.
    // They are never added to the Telegram cf_worker_ws pool.
    private static final List<String> BUILT_IN_AWG_WORKERS = Arrays.asList(
            "floral-surf-cc2c.awpmxkmo.workers.dev",
            "lucky-frog-795f.ixmxdpw8.workers.dev",
            "steep-snow-3ae9.4048pm01.workers.dev"
    );

    private static final String AWG_I1 =
            "<r 2><b 0x858000010001000000000669636c6f756403636f6d0000010001c00c000100010000105a00044d583737>";

    private TgWsProxyController() {
    }

    public interface Callback {
        void onComplete(boolean success, String message);
    }

    public static final class UiState {
        public final boolean enabled;
        public final String routeMode;
        public final String status;
        public final String actualBackend;
        public final String lastError;
        public final String manualCfDomains;
        public final String cfSummary;
        public final String awgSummary;
        public final String proxyWorkers;
        public final String awgWorkers;
        public final String workerSummary;

        UiState(
                boolean enabled,
                String routeMode,
                String status,
                String actualBackend,
                String lastError,
                String manualCfDomains,
                String cfSummary,
                String awgSummary,
                String proxyWorkers,
                String awgWorkers,
                String workerSummary
        ) {
            this.enabled = enabled;
            this.routeMode = routeMode;
            this.status = status;
            this.actualBackend = actualBackend;
            this.lastError = lastError;
            this.manualCfDomains = manualCfDomains;
            this.cfSummary = cfSummary;
            this.awgSummary = awgSummary;
            this.proxyWorkers = proxyWorkers;
            this.awgWorkers = awgWorkers;
            this.workerSummary = workerSummary;
        }
    }

    public static void start(Context context) {
        startManaged(context.getApplicationContext());
    }

    public static UiState getUiState(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = prefs(appContext);
        ensureDefaultAwgWorkers(prefs);
        boolean enabled = prefs.getBoolean(KEY_ENABLED, true);
        TgWsProxyStatus status = null;
        try {
            status = TgWsProxyCore.INSTANCE.status();
        } catch (Throwable ignore) {
        }

        String runtimeState = enabled ? "Запускается" : "Выключен";
        String actualBackend = "";
        String lastError = "";
        if (status != null) {
            runtimeState = runtimeStateLabel(status.getState().name());
            actualBackend = backendLabel(status.getActualBackend());
            lastError = safeStatusText(status.getLastError());
        }
        if (!actualBackend.isEmpty() && enabled) {
            runtimeState += " · " + actualBackend;
        }

        String manual = prefs.getString(KEY_CF_MANUAL, "");
        String proxyWorkers = prefs.getString(KEY_PROXY_WORKERS, "");
        String awgWorkers = awgWorkersText(prefs);
        int proxyCount = splitStoredHosts(proxyWorkers).size();
        int awgCount = splitStoredHosts(awgWorkers).size();

        return new UiState(
                enabled,
                sanitizeRouteMode(prefs.getString(KEY_ROUTE_MODE, ROUTE_AUTO)),
                runtimeState,
                actualBackend,
                lastError,
                manual == null ? "" : manual,
                cfSummary(prefs),
                awgSummary(appContext, prefs),
                proxyWorkers == null ? "" : proxyWorkers,
                awgWorkers == null ? "" : awgWorkers,
                "Proxy: " + proxyCount + " · Amnezia: " + awgCount
        );
    }

    public static void setEnabledAsync(Context context, boolean enabled, Callback callback) {
        Context appContext = context.getApplicationContext();
        prefs(appContext).edit().putBoolean(KEY_ENABLED, enabled).apply();
        runAsync("TgWsProxyToggle", () -> {
            if (enabled) {
                restartManaged(appContext);
                complete(callback, true, "ok");
            } else {
                stopManaged(appContext);
                complete(callback, true, "ok");
            }
        });
    }

    public static void setRouteModeAsync(Context context, String routeMode, Callback callback) {
        Context appContext = context.getApplicationContext();
        String normalized = sanitizeRouteMode(routeMode);
        prefs(appContext).edit().putString(KEY_ROUTE_MODE, normalized).apply();
        restartIfEnabledAsync(appContext, "TgWsProxyRoute", callback);
    }

    public static String setManualCfDomains(Context context, String raw) {
        HostListValidation validation = normalizeHostList(raw);
        if (!validation.success) {
            return validation.error;
        }
        prefs(context).edit().putString(KEY_CF_MANUAL, validation.normalizedText).apply();
        return "";
    }

    public static void updateAndCheckCfDomainsAsync(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        runAsync("TgWsProxyCFDomains", () -> {
            SharedPreferences prefs = prefs(appContext);
            String updateWarning = "";
            try {
                String downloaded = readHttpsText(CF_UPSTREAM_URL, SMALL_RESPONSE_LIMIT);
                HostListValidation upstream = normalizeHostList(downloaded);
                if (!upstream.success || splitStoredHosts(upstream.normalizedText).size() < 3) {
                    throw new IllegalStateException("upstream_quality_gate");
                }
                prefs.edit().putString(KEY_CF_CACHED, upstream.normalizedText).apply();
            } catch (Throwable error) {
                updateWarning = "upstream_update_failed";
            }

            List<String> checkDomains = new ArrayList<>();
            checkDomains.addAll(splitStoredHosts(prefs.getString(KEY_CF_MANUAL, "")));
            checkDomains.addAll(decodeCachedForHealth(splitStoredHosts(prefs.getString(KEY_CF_CACHED, ""))));
            checkDomains = dedupe(checkDomains);

            int working = 0;
            for (String domain : checkDomains) {
                if (probeHttpsHost(domain)) {
                    working++;
                }
            }
            long now = System.currentTimeMillis();
            prefs.edit()
                    .putInt(KEY_CF_WORKING, working)
                    .putInt(KEY_CF_TOTAL, checkDomains.size())
                    .putLong(KEY_CF_LAST_CHECK, now)
                    .apply();

            restartManagedIfEnabled(appContext);
            String message = working + "/" + checkDomains.size();
            if (!updateWarning.isEmpty()) {
                message += " · " + updateWarning;
            }
            complete(callback, true, message);
        });
    }

    public static void saveAndCheckWorkersAsync(
            Context context,
            boolean amnezia,
            String raw,
            Callback callback
    ) {
        Context appContext = context.getApplicationContext();
        HostListValidation validation = normalizeHostList(raw);
        if (!validation.success) {
            complete(callback, false, validation.error);
            return;
        }

        String key = amnezia ? KEY_AWG_WORKERS : KEY_PROXY_WORKERS;
        prefs(appContext).edit().putString(key, validation.normalizedText).apply();

        runAsync("TgWsProxyWorkers", () -> {
            List<String> workers = splitStoredHosts(validation.normalizedText);
            int working = 0;
            for (String worker : workers) {
                boolean ok;
                if (amnezia) {
                    TgWsProxyWorkerHealthResult health =
                            TgWsProxyCore.INSTANCE.checkConsumerWarpWorker(worker, 5_000L);
                    ok = health.getSuccess();
                } else {
                    ok = probeHttpsHost(worker);
                }
                if (ok) {
                    working++;
                }
            }

            SharedPreferences.Editor editor = prefs(appContext).edit();
            if (amnezia) {
                editor.putInt(KEY_AWG_WORKERS_WORKING, working)
                        .putLong(KEY_AWG_WORKERS_LAST_CHECK, System.currentTimeMillis());
            } else {
                editor.putInt(KEY_PROXY_WORKERS_WORKING, working)
                        .putLong(KEY_PROXY_WORKERS_LAST_CHECK, System.currentTimeMillis());
            }
            editor.apply();

            if (!amnezia) {
                restartManagedIfEnabled(appContext);
            }
            complete(callback, true, working + "/" + workers.size());
        });
    }

    public static void importAwgProfileAsync(
            Context context,
            Uri uri,
            String profileName,
            Callback callback
    ) {
        Context appContext = context.getApplicationContext();
        runAsync("TgWsProxyAwgImport", () -> {
            try {
                String config = readUriText(appContext, uri, SMALL_RESPONSE_LIMIT);
                String name = sanitizeProfileName(profileName, "Imported WARP");
                String error = validateAndStoreAwgConfig(appContext, name, config);
                if (!error.isEmpty()) {
                    complete(callback, false, error);
                    return;
                }
                restartManagedIfEnabled(appContext);
                complete(callback, true, "ok");
            } catch (Throwable error) {
                complete(callback, false, "import_failed");
            }
        });
    }

    public static void provisionAwgProfileAsync(
            Context context,
            String profileName,
            Callback callback
    ) {
        Context appContext = context.getApplicationContext();
        runAsync("TgWsProxyAwgProvision", () -> {
            try {
                TgWsProxyWireGuardKeyPair pair = TgWsProxyCore.INSTANCE.generateWireGuardKeyPair();
                if (!pair.getSuccess() || pair.getPrivateKey().isEmpty() || pair.getPublicKey().isEmpty()) {
                    complete(callback, false, safeCode(pair.getCode(), "key_generation_failed"));
                    return;
                }

                RegistrationResult registration = registerConsumerWarp(appContext, pair.getPublicKey());
                if (registration == null || !registration.result.getSuccess()) {
                    String code = registration == null
                            ? "registration_failed"
                            : safeCode(registration.result.getCode(), "registration_failed");
                    complete(callback, false, code);
                    return;
                }

                JSONObject payload = new JSONObject(registration.result.getBody());
                String registrationId = payload.optString("id", "").trim();
                String token = payload.optString("token", "").trim();
                JSONObject config = payload.optJSONObject("config");
                JSONObject iface = config == null ? null : config.optJSONObject("interface");
                JSONObject addresses = iface == null ? null : iface.optJSONObject("addresses");
                JSONArray peers = config == null ? null : config.optJSONArray("peers");
                JSONObject peer = peers == null || peers.length() == 0 ? null : peers.optJSONObject(0);
                JSONObject endpointObject = peer == null ? null : peer.optJSONObject("endpoint");

                String ipv4 = addresses == null ? "" : addresses.optString("v4", "").trim();
                String ipv6 = addresses == null ? "" : addresses.optString("v6", "").trim();
                String peerKey = peer == null ? "" : peer.optString("public_key", "").trim();
                String endpoint = endpointObject == null ? "" : endpointObject.optString("host", "").trim();
                if (endpoint.isEmpty() && endpointObject != null) {
                    endpoint = endpointObject.optString("v4", "").trim();
                }
                if (registrationId.isEmpty() || token.isEmpty() || peerKey.isEmpty()
                        || endpoint.isEmpty() || (ipv4.isEmpty() && ipv6.isEmpty())) {
                    complete(callback, false, "registration_response_incomplete");
                    return;
                }

                if (!activateConsumerWarp(appContext, registration.transportWorker, registrationId, token)) {
                    complete(callback, false, "activation_failed");
                    return;
                }

                List<String> endpoints = dedupe(Arrays.asList(
                        endpoint,
                        "188.114.98.1:7559",
                        "188.114.98.1:2408",
                        "162.159.195.1:2408"
                ));
                String selectedConfig = "";
                String lastCode = "probe_failed";
                for (String candidateEndpoint : endpoints) {
                    for (int variant = 0; variant < 2; variant++) {
                        String candidate = buildAwgConfig(
                                pair.getPrivateKey(),
                                ipv4,
                                ipv6,
                                peerKey,
                                candidateEndpoint,
                                variant == 0
                        );
                        ProbeResult probe = probeCandidateAwg(appContext, candidate);
                        lastCode = probe.code;
                        if (probe.success) {
                            selectedConfig = candidate;
                            break;
                        }
                    }
                    if (!selectedConfig.isEmpty()) {
                        break;
                    }
                }

                if (selectedConfig.isEmpty()) {
                    complete(callback, false, lastCode);
                    return;
                }

                String name = sanitizeProfileName(profileName, suggestedAwgName(prefs(appContext)));
                saveAwgConfig(appContext, name, selectedConfig);
                restartManagedIfEnabled(appContext);
                complete(callback, true, "ok");
            } catch (Throwable error) {
                complete(callback, false, "profile_creation_failed");
            }
        });
    }

    private static RegistrationResult registerConsumerWarp(Context context, String publicKey) {
        TgWsProxyConsumerWarpHttpResult direct =
                TgWsProxyCore.INSTANCE.registerConsumerWarpDirect(publicKey, 20_000L);
        if (direct.getSuccess()) {
            return new RegistrationResult(direct, null);
        }

        boolean safeFallback = !direct.getRequestSent()
                || "registration_rate_limited".equals(direct.getCode())
                || "registration_server_error".equals(direct.getCode());
        if (!safeFallback) {
            return new RegistrationResult(direct, null);
        }

        for (String worker : splitStoredHosts(awgWorkersText(prefs(context)))) {
            TgWsProxyWorkerHealthResult health =
                    TgWsProxyCore.INSTANCE.checkConsumerWarpWorker(worker, 5_000L);
            if (!health.getSuccess()) {
                continue;
            }
            TgWsProxyConsumerWarpHttpResult result =
                    TgWsProxyCore.INSTANCE.registerConsumerWarpWithWorker(worker, publicKey, 20_000L);
            if (result.getSuccess()) {
                return new RegistrationResult(result, worker);
            }
        }
        return new RegistrationResult(direct, null);
    }

    private static boolean activateConsumerWarp(
            Context context,
            String transportWorker,
            String registrationId,
            String token
    ) {
        if (transportWorker != null) {
            TgWsProxyConsumerWarpHttpResult result =
                    TgWsProxyCore.INSTANCE.activateConsumerWarpWithWorker(
                            transportWorker, registrationId, token, 20_000L
                    );
            if (result.getSuccess()) {
                return true;
            }
        } else {
            TgWsProxyConsumerWarpHttpResult direct =
                    TgWsProxyCore.INSTANCE.activateConsumerWarpDirect(registrationId, token, 20_000L);
            if (direct.getSuccess()) {
                return true;
            }
        }

        for (String worker : splitStoredHosts(awgWorkersText(prefs(context)))) {
            if (worker.equals(transportWorker)) {
                continue;
            }
            TgWsProxyWorkerHealthResult health =
                    TgWsProxyCore.INSTANCE.checkConsumerWarpWorker(worker, 5_000L);
            if (!health.getSuccess()) {
                continue;
            }
            TgWsProxyConsumerWarpHttpResult result =
                    TgWsProxyCore.INSTANCE.activateConsumerWarpWithWorker(
                            worker, registrationId, token, 20_000L
                    );
            if (result.getSuccess()) {
                return true;
            }
        }
        return false;
    }

    private static ProbeResult probeCandidateAwg(Context context, String config) {
        File temp = null;
        try {
            temp = writePrivateTempFile(context, config);
            if (!TgWsProxyCore.INSTANCE.validateAwgWarpConfig(temp.getAbsolutePath())) {
                return new ProbeResult(false, "native_config_validation_failed");
            }
            TgWsProxyAwgProbeResult first =
                    TgWsProxyCore.INSTANCE.probeAwgWarpConfig(
                            temp.getAbsolutePath(), TELEGRAM_AWG_PROBE_TARGET, 15_000L
                    );
            if (!first.getSuccess()) {
                return new ProbeResult(false, safeCode(first.getCode(), "probe_failed"));
            }
            TgWsProxyAwgProbeResult second =
                    TgWsProxyCore.INSTANCE.probeAwgWarpConfig(
                            temp.getAbsolutePath(), TELEGRAM_AWG_PROBE_TARGET, 15_000L
                    );
            if (!second.getSuccess()) {
                return new ProbeResult(false, safeCode(second.getCode(), "probe_confirmation_failed"));
            }
            return new ProbeResult(true, "ok");
        } catch (Throwable error) {
            return new ProbeResult(false, "probe_failed");
        } finally {
            if (temp != null) {
                temp.delete();
            }
        }
    }

    private static String validateAndStoreAwgConfig(Context context, String name, String config) {
        ProbeResult probe = probeCandidateAwg(context, config);
        if (!probe.success) {
            return probe.code;
        }
        try {
            saveAwgConfig(context, name, config);
            return "";
        } catch (Throwable error) {
            return "profile_save_failed";
        }
    }

    private static void saveAwgConfig(Context context, String name, String config) throws Exception {
        File target = selectedAwgConfigFile(context);
        File directory = target.getParentFile();
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("profile_directory_failed");
        }
        File partial = new File(target.getAbsolutePath() + ".part");
        try (FileOutputStream output = new FileOutputStream(partial)) {
            output.write(config.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            throw new IllegalStateException("profile_replace_failed");
        }
        if (!partial.renameTo(target)) {
            throw new IllegalStateException("profile_finalize_failed");
        }
        prefs(context).edit()
                .putString(KEY_AWG_PROFILE_NAME, name)
                .putBoolean(KEY_AWG_PROFILE_READY, true)
                .putString(KEY_AWG_PROFILE_HEALTH, "working")
                .putLong(KEY_AWG_PROFILE_LAST_CHECK, System.currentTimeMillis())
                .apply();
    }

    private static String buildAwgConfig(
            String privateKey,
            String ipv4,
            String ipv6,
            String peerKey,
            String endpoint,
            boolean includeI1
    ) {
        List<String> addresses = new ArrayList<>();
        if (!ipv4.isEmpty()) {
            addresses.add(ipv4.contains("/") ? ipv4 : ipv4 + "/32");
        }
        if (!ipv6.isEmpty()) {
            addresses.add(ipv6.contains("/") ? ipv6 : ipv6 + "/128");
        }

        StringBuilder out = new StringBuilder();
        out.append("[Interface]\n");
        out.append("PrivateKey = ").append(privateKey).append("\n");
        out.append("Address = ").append(join(addresses, ", ")).append("\n");
        out.append("MTU = 1280\n");
        out.append("Jc = 6\n");
        out.append("Jmin = 10\n");
        out.append("Jmax = 50\n");
        if (includeI1) {
            out.append("I1 = ").append(AWG_I1).append("\n");
        }
        out.append("\n[Peer]\n");
        out.append("PublicKey = ").append(peerKey).append("\n");
        out.append("Endpoint = ").append(endpoint).append("\n");
        out.append("AllowedIPs = 0.0.0.0/0, ::/0\n");
        out.append("PersistentKeepalive = 25\n");
        return out.toString();
    }

    private static void startManaged(Context appContext) {
        synchronized (RUNTIME_LOCK) {
            try {
                SharedPreferences prefs = prefs(appContext);
                if (!prefs.getBoolean(KEY_ENABLED, true) || !supportsArm64()) {
                    TgWsProxyCore.INSTANCE.stop();
                    TgWsProxyCore.INSTANCE.resetAwgWarp();
                    disableManagedProxy(appContext, prefs);
                    return;
                }

                prepareAwgRuntime(appContext, prefs);
                String runtimeConfig = buildRuntimeConfig(appContext, prefs);
                prefs.edit().putString(KEY_RUNTIME_CONFIG, runtimeConfig).apply();

                String secret = getOrCreateSecret(prefs);
                TgWsProxyConfig config =
                        new TgWsProxyConfig(HOST, PORT, secret, runtimeConfig, BuildVars.LOGS_ENABLED);
                TgWsProxyOperationResult result = TgWsProxyCore.INSTANCE.start(config);
                if (!result.getSuccess()) {
                    FileLog.e("TgWsProxy core start failed: " + result.getMessage());
                    disableManagedProxy(appContext, prefs);
                    return;
                }

                SharedPreferences telegramPrefs =
                        appContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
                boolean stored = telegramPrefs.edit()
                        .putBoolean("proxy_enabled", true)
                        .putString("proxy_ip", HOST)
                        .putInt("proxy_port", PORT)
                        .putString("proxy_user", "")
                        .putString("proxy_pass", "")
                        .putString("proxy_secret", secret)
                        .commit();
                if (!stored) {
                    TgWsProxyCore.INSTANCE.stop();
                    return;
                }

                prefs.edit().putBoolean(KEY_MANAGED_PROXY, true).apply();
                applyTelegramProxy(true, secret);
            } catch (Throwable error) {
                FileLog.e("TgWsProxy controller start failed: " + error.getClass().getSimpleName());
            }
        }
    }

    private static void stopManaged(Context appContext) {
        synchronized (RUNTIME_LOCK) {
            try {
                TgWsProxyCore.INSTANCE.stop();
            } catch (Throwable ignore) {
            }
            try {
                TgWsProxyCore.INSTANCE.resetAwgWarp();
            } catch (Throwable ignore) {
            }
            disableManagedProxy(appContext, prefs(appContext));
        }
    }

    private static void restartManaged(Context appContext) {
        synchronized (RUNTIME_LOCK) {
            try {
                TgWsProxyCore.INSTANCE.stop();
            } catch (Throwable ignore) {
            }
            startManaged(appContext);
        }
    }

    private static void restartManagedIfEnabled(Context appContext) {
        if (prefs(appContext).getBoolean(KEY_ENABLED, true)) {
            restartManaged(appContext);
        }
    }

    private static void restartIfEnabledAsync(Context appContext, String threadName, Callback callback) {
        runAsync(threadName, () -> {
            restartManagedIfEnabled(appContext);
            complete(callback, true, "ok");
        });
    }

    private static void prepareAwgRuntime(Context context, SharedPreferences prefs) {
        File config = selectedAwgConfigFile(context);
        boolean ready = prefs.getBoolean(KEY_AWG_PROFILE_READY, false)
                && config.isFile()
                && TgWsProxyCore.INSTANCE.validateAwgWarpConfig(config.getAbsolutePath());
        if (ready) {
            TgWsProxyActionResult result =
                    TgWsProxyCore.INSTANCE.configureAwgWarp(
                            config.getAbsolutePath(), true, false, true
                    );
            if (!result.getSuccess()) {
                prefs.edit()
                        .putBoolean(KEY_AWG_PROFILE_READY, false)
                        .putString(KEY_AWG_PROFILE_HEALTH, result.getCode())
                        .apply();
            }
        } else {
            TgWsProxyCore.INSTANCE.resetAwgWarp();
        }
    }

    private static String buildRuntimeConfig(Context context, SharedPreferences prefs) {
        String routeMode = sanitizeRouteMode(prefs.getString(KEY_ROUTE_MODE, ROUTE_AUTO));
        boolean awgReady = prefs.getBoolean(KEY_AWG_PROFILE_READY, false)
                && selectedAwgConfigFile(context).isFile();
        List<String> workers = splitStoredHosts(prefs.getString(KEY_PROXY_WORKERS, ""));
        boolean workerReady = !workers.isEmpty();
        boolean wifi = isWifi(context);

        List<String> order = new ArrayList<>();
        if (ROUTE_AUTO.equals(routeMode)) {
            order.add("cf_proxy_ws");
            if (awgReady) {
                order.add("awg_warp");
            }
            if (workerReady) {
                order.add("cf_worker_ws");
            }
            if (wifi) {
                order.add("direct_ws");
            }
        } else if (ROUTE_CF_PROXY.equals(routeMode)) {
            order.add("cf_proxy_ws");
        } else if (ROUTE_AWG.equals(routeMode)) {
            order.add("awg_warp");
        } else if (ROUTE_WORKER.equals(routeMode)) {
            order.add("cf_worker_ws");
        } else {
            order.add("direct_ws");
        }

        boolean allowDirect = order.contains("direct_ws");
        boolean allowCf = order.contains("cf_proxy_ws");
        boolean allowAwg = order.contains("awg_warp") && awgReady;
        boolean allowWorker = order.contains("cf_worker_ws") && workerReady;

        List<String> tokens = new ArrayList<>();
        tokens.add("@connection_mode=auto");
        tokens.add("@route_direct_ws=" + bool(allowDirect));
        tokens.add("@route_cf_proxy_ws=" + bool(allowCf));
        tokens.add("@route_awg_warp=" + bool(allowAwg));
        tokens.add("@route_worker_ws=" + bool(allowWorker));
        tokens.add("@route_tcp_fallback=0");
        tokens.add("@route_fallback=1");
        tokens.add("@route_order=" + join(order, "|"));
        tokens.add("@preferred_route=" + order.get(0));
        tokens.add("@network_profile_type=" + (wifi ? "wifi" : "mobile"));
        tokens.add("@network_profile_id=" + (wifi ? "telegram_wsp_wifi" : "telegram_wsp_mobile"));
        tokens.add("@mtproto_worker_preconnect=0");

        String manual = pipeList(prefs.getString(KEY_CF_MANUAL, ""));
        if (!manual.isEmpty()) {
            tokens.add("@cf_manual_domains=" + manual);
        }
        String cached = pipeList(prefs.getString(KEY_CF_CACHED, ""));
        if (!cached.isEmpty()) {
            tokens.add("@cf_cached_domains=" + cached);
        }

        if (workerReady) {
            tokens.add("@worker_enabled=1");
            tokens.add("@worker_domain=" + workers.get(0));
            tokens.add("@worker_failover_enabled=" + bool(workers.size() > 1));
            tokens.add("@worker_failover_max_attempts=" + workers.size());
            List<String> encodedWorkers = new ArrayList<>();
            for (int i = 0; i < workers.size(); i++) {
                encodedWorkers.add("w" + (i + 1) + ":" + workers.get(i));
            }
            tokens.add("@worker_failover_candidates=" + join(encodedWorkers, "|"));
        } else {
            tokens.add("@worker_enabled=0");
        }

        return join(tokens, ",");
    }

    private static boolean isWifi(Context context) {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = manager == null ? null : manager.getActiveNetworkInfo();
            return info != null && info.isConnected() && info.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static HostListValidation normalizeHostList(String raw) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String text = raw == null ? "" : raw;
        for (String line : text.split("\\r?\\n")) {
            String value = line.trim().toLowerCase(Locale.US);
            if (value.isEmpty() || value.startsWith("#")) {
                continue;
            }
            if (!isValidHostname(value)) {
                return new HostListValidation(false, "", "invalid_hostname: " + safeHostForMessage(value));
            }
            result.add(value);
        }
        return new HostListValidation(true, join(new ArrayList<>(result), "\n"), "");
    }

    private static boolean isValidHostname(String value) {
        if (value.length() < 3 || value.length() > 253 || !value.contains(".")) {
            return false;
        }
        if (value.contains("://") || value.contains("/") || value.contains(":")
                || value.contains("?") || value.contains("#") || value.contains("@")
                || value.contains(" ") || value.contains("..")) {
            return false;
        }
        String[] labels = value.split("\\.");
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63
                    || label.startsWith("-") || label.endsWith("-")) {
                return false;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '-') {
                    return false;
                }
            }
        }
        return true;
    }

    private static String readHttpsText(String rawUrl, int maxBytes) throws Exception {
        URL url = new URL(rawUrl);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("https_required");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(HTTPS_TIMEOUT_MS);
        connection.setReadTimeout(HTTPS_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", "Telegram-WSP");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("http_" + status);
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IllegalStateException("response_too_large");
                    }
                    output.write(buffer, 0, read);
                }
                return output.toString("UTF-8");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static boolean probeHttpsHost(String host) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://" + host + "/");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(HTTPS_TIMEOUT_MS);
            connection.setReadTimeout(HTTPS_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestProperty("User-Agent", "Telegram-WSP health check");
            connection.getResponseCode();
            return true;
        } catch (Throwable ignore) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static List<String> decodeCachedForHealth(List<String> cached) {
        boolean encoded = false;
        for (String value : cached) {
            if (FLOWSEAL_ENCODED.contains(value)) {
                encoded = true;
                break;
            }
        }
        if (!encoded) {
            return cached;
        }
        List<String> result = new ArrayList<>();
        for (String value : cached) {
            result.add(decodeFlowsealDomain(value));
        }
        return result;
    }

    private static String decodeFlowsealDomain(String value) {
        String normalized = value.trim().toLowerCase(Locale.US);
        if (!normalized.endsWith(".com")) {
            return normalized;
        }
        String prefix = normalized.substring(0, normalized.length() - 4);
        int shift = 0;
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c >= 'a' && c <= 'z') {
                shift++;
            }
        }
        StringBuilder decoded = new StringBuilder();
        for (int i = 0; i < prefix.length(); i++) {
            char c = prefix.charAt(i);
            if (c >= 'a' && c <= 'z') {
                int offset = c - 'a' - shift;
                while (offset < 0) {
                    offset += 26;
                }
                decoded.append((char) ('a' + offset % 26));
            } else {
                decoded.append(c);
            }
        }
        return decoded.append(".co.uk").toString();
    }

    private static String readUriText(Context context, Uri uri, int maxBytes) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IllegalStateException("import_open_failed");
            }
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > maxBytes) {
                    throw new IllegalStateException("import_too_large");
                }
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private static File writePrivateTempFile(Context context, String text) throws Exception {
        File dir = new File(context.getFilesDir(), "tgwsproxy/awg/tmp");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("temp_directory_failed");
        }
        File file = File.createTempFile("candidate-", ".conf", dir);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        return file;
    }

    private static File selectedAwgConfigFile(Context context) {
        return new File(context.getFilesDir(), "tgwsproxy/awg/selected.conf");
    }

    private static String cfSummary(SharedPreferences prefs) {
        int working = prefs.getInt(KEY_CF_WORKING, -1);
        int total = prefs.getInt(KEY_CF_TOTAL, 0);
        long checked = prefs.getLong(KEY_CF_LAST_CHECK, 0L);
        if (working < 0 || checked <= 0L) {
            int configured = splitStoredHosts(prefs.getString(KEY_CF_MANUAL, "")).size()
                    + splitStoredHosts(prefs.getString(KEY_CF_CACHED, "")).size();
            return configured > 0 ? configured + " настроено · не проверено" : "Встроенный fallback · не проверено";
        }
        return working + " / " + total + " · " + relativeTime(checked);
    }

    private static String awgSummary(Context context, SharedPreferences prefs) {
        String name = prefs.getString(KEY_AWG_PROFILE_NAME, "");
        boolean ready = prefs.getBoolean(KEY_AWG_PROFILE_READY, false) && selectedAwgConfigFile(context).isFile();
        if (name == null || name.trim().isEmpty()) {
            return "Не настроен";
        }
        return name + (ready ? " · работает" : " · " + prefs.getString(KEY_AWG_PROFILE_HEALTH, "не проверен"));
    }

    private static String relativeTime(long timestamp) {
        long delta = Math.max(0L, System.currentTimeMillis() - timestamp);
        if (delta < 60_000L) {
            return "только что";
        }
        if (delta < 60L * 60L * 1000L) {
            return (delta / 60_000L) + " мин назад";
        }
        return (delta / (60L * 60L * 1000L)) + " ч назад";
    }

    private static String suggestedAwgName(SharedPreferences prefs) {
        String existing = prefs.getString(KEY_AWG_PROFILE_NAME, "");
        return existing == null || existing.isEmpty() ? "WARP 1" : "WARP 2";
    }

    private static String sanitizeProfileName(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return fallback;
        }
        if (value.length() > 64) {
            value = value.substring(0, 64);
        }
        return value.replace("\n", " ").replace("\r", " ");
    }

    private static String sanitizeRouteMode(String raw) {
        if (ROUTE_CF_PROXY.equals(raw) || ROUTE_AWG.equals(raw)
                || ROUTE_WORKER.equals(raw) || ROUTE_DIRECT.equals(raw)) {
            return raw;
        }
        return ROUTE_AUTO;
    }

    public static String routeLabel(String mode) {
        String normalized = sanitizeRouteMode(mode);
        if (ROUTE_CF_PROXY.equals(normalized)) {
            return "Cloudflare Proxy";
        }
        if (ROUTE_AWG.equals(normalized)) {
            return "WARP / AmneziaWG";
        }
        if (ROUTE_WORKER.equals(normalized)) {
            return "Cloudflare Worker";
        }
        if (ROUTE_DIRECT.equals(normalized)) {
            return "Напрямую";
        }
        return "Автоматически";
    }

    private static String runtimeStateLabel(String state) {
        if ("LISTENING_ROUTE_READY".equals(state)) {
            return "Работает";
        }
        if ("LISTENING_ROUTE_DEGRADED".equals(state)) {
            return "Ограничено";
        }
        if ("STARTING".equals(state)) {
            return "Запускается";
        }
        if ("FAILED".equals(state)) {
            return "Ошибка";
        }
        if ("STOPPED".equals(state)) {
            return "Остановлен";
        }
        return state == null || state.isEmpty() ? "Неизвестно" : state;
    }

    private static String backendLabel(String backend) {
        if (backend == null || backend.isEmpty() || "none".equalsIgnoreCase(backend)) {
            return "";
        }
        if ("direct_ws".equals(backend)) {
            return "Direct";
        }
        if ("cf_proxy_ws".equals(backend)) {
            return "Cloudflare Proxy";
        }
        if ("cf_worker_ws".equals(backend) || "worker_ws".equals(backend)) {
            return "Cloudflare Worker";
        }
        if ("awg_warp".equals(backend)) {
            return "WARP / AmneziaWG";
        }
        return backend;
    }

    private static String safeStatusText(String value) {
        if (value == null) {
            return "";
        }
        String safe = value.replace("\n", " ").replace("\r", " ").trim();
        return safe.length() > 120 ? safe.substring(0, 120) : safe;
    }

    private static String safeCode(String value, String fallback) {
        if (value == null || !value.matches("[a-zA-Z0-9_.-]{1,80}")) {
            return fallback;
        }
        return value;
    }

    private static String safeHostForMessage(String value) {
        if (value == null) {
            return "invalid";
        }
        String safe = value.replaceAll("[^a-zA-Z0-9.-]", "");
        return safe.length() > 80 ? safe.substring(0, 80) : safe;
    }

    private static List<String> splitStoredHosts(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        for (String line : raw.split("\\r?\\n")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return dedupe(result);
    }

    private static List<String> dedupe(List<String> values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                unique.add(value.trim());
            }
        }
        return new ArrayList<>(unique);
    }

    private static String pipeList(String raw) {
        return join(splitStoredHosts(raw), "|");
    }

    private static String join(List<String> values, String separator) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(separator);
            }
            out.append(value);
        }
        return out.toString();
    }

    private static int bool(boolean value) {
        return value ? 1 : 0;
    }

    private static void ensureDefaultAwgWorkers(SharedPreferences preferences) {
        if (preferences.contains(KEY_AWG_WORKERS)) {
            return;
        }
        preferences.edit()
                .putString(KEY_AWG_WORKERS, join(BUILT_IN_AWG_WORKERS, "\n"))
                .apply();
    }

    private static String awgWorkersText(SharedPreferences preferences) {
        ensureDefaultAwgWorkers(preferences);
        String value = preferences.getString(KEY_AWG_WORKERS, "");
        return value == null ? "" : value;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String getOrCreateSecret(SharedPreferences preferences) {
        String current = preferences.getString(KEY_SECRET, null);
        if (current != null && current.matches("[0-9a-fA-F]{32}")) {
            return current.toLowerCase(Locale.US);
        }

        byte[] bytes = new byte[16];
        new java.security.SecureRandom().nextBytes(bytes);
        char[] chars = new char[32];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            chars[i * 2] = HEX[value >>> 4];
            chars[i * 2 + 1] = HEX[value & 0x0f];
        }
        String generated = new String(chars);
        preferences.edit().putString(KEY_SECRET, generated).commit();
        return generated;
    }

    private static boolean supportsArm64() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) {
                return true;
            }
        }
        return false;
    }

    private static void disableManagedProxy(Context context, SharedPreferences integrationPrefs) {
        if (!integrationPrefs.getBoolean(KEY_MANAGED_PROXY, false)) {
            return;
        }
        String secret = integrationPrefs.getString(KEY_SECRET, "");
        SharedPreferences telegramPrefs =
                context.getSharedPreferences("mainconfig", Context.MODE_PRIVATE);
        String address = telegramPrefs.getString("proxy_ip", "");
        int port = telegramPrefs.getInt("proxy_port", 0);
        String telegramSecret = telegramPrefs.getString("proxy_secret", "");
        if (HOST.equals(address) && PORT == port && secret != null && secret.equals(telegramSecret)) {
            telegramPrefs.edit().putBoolean("proxy_enabled", false).commit();
            applyTelegramProxy(false, "");
        }
        integrationPrefs.edit().putBoolean(KEY_MANAGED_PROXY, false).apply();
    }

    private static void applyTelegramProxy(boolean enabled, String secret) {
        Runnable apply = () ->
                ConnectionsManager.setProxySettings(enabled, HOST, PORT, "", "", secret);
        Handler handler = ApplicationLoader.applicationHandler;
        if (handler != null) {
            handler.post(apply);
        } else {
            new Handler(Looper.getMainLooper()).post(apply);
        }
    }

    private static void runAsync(String name, Runnable runnable) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static void complete(Callback callback, boolean success, String message) {
        if (callback == null) {
            return;
        }
        Handler handler = ApplicationLoader.applicationHandler;
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        Handler finalHandler = handler;
        finalHandler.post(() -> callback.onComplete(success, message));
    }

    private static final class HostListValidation {
        final boolean success;
        final String normalizedText;
        final String error;

        HostListValidation(boolean success, String normalizedText, String error) {
            this.success = success;
            this.normalizedText = normalizedText;
            this.error = error;
        }
    }

    private static final class RegistrationResult {
        final TgWsProxyConsumerWarpHttpResult result;
        final String transportWorker;

        RegistrationResult(TgWsProxyConsumerWarpHttpResult result, String transportWorker) {
            this.result = result;
            this.transportWorker = transportWorker;
        }
    }

    private static final class ProbeResult {
        final boolean success;
        final String code;

        ProbeResult(boolean success, String code) {
            this.success = success;
            this.code = code;
        }
    }
}
