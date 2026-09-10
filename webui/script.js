(() => {
  "use strict";

  const VAULT_ROOT = "/sdcard/pulse-vault";
  const GAUGE_CIRCUMFERENCE = 2 * Math.PI * 36; // r=36, matches the SVG

  const el = {
    bridgeWarning: document.getElementById("bridge-warning"),
    bridgeBadge: document.getElementById("bridge-badge"),
    bridgeRetryBtn: document.getElementById("bridge-retry-btn"),
    tabs: document.querySelectorAll(".tab-btn"),
    panels: document.querySelectorAll(".panel-view"),
    gauge: document.getElementById("battery-gauge"),
    gaugeFill: document.getElementById("gauge-fill-circle"),
    gaugeValue: document.getElementById("gauge-value"),
    vitalStatus: document.getElementById("vital-status"),
    vitalTemp: document.getElementById("vital-temp"),
    vitalMode: document.getElementById("vital-mode"),

    scanWakelocksBtn: document.getElementById("scan-wakelocks-btn"),
    wakelockList: document.getElementById("wakelock-list"),
    scanAlarmsBtn: document.getElementById("scan-alarms-btn"),
    alarmList: document.getElementById("alarm-list"),
    scanReportBtn: document.getElementById("scan-report-btn"),
    reportPkgInput: document.getElementById("report-pkg-input"),
    reportOutput: document.getElementById("report-output"),

    scanBucketsBtn: document.getElementById("scan-buckets-btn"),
    bucketSearch: document.getElementById("bucket-search"),
    bucketScopeUser: document.getElementById("bucket-scope-user"),
    bucketList: document.getElementById("bucket-list"),
    dozeList: document.getElementById("doze-list"),
    dozeInput: document.getElementById("doze-input"),
    dozeAddBtn: document.getElementById("doze-add-btn"),
    forceIdleBtn: document.getElementById("force-idle-btn"),
    unforceIdleBtn: document.getElementById("unforce-idle-btn"),

    backupCapabilityText: document.getElementById("backup-capability-text"),
    scanBackupAppsBtn: document.getElementById("scan-backup-apps-btn"),
    backupAppSearch: document.getElementById("backup-app-search"),
    backupScopeUser: document.getElementById("backup-scope-user"),
    backupAppList: document.getElementById("backup-app-list"),
    backupSelectAllBtn: document.getElementById("backup-select-all-btn"),
    backupSelectNoneBtn: document.getElementById("backup-select-none-btn"),
    backupOptApk: document.getElementById("backup-opt-apk"),
    backupOptData: document.getElementById("backup-opt-data"),
    backupExportBtn: document.getElementById("backup-export-btn"),
    backupProgress: document.getElementById("backup-progress"),

    vaultRefreshBtn: document.getElementById("vault-refresh-btn"),
    vaultList: document.getElementById("vault-list"),
    restoreDetailCard: document.getElementById("restore-detail-card"),
    restoreDetailTitle: document.getElementById("restore-detail-title"),
    restoreAppList: document.getElementById("restore-app-list"),
    restoreBtn: document.getElementById("restore-btn"),
    restoreProgress: document.getElementById("restore-progress"),

    consoleDrawer: document.getElementById("console-drawer"),
    consoleToggle: document.getElementById("console-toggle"),
    consoleBody: document.getElementById("console-body"),
    consoleCount: document.getElementById("console-count"),
    confirmBackdrop: document.getElementById("confirm-backdrop"),
    confirmTitle: document.getElementById("confirm-title"),
    confirmBody: document.getElementById("confirm-body"),
    confirmOk: document.getElementById("confirm-ok"),
    confirmCancel: document.getElementById("confirm-cancel"),
  };

  let consoleLines = 0;
  let isRoot = false;
  let scannedBackupApps = []; // {pkg, versionName, versionCode, debuggable}
  const backupSelected = new Set();
  let vaults = [];
  let activeVault = null;
  const restoreSelected = new Set();

  // ---------- theme toggle (light/dark, persisted in localStorage) ----------

  (function initThemeToggle() {
    const KEY = "pulse-theme";
    const btn = document.getElementById("theme-toggle-btn");
    if (!btn) return;
    function current() { return document.documentElement.getAttribute("data-theme") === "dark" ? "dark" : "light"; }
    function apply(theme) {
      if (theme === "dark") document.documentElement.setAttribute("data-theme", "dark");
      else document.documentElement.removeAttribute("data-theme");
      btn.textContent = theme === "dark" ? "☀ light" : "☾ dark";
      try { localStorage.setItem(KEY, theme); } catch (e) { /* storage unavailable, theme just won't persist */ }
    }
    apply(current()); // sync the button label with whatever the anti-flash inline script already applied
    btn.addEventListener("click", () => apply(current() === "dark" ? "light" : "dark"));
  })();

  // ---------- shell bridge (sync call, JSON-string result) ----------

  function bridgeAvailable() { return typeof window.Shizuku !== "undefined" && window.Shizuku !== null; }
  function shq(s) { return "'" + String(s).replace(/'/g, "'\\''") + "'"; }

  function logConsole(text, kind) {
    consoleLines++;
    el.consoleCount.textContent = String(consoleLines);
    const line = document.createElement("div");
    line.className = "console-line" + (kind ? ` ${kind}` : "");
    line.textContent = text;
    el.consoleBody.appendChild(line);
    el.consoleBody.scrollTop = el.consoleBody.scrollHeight;
    while (el.consoleBody.children.length > 300) el.consoleBody.removeChild(el.consoleBody.firstChild);
  }

  async function exec(cmd) {
    logConsole("$ " + cmd.split("\n")[0] + (cmd.indexOf("\n") !== -1 ? " …" : ""));
    if (!bridgeAvailable()) {
      logConsole("window.Shizuku is not available.", "err");
      return { ok: false, exitCode: -1, stdout: "", stderr: "window.Shizuku is not available", timedOut: false };
    }
    let raw;
    try { raw = window.Shizuku.exec(cmd); }
    catch (e) { logConsole(String(e), "err"); return { ok: false, exitCode: -1, stdout: "", stderr: String(e), timedOut: false }; }
    let res;
    try { res = JSON.parse(raw); }
    catch (e) { logConsole("unparseable bridge response: " + String(raw).slice(0, 200), "err"); return { ok: false, exitCode: -1, stdout: "", stderr: "unparseable bridge response", timedOut: false }; }
    if (res.stdout) logConsole(res.stdout.trim(), "ok");
    if (res.stderr) logConsole(res.stderr.trim(), "err");
    return res;
  }

  async function checkBridge() {
    if (!bridgeAvailable()) {
      el.bridgeBadge.textContent = "bridge unavailable";
      el.bridgeBadge.className = "bridge-badge error";
      el.bridgeWarning.classList.remove("hidden");
      return false;
    }
    const idRes = await exec("id");
    isRoot = !!(idRes.ok && /uid=0\(root\)/.test(idRes.stdout || ""));
    el.bridgeBadge.textContent = "connected";
    el.bridgeBadge.className = "bridge-badge ok";
    el.bridgeWarning.classList.add("hidden");
    el.vitalMode.textContent = isRoot ? "root" : "adb / shell";
    updateBackupCapabilityText();
    return true;
  }

  function updateBackupCapabilityText() {
    el.backupCapabilityText.textContent = isRoot
      ? "Root detected — full app data backup/restore works for any app."
      : "No root detected — full data backup still works for apps signed with a debug key (via run-as), detected automatically during the scan below. Other apps: APK export only.";
  }

  // ---------- confirm modal ----------

  function confirmAction(title, body) {
    el.confirmTitle.textContent = title;
    el.confirmBody.textContent = body;
    el.confirmBackdrop.classList.remove("hidden");
    return new Promise((resolve) => {
      const cleanup = (r) => {
        el.confirmBackdrop.classList.add("hidden");
        el.confirmOk.removeEventListener("click", onOk);
        el.confirmCancel.removeEventListener("click", onCancel);
        resolve(r);
      };
      const onOk = () => cleanup(true);
      const onCancel = () => cleanup(false);
      el.confirmOk.addEventListener("click", onOk);
      el.confirmCancel.addEventListener("click", onCancel);
    });
  }

  // ---------- tabs ----------

  el.tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      el.tabs.forEach((t) => t.classList.remove("active"));
      el.panels.forEach((p) => p.classList.remove("active"));
      tab.classList.add("active");
      document.getElementById(`tab-${tab.dataset.tab}`).classList.add("active");
      if (tab.dataset.tab === "standby") loadDozeWhitelist();
      if (tab.dataset.tab === "restore") loadVaults();
    });
  });
  el.consoleToggle.addEventListener("click", () => el.consoleDrawer.classList.toggle("open"));

  // ---------- gauge / vitals ----------

  function setGauge(percent, kind) {
    const clamped = Math.max(0, Math.min(100, percent));
    const offset = GAUGE_CIRCUMFERENCE * (1 - clamped / 100);
    el.gaugeFill.style.strokeDashoffset = String(offset);
    el.gaugeFill.classList.remove("warn", "crit");
    if (kind === "warn") el.gaugeFill.classList.add("warn");
    if (kind === "crit") el.gaugeFill.classList.add("crit");
    el.gaugeValue.textContent = Number.isFinite(percent) ? String(Math.round(percent)) : "--";
  }

  const BATTERY_STATUS = { 1: "unknown", 2: "charging", 3: "discharging", 4: "not charging", 5: "full" };

  async function refreshVitals() {
    el.gauge.classList.add("scanning");
    const res = await exec("dumpsys battery");
    el.gauge.classList.remove("scanning");
    const text = res.stdout || "";
    const level = parseInt((text.match(/level:\s*(\d+)/) || [])[1], 10);
    const statusNum = parseInt((text.match(/status:\s*(\d+)/) || [])[1], 10);
    const tempRaw = parseInt((text.match(/temperature:\s*(\d+)/) || [])[1], 10);

    if (Number.isFinite(level)) {
      const kind = level <= 10 ? "crit" : level <= 20 ? "warn" : null;
      setGauge(level, kind);
    }
    el.vitalStatus.textContent = BATTERY_STATUS[statusNum] || "—";
    if (Number.isFinite(tempRaw)) {
      const celsius = tempRaw / 10;
      el.vitalTemp.textContent = celsius.toFixed(1) + "°C";
      el.vitalTemp.className = "vital-val" + (celsius >= 45 ? " crit" : celsius >= 40 ? " warn" : "");
    }
  }

  // ---------- wakelocks (best-effort) ----------

  el.scanWakelocksBtn.addEventListener("click", async () => {
    el.wakelockList.innerHTML = `<div class="empty-state">Scanning…</div>`;
    const powerRes = await exec("dumpsys power");
    const lines = (powerRes.stdout || "").split("\n").filter((l) => /WAKE_LOCK/.test(l) && /uid=/.test(l));
    if (lines.length === 0) {
      el.wakelockList.innerHTML = `<div class="empty-state">No wakelocks currently held (or the output format wasn't recognized — check the console for the raw dump).</div>`;
      return;
    }
    // Map uid -> package via a single batched lookup.
    const uidRes = await exec("pm list packages -U");
    const uidMap = {};
    (uidRes.stdout || "").split("\n").forEach((l) => {
      const m = l.match(/^package:(\S+)\s+uid:(\d+)/);
      if (m) uidMap[m[2]] = m[1];
    });
    const rows = lines.map((l) => {
      const tagMatch = l.match(/'([^']+)'/);
      const uidMatch = l.match(/uid=(\d+)/);
      const uid = uidMatch ? uidMatch[1] : null;
      return { tag: tagMatch ? tagMatch[1] : l.trim().slice(0, 60), pkg: uid && uidMap[uid] ? uidMap[uid] : (uid ? `uid ${uid}` : "unknown") };
    });
    el.wakelockList.innerHTML = rows.map((r) => `
      <div class="item-row">
        <div class="item-main"><span class="item-name">${escapeHtml(r.pkg)}</span><span class="item-sub">${escapeHtml(r.tag)}</span></div>
      </div>
    `).join("");
  });

  // ---------- alarms (best-effort) ----------

  el.scanAlarmsBtn.addEventListener("click", async () => {
    el.alarmList.innerHTML = `<div class="empty-state">Scanning…</div>`;
    const res = await exec("dumpsys alarm");
    const lines = (res.stdout || "").split("\n").filter((l) => /wakeups/i.test(l));
    if (lines.length === 0) {
      el.alarmList.innerHTML = `<div class="empty-state">No alarm-wakeup lines recognized — check the console for the raw dump.</div>`;
      return;
    }
    el.alarmList.innerHTML = lines.slice(0, 60).map((l) => `
      <div class="item-row"><div class="item-main"><span class="item-sub">${escapeHtml(l.trim().slice(0, 140))}</span></div></div>
    `).join("");
  });

  // ---------- raw battery report ----------

  el.scanReportBtn.addEventListener("click", async () => {
    const pkg = el.reportPkgInput.value.trim();
    if (!pkg) return;
    el.reportOutput.classList.remove("hidden");
    el.reportOutput.innerHTML = `<div class="progress-line">Generating…</div>`;
    const res = await exec(`dumpsys batterystats ${shq(pkg)}`);
    const text = (res.stdout || "").slice(0, 20000);
    el.reportOutput.innerHTML = `<div class="progress-line">${escapeHtml(text || "(empty output)")}</div>`;
  });

  // ---------- standby buckets ----------

  const BUCKET_NAMES = ["active", "working_set", "frequent", "rare", "restricted"];
  let scannedBuckets = [];

  function renderBucketList() {
    const filter = el.bucketSearch.value.trim().toLowerCase();
    const rows = scannedBuckets.filter((a) => !filter || a.pkg.toLowerCase().includes(filter));
    if (rows.length === 0) {
      el.bucketList.innerHTML = `<div class="empty-state">${scannedBuckets.length ? "No apps match your filter." : "Run a scan to list installed apps."}</div>`;
      return;
    }
    el.bucketList.innerHTML = rows.map((a) => `
      <div class="item-row column" data-pkg="${a.pkg}">
        <div class="item-main"><span class="item-name">${escapeHtml(a.pkg)}</span></div>
        <div style="display:flex; gap:5px; flex-wrap:wrap; margin-top:4px;">
          ${BUCKET_NAMES.map((b) => `<span class="badge ${a.bucket === b ? (b === "restricted" ? "crit" : "ok") : "neutral"} bucket-opt" data-bucket="${b}" style="cursor:pointer;">${b}</span>`).join("")}
        </div>
      </div>
    `).join("");
    el.bucketList.querySelectorAll(".item-row").forEach((row) => {
      row.querySelectorAll(".bucket-opt").forEach((badge) => {
        badge.addEventListener("click", async () => {
          const pkg = row.dataset.pkg;
          const bucket = badge.dataset.bucket;
          if (bucket === "restricted") {
            const ok = await confirmAction("Set to Restricted?", `${pkg} will be limited to a few background job/alarm windows per day. This can noticeably delay notifications.`);
            if (!ok) return;
          }
          await exec(`am set-standby-bucket ${shq(pkg)} ${bucket}`);
          const app = scannedBuckets.find((a) => a.pkg === pkg);
          if (app) app.bucket = bucket;
          renderBucketList();
        });
      });
    });
  }
  el.bucketSearch.addEventListener("input", renderBucketList);

  el.scanBucketsBtn.addEventListener("click", async () => {
    el.scanBucketsBtn.disabled = true;
    el.scanBucketsBtn.textContent = "Scanning…";
    el.bucketList.innerHTML = `<div class="empty-state">Scanning…</div>`;
    const scopeFlag = el.bucketScopeUser.checked ? "-3" : "";
    const cmd = [
      `for p in $(pm list packages ${scopeFlag} | sed 's/^package://'); do`,
      `  b=$(am get-standby-bucket "$p" 2>/dev/null)`,
      `  printf '%s|%s\\n' "$p" "$b"`,
      `done`,
    ].join("\n");
    const res = await exec(cmd);
    scannedBuckets = (res.stdout || "").split("\n").map((l) => l.trim()).filter(Boolean).map((line) => {
      const [pkg, bucket] = line.split("|");
      return { pkg, bucket: (bucket || "").toLowerCase().trim() };
    }).sort((a, b) => a.pkg.localeCompare(b.pkg));
    el.scanBucketsBtn.disabled = false;
    el.scanBucketsBtn.textContent = "Scan installed apps";
    renderBucketList();
  });

  // ---------- doze whitelist ----------

  async function loadDozeWhitelist() {
    el.dozeList.innerHTML = `<div class="empty-state">Loading…</div>`;
    const res = await exec("dumpsys deviceidle whitelist");
    const pkgs = [...new Set(((res.stdout || "").match(/[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+/g) || []))];
    if (pkgs.length === 0) {
      el.dozeList.innerHTML = `<div class="empty-state">Nothing whitelisted, or the output format wasn't recognized — check the console.</div>`;
      return;
    }
    el.dozeList.innerHTML = pkgs.map((p) => `
      <div class="item-row"><span class="item-name">${escapeHtml(p)}</span><button class="btn btn-ghost doze-remove-btn" data-pkg="${p}">Remove</button></div>
    `).join("");
    el.dozeList.querySelectorAll(".doze-remove-btn").forEach((btn) => {
      btn.addEventListener("click", async () => {
        await exec(`dumpsys deviceidle whitelist -${shq(btn.dataset.pkg)}`);
        loadDozeWhitelist();
      });
    });
  }
  el.dozeAddBtn.addEventListener("click", async () => {
    const pkg = el.dozeInput.value.trim();
    if (!pkg) return;
    await exec(`dumpsys deviceidle whitelist +${shq(pkg)}`);
    el.dozeInput.value = "";
    loadDozeWhitelist();
  });
  el.forceIdleBtn.addEventListener("click", () => exec("dumpsys deviceidle force-idle"));
  el.unforceIdleBtn.addEventListener("click", () => exec("dumpsys deviceidle unforce"));

  // ---------- backup: scan ----------

  function escapeHtml(str) {
    return (str || "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  }

  function dataMethodFor(app) {
    if (isRoot) return "root";
    if (app.debuggable) return "run-as";
    return null;
  }

  function renderBackupAppList() {
    const filter = el.backupAppSearch.value.trim().toLowerCase();
    const rows = scannedBackupApps.filter((a) => !filter || a.pkg.toLowerCase().includes(filter));
    if (rows.length === 0) {
      el.backupAppList.innerHTML = `<div class="empty-state">${scannedBackupApps.length ? "No apps match your filter." : "Run a scan to list installed apps."}</div>`;
      return;
    }
    el.backupAppList.innerHTML = rows.map((a) => {
      const method = dataMethodFor(a);
      return `
      <div class="item-row" data-pkg="${a.pkg}">
        <div class="item-main">
          <span class="item-name">${escapeHtml(a.pkg)}</span>
          <span class="item-sub">v${escapeHtml(a.versionName || "?")} — data: ${method ? `<span class="badge ok">${method}</span>` : `<span class="badge neutral">apk only</span>`}</span>
        </div>
        <input type="checkbox" ${backupSelected.has(a.pkg) ? "checked" : ""}>
      </div>
    `;
    }).join("");
    el.backupAppList.querySelectorAll(".item-row").forEach((row) => {
      const checkbox = row.querySelector("input[type=checkbox]");
      const toggle = () => {
        const pkg = row.dataset.pkg;
        if (backupSelected.has(pkg)) backupSelected.delete(pkg); else backupSelected.add(pkg);
        checkbox.checked = backupSelected.has(pkg);
        updateBackupButton();
      };
      row.addEventListener("click", (e) => { if (e.target !== checkbox) toggle(); });
      checkbox.addEventListener("click", (e) => { e.stopPropagation(); toggle(); });
    });
  }
  function updateBackupButton() {
    el.backupExportBtn.textContent = `Export selected (${backupSelected.size})`;
    el.backupExportBtn.disabled = backupSelected.size === 0;
  }
  el.backupAppSearch.addEventListener("input", renderBackupAppList);
  el.backupSelectAllBtn.addEventListener("click", () => {
    const filter = el.backupAppSearch.value.trim().toLowerCase();
    scannedBackupApps.filter((a) => !filter || a.pkg.toLowerCase().includes(filter)).forEach((a) => backupSelected.add(a.pkg));
    renderBackupAppList(); updateBackupButton();
  });
  el.backupSelectNoneBtn.addEventListener("click", () => { backupSelected.clear(); renderBackupAppList(); updateBackupButton(); });

  el.scanBackupAppsBtn.addEventListener("click", async () => {
    el.scanBackupAppsBtn.disabled = true;
    el.scanBackupAppsBtn.textContent = "Scanning…";
    el.backupAppList.innerHTML = `<div class="empty-state">Scanning…</div>`;
    const scopeFlag = el.backupScopeUser.checked ? "-3" : "";
    const cmd = [
      `for p in $(pm list packages ${scopeFlag} | sed 's/^package://'); do`,
      `  d=$(dumpsys package "$p" 2>/dev/null)`,
      `  v=$(echo "$d" | grep -m1 "versionName=" | sed 's/^ *versionName=//')`,
      `  c=$(echo "$d" | grep -m1 "versionCode=" | awk '{print $1}' | sed 's/versionCode=//')`,
      `  dbg=$(echo "$d" | grep -m1 "flags=\\[" | grep -c "DEBUGGABLE")`,
      `  printf '%s|%s|%s|%s\\n' "$p" "$v" "$c" "$dbg"`,
      `done`,
    ].join("\n");
    const res = await exec(cmd);
    scannedBackupApps = (res.stdout || "").split("\n").map((l) => l.trim()).filter(Boolean).map((line) => {
      const [pkg, versionName, versionCode, dbg] = line.split("|");
      return { pkg, versionName, versionCode, debuggable: dbg === "1" };
    }).sort((a, b) => a.pkg.localeCompare(b.pkg));
    el.scanBackupAppsBtn.disabled = false;
    el.scanBackupAppsBtn.textContent = "Scan installed apps";
    renderBackupAppList();
  });

  // ---------- backup: export ----------

  function progressLog(container, text, kind) {
    container.classList.remove("hidden");
    const line = document.createElement("div");
    line.className = "progress-line" + (kind ? ` ${kind}` : "");
    line.textContent = text;
    container.appendChild(line);
    container.scrollTop = container.scrollHeight;
  }

  el.backupExportBtn.addEventListener("click", async () => {
    if (backupSelected.size === 0) return;
    const wantsData = el.backupOptData.checked;
    const ok = await confirmAction(
      "Export " + backupSelected.size + " app(s)?",
      wantsData
        ? "Copies APKs for every selected app, and full app data for apps where that's possible (root, or run-as for debuggable apps). This can take a while."
        : "Copies APK files for the selected apps into a new vault folder on /sdcard."
    );
    if (!ok) return;

    el.backupExportBtn.disabled = true;
    el.backupProgress.innerHTML = "";
    el.backupProgress.classList.remove("hidden");

    const tsRes = await exec("date +%Y-%m-%d_%H%M%S");
    const ts = (tsRes.stdout || "").trim() || String(Date.now());
    const vaultDir = `${VAULT_ROOT}/${ts}`;
    await exec(`mkdir -p ${shq(vaultDir)}`);

    const manifestApps = [];

    for (const pkg of backupSelected) {
      const app = scannedBackupApps.find((a) => a.pkg === pkg) || { pkg };
      progressLog(el.backupProgress, `→ ${pkg}`);
      const destDir = `${vaultDir}/${pkg}`;
      await exec(`mkdir -p ${shq(destDir)}`);

      const apkFiles = [];
      let dataMethod = null;

      if (el.backupOptApk.checked) {
        const pathRes = await exec(`pm path ${shq(pkg)}`);
        const apkPaths = (pathRes.stdout || "").split("\n").map((l) => l.trim()).filter((l) => l.indexOf("package:") === 0).map((l) => l.slice("package:".length));
        if (apkPaths.length) {
          const copyCmds = apkPaths.map((p) => { const name = p.split("/").pop(); apkFiles.push(name); return `cp ${shq(p)} ${shq(destDir + "/" + name)}`; });
          const copyRes = await exec(copyCmds.join("\n"));
          progressLog(el.backupProgress, copyRes.ok ? `  ✓ ${apkFiles.length} APK file(s)` : `  ✗ APK copy failed`, copyRes.ok ? "ok" : "err");
        }
      }

      if (wantsData) {
        const method = dataMethodFor(app);
        if (method === "root") {
          const tarRes = await exec(`tar czf ${shq(destDir + "/data.tar.gz")} -C /data/data ${shq(pkg)} 2>&1`);
          dataMethod = tarRes.ok ? "root" : null;
          progressLog(el.backupProgress, tarRes.ok ? "  ✓ app data archived (root)" : `  ✗ data archive failed`, tarRes.ok ? "ok" : "err");
        } else if (method === "run-as") {
          const tarRes = await exec(`run-as ${shq(pkg)} sh -c "cd /data/data && tar czf - ${pkg}" > ${shq(destDir + "/data.tar.gz")}`);
          dataMethod = tarRes.ok ? "run-as" : null;
          progressLog(el.backupProgress, tarRes.ok ? "  ✓ app data archived (run-as, debuggable app)" : `  ✗ run-as data archive failed`, tarRes.ok ? "ok" : "err");
        } else {
          progressLog(el.backupProgress, "  – data backup skipped (not root, not debuggable)");
        }
      }

      manifestApps.push({ pkg, versionName: app.versionName || "", versionCode: app.versionCode || "", apkFiles, dataMethod });
    }

    const modelRes = await exec("getprop ro.product.model");
    const manifest = { created: new Date().toISOString(), device: (modelRes.stdout || "").trim(), hasRoot: isRoot, apps: manifestApps };
    await exec(`printf '%s' ${shq(JSON.stringify(manifest))} > ${shq(vaultDir + "/manifest.json")}`);
    progressLog(el.backupProgress, `Done — vault saved to ${vaultDir}`, "ok");
    el.backupExportBtn.disabled = false;
  });

  // ---------- restore & vault list ----------

  async function loadVaults() {
    el.vaultList.innerHTML = `<div class="empty-state">Loading…</div>`;
    const listRes = await exec(`ls -1 ${shq(VAULT_ROOT)} 2>/dev/null`);
    const dirs = (listRes.stdout || "").split("\n").map((l) => l.trim()).filter(Boolean).sort().reverse();
    if (dirs.length === 0) { el.vaultList.innerHTML = `<div class="empty-state">No vaults yet — create one from the Backup tab.</div>`; vaults = []; return; }
    vaults = [];
    for (const d of dirs) {
      const dir = `${VAULT_ROOT}/${d}`;
      const manifestRes = await exec(`cat ${shq(dir + "/manifest.json")} 2>/dev/null`);
      let manifest = null;
      try { manifest = JSON.parse((manifestRes.stdout || "").trim()); } catch (e) { /* skip */ }
      const sizeRes = await exec(`du -sh ${shq(dir)} 2>/dev/null`);
      const size = (sizeRes.stdout || "").split(/\s+/)[0] || "?";
      vaults.push({ dir, name: d, manifest, size });
    }
    el.vaultList.innerHTML = vaults.map((v, i) => `
      <div class="item-row" data-idx="${i}" style="cursor:pointer;">
        <div class="item-main"><span class="item-name">${escapeHtml(v.name)}</span><span class="item-sub">${v.manifest ? v.manifest.apps.length + " app(s)" : "manifest missing"} · ${v.size}</span></div>
        <button class="btn btn-ghost delete-vault-btn" data-idx="${i}">Delete</button>
      </div>
    `).join("");
    el.vaultList.querySelectorAll(".item-row").forEach((row) => {
      row.addEventListener("click", (e) => { if (!e.target.classList.contains("delete-vault-btn")) openRestoreVault(vaults[parseInt(row.dataset.idx, 10)]); });
    });
    el.vaultList.querySelectorAll(".delete-vault-btn").forEach((btn) => {
      btn.addEventListener("click", async (e) => {
        e.stopPropagation();
        const v = vaults[parseInt(btn.dataset.idx, 10)];
        const ok = await confirmAction("Delete this vault?", `${v.name} (${v.size}) will be permanently deleted.`);
        if (!ok) return;
        await exec(`rm -rf ${shq(v.dir)}`);
        loadVaults();
      });
    });
  }

  function openRestoreVault(vault) {
    activeVault = vault;
    restoreSelected.clear();
    el.restoreDetailCard.classList.remove("hidden");
    el.restoreDetailTitle.textContent = "Contents of " + vault.name;
    if (!vault.manifest) { el.restoreAppList.innerHTML = `<div class="empty-state">manifest.json missing/corrupt.</div>`; return; }
    el.restoreAppList.innerHTML = vault.manifest.apps.map((a) => `
      <div class="item-row" data-pkg="${a.pkg}">
        <div class="item-main"><span class="item-name">${escapeHtml(a.pkg)}</span><span class="item-sub">v${escapeHtml(a.versionName || "?")} · ${a.apkFiles.length} APK file(s)${a.dataMethod ? ` · data (${a.dataMethod})` : ""}</span></div>
        <input type="checkbox">
      </div>
    `).join("");
    el.restoreAppList.querySelectorAll(".item-row").forEach((row) => {
      const checkbox = row.querySelector("input[type=checkbox]");
      const toggle = () => { const pkg = row.dataset.pkg; if (restoreSelected.has(pkg)) restoreSelected.delete(pkg); else restoreSelected.add(pkg); checkbox.checked = restoreSelected.has(pkg); };
      row.addEventListener("click", (e) => { if (e.target !== checkbox) toggle(); });
      checkbox.addEventListener("click", (e) => { e.stopPropagation(); toggle(); });
    });
  }

  el.restoreBtn.addEventListener("click", async () => {
    if (!activeVault || !activeVault.manifest || restoreSelected.size === 0) return;
    const wantsData = activeVault.manifest.apps.some((a) => restoreSelected.has(a.pkg) && a.dataMethod);
    const ok = await confirmAction(
      "Restore " + restoreSelected.size + " app(s)?",
      wantsData ? "Reinstalls the selected APKs and OVERWRITES existing app data where a data backup exists. Best results when the app version matches. Continue?" : "Reinstalls the selected APKs via pm install-multiple."
    );
    if (!ok) return;

    el.restoreBtn.disabled = true;
    el.restoreProgress.innerHTML = "";
    el.restoreProgress.classList.remove("hidden");

    for (const app of activeVault.manifest.apps) {
      if (!restoreSelected.has(app.pkg)) continue;
      progressLog(el.restoreProgress, `→ ${app.pkg}`);
      const destDir = `${activeVault.dir}/${app.pkg}`;

      if (app.apkFiles && app.apkFiles.length) {
        const apkPaths = app.apkFiles.map((f) => shq(destDir + "/" + f)).join(" ");
        const installRes = await exec(`pm install-multiple -r ${apkPaths}`);
        progressLog(el.restoreProgress, installRes.ok ? "  ✓ installed" : `  ✗ install failed`, installRes.ok ? "ok" : "err");
      }

      if (app.dataMethod === "root") {
        await exec(`am force-stop ${shq(app.pkg)}`);
        const r = await exec(`tar xzf ${shq(destDir + "/data.tar.gz")} -C /data/data 2>&1; restorecon -R ${shq("/data/data/" + app.pkg)} 2>/dev/null`);
        progressLog(el.restoreProgress, r.ok ? "  ✓ app data restored (root)" : "  ✗ data restore failed", r.ok ? "ok" : "err");
      } else if (app.dataMethod === "run-as") {
        await exec(`am force-stop ${shq(app.pkg)}`);
        const r = await exec(`cat ${shq(destDir + "/data.tar.gz")} | run-as ${shq(app.pkg)} sh -c "cd /data/data && tar xzf -"`);
        progressLog(el.restoreProgress, r.ok ? "  ✓ app data restored (run-as)" : "  ✗ run-as data restore failed", r.ok ? "ok" : "err");
      }
    }
    progressLog(el.restoreProgress, "Done.", "ok");
    el.restoreBtn.disabled = false;
  });
  el.vaultRefreshBtn.addEventListener("click", loadVaults);

  // ---------- bridge retry / init ----------

  if (el.bridgeRetryBtn) {
    el.bridgeRetryBtn.addEventListener("click", async () => {
      el.bridgeRetryBtn.disabled = true;
      el.bridgeRetryBtn.textContent = "Retrying…";
      const ok = await checkBridge();
      el.bridgeRetryBtn.disabled = false;
      el.bridgeRetryBtn.textContent = "Retry connection";
      if (ok) refreshVitals();
    });
  }

  function init() {
    checkBridge().then((ok) => { if (ok) refreshVitals(); });
    updateBackupButton();
  }
  document.addEventListener("DOMContentLoaded", init);
})();
