package io.github.kreza6173pixel.pulsebattery.ui.report

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kreza6173pixel.pulsebattery.R
import io.github.kreza6173pixel.pulsebattery.diag.DiagResult
import io.github.kreza6173pixel.pulsebattery.drain.Baseline
import io.github.kreza6173pixel.pulsebattery.drain.BaselineStore
import io.github.kreza6173pixel.pulsebattery.drain.DrainReport
import io.github.kreza6173pixel.pulsebattery.drain.DrainReportResult
import io.github.kreza6173pixel.pulsebattery.drain.DrainRepository
import io.github.kreza6173pixel.pulsebattery.drain.Offender
import io.github.kreza6173pixel.pulsebattery.exec.ConnectionState
import io.github.kreza6173pixel.pulsebattery.exec.ExecBridge
import io.github.kreza6173pixel.pulsebattery.standby.ActionResult
import io.github.kreza6173pixel.pulsebattery.standby.StandbyBucket
import io.github.kreza6173pixel.pulsebattery.standby.StandbyParsers
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ROWS = 20

/** The screen re-reads the counters this often while it is open. Nothing runs when closed. */
private const val AUTO_REFRESH_MS = 60_000L

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

private fun clock(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(CLOCK)

/**
 * Overnight drain report: start a period before bed, read the top alarm wakers in the
 * morning, restrict an offender in one tap (read back, revertable). Android keeps the
 * counters itself, so nothing of ours runs while this screen is closed.
 */
@Composable
fun DrainReportScreen(bridge: ExecBridge, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ownPkg = context.packageName
    val repo = remember(bridge) { DrainRepository(bridge) }
    val store = remember { BaselineStore(context) }
    val scope = rememberCoroutineScope()
    var baseline by remember { mutableStateOf(store.load()) }
    var totals by remember { mutableStateOf<DiagResult<Map<String, Int>>?>(null) }
    var buckets by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var busy by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var updatedAt by remember { mutableStateOf<Long?>(null) }
    var lastAction by remember { mutableStateOf<ActionResult?>(null) }
    var showStar by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(connected, reloadTick) {
        if (!connected) return@LaunchedEffect
        loading = true
        val t = withContext(Dispatchers.IO) { repo.readTotals() }
        totals = t
        if (t is DiagResult.Ok) {
            val r = DrainReport.compute(baseline, t.value, System.currentTimeMillis(), SystemClock.elapsedRealtime())
            val top = r.offenders.take(MAX_ROWS).map { it.pkg }
            buckets = withContext(Dispatchers.IO) { repo.readBuckets(top) }
        }
        updatedAt = System.currentTimeMillis()
        loading = false
    }

    // Live while open: one cheap read per minute, cancelled as soon as the screen is left.
    LaunchedEffect(connected) {
        if (!connected) return@LaunchedEffect
        while (true) {
            delay(AUTO_REFRESH_MS)
            if (!busy) reloadTick++
        }
    }

    fun act(fromFix: Boolean, block: () -> ActionResult) {
        if (busy) return
        scope.launch {
            busy = true
            val r = withContext(Dispatchers.IO) { block() }
            lastAction = r
            busy = false
            if (fromFix && r.ok && !store.starPromptShown) {
                store.starPromptShown = true
                showStar = true
            }
            reloadTick++
        }
    }

    fun startPeriod() {
        if (busy) return
        scope.launch {
            busy = true
            val t = withContext(Dispatchers.IO) { repo.readTotals() }
            totals = t
            if (t is DiagResult.Ok) {
                val b = Baseline(System.currentTimeMillis(), SystemClock.elapsedRealtime(), t.value)
                store.save(b)
                baseline = b
                lastAction = null
                Toast.makeText(context, R.string.report_started_toast, Toast.LENGTH_SHORT).show()
            }
            busy = false
            reloadTick++
        }
    }

    val t = totals
    val report: DrainReportResult? = if (t is DiagResult.Ok) {
        DrainReport.compute(baseline, t.value, System.currentTimeMillis(), SystemClock.elapsedRealtime())
    } else {
        null
    }
    val rows: List<Offender> = report?.offenders?.take(MAX_ROWS).orEmpty()
    val action = lastAction
    val enabled = connected && !busy
    val currentBaseline = baseline

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!connected) {
            item { Text(stringResource(R.string.diag_waiting)) }
        }
        item {
            PeriodCard(
                baseline = currentBaseline,
                rebooted = report?.rebooted == true,
                enabled = enabled,
                busy = busy,
                loading = loading,
                updatedAt = updatedAt,
                onStart = { startPeriod() },
                onRefresh = { reloadTick++ },
            )
        }
        if (showStar) {
            item { StarCard(onDismiss = { showStar = false }) }
        }
        if (action != null) {
            item {
                ActionCard(
                    a = action,
                    enabled = enabled,
                    onRevert = { pkg, prev -> act(false) { repo.revert(pkg, prev) } },
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        if (currentBaseline == null) R.string.report_since_boot_title else R.string.report_top_title,
                        rows.size,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (report != null) CopyShareButtons(DrainReport.toText(report, MAX_ROWS))
            }
        }
        when (t) {
            null -> item { Text(stringResource(R.string.diag_loading)) }
            is DiagResult.Error -> item {
                Text(
                    text = stringResource(R.string.diag_error, t.message),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            is DiagResult.Ok -> Unit
        }
        if (report != null) {
            item {
                val period = report.elapsedMs?.takeIf { it >= DrainReport.MIN_RATE_PERIOD_MS }
                val total = pluralStringResource(R.plurals.report_total, report.total, report.total)
                Text(
                    text = if (period != null) {
                        val perHour = report.total / (period / 3_600_000.0)
                        total + " \u00b7 " + stringResource(R.string.report_rate, DrainReport.formatRate(perHour))
                    } else {
                        total
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (rows.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.report_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(rows, key = { it.pkg }) { o ->
            OffenderRow(
                o = o,
                code = buckets[o.pkg],
                ownPkg = ownPkg,
                enabled = enabled,
                onRestrict = { act(true) { repo.restrict(o.pkg) } },
            )
        }
        item {
            Text(
                text = stringResource(R.string.report_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PeriodCard(
    baseline: Baseline?,
    rebooted: Boolean,
    enabled: Boolean,
    busy: Boolean,
    loading: Boolean,
    updatedAt: Long?,
    onStart: () -> Unit,
    onRefresh: () -> Unit,
) {
    var confirmRestart by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.report_intro_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.report_intro_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (baseline == null) {
                Text(
                    text = stringResource(R.string.report_no_period),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val elapsed = System.currentTimeMillis() - baseline.timeMs
                Text(
                    text = stringResource(
                        R.string.report_period_since,
                        clock(baseline.timeMs),
                        DrainReport.formatDuration(elapsed),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (rebooted) {
                Text(
                    text = stringResource(R.string.report_rebooted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = when {
                    busy || loading -> stringResource(R.string.report_updating)
                    updatedAt != null -> stringResource(R.string.report_updated, clock(updatedAt))
                    else -> stringResource(R.string.diag_loading)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (baseline == null) {
                Button(onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (busy) R.string.report_starting else R.string.report_start))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onRefresh,
                        enabled = enabled && !loading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.report_refresh))
                    }
                    OutlinedButton(
                        onClick = { confirmRestart = true },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(if (busy) R.string.report_starting else R.string.report_restart))
                    }
                }
            }
        }
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text(stringResource(R.string.report_restart_confirm_title)) },
            text = { Text(stringResource(R.string.report_restart_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestart = false
                    onStart()
                }) {
                    Text(stringResource(R.string.report_restart_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestart = false }) {
                    Text(stringResource(R.string.report_cancel))
                }
            },
        )
    }
}

@Composable
private fun StarCard(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val url = stringResource(R.string.report_repo_url)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.report_star_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.report_star_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                    onDismiss()
                }) {
                    Text(stringResource(R.string.report_star_action))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.report_star_dismiss))
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    a: ActionResult,
    enabled: Boolean,
    onRevert: (String, StandbyBucket) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
            Text(
                text = stringResource(if (a.ok) R.string.standby_applied else R.string.standby_not_applied),
                style = MaterialTheme.typography.titleSmall,
                color = if (a.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            LtrMonoText(a.message)
            CopyShareButtons(a.message)
            val pkg = a.pkg
            val prev = a.previousBucket
            if (a.ok && pkg != null && prev != null && prev.settable) {
                TextButton(onClick = { onRevert(pkg, prev) }, enabled = enabled) {
                    Text(stringResource(R.string.standby_revert_to, stringResource(bucketRes(prev))))
                }
            }
        }
    }
}

@Composable
private fun OffenderRow(
    o: Offender,
    code: Int?,
    ownPkg: String,
    enabled: Boolean,
    onRestrict: () -> Unit,
) {
    val bucket = StandbyBucket.fromCode(code)
    val bucketText = when {
        bucket != null -> stringResource(bucketRes(bucket))
        code != null -> stringResource(R.string.standby_bucket_code, code)
        else -> stringResource(R.string.report_bucket_none)
    }
    val rate = o.perHour
    val count = pluralStringResource(R.plurals.report_wakeups, o.wakeups, o.wakeups)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            LtrMonoText(text = o.pkg, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (rate != null) {
                    count + " \u00b7 " + stringResource(R.string.report_rate, DrainReport.formatRate(rate))
                } else {
                    count
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.standby_bucket_label, bucketText),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (DrainRepository.canRestrict(o.pkg, code, ownPkg)) {
                    OutlinedButton(onClick = onRestrict, enabled = enabled) {
                        Text(stringResource(R.string.report_restrict))
                    }
                } else {
                    Text(
                        text = stringResource(reasonRes(o.pkg, bucket, ownPkg)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun reasonRes(pkg: String, bucket: StandbyBucket?, ownPkg: String): Int = when {
    pkg == ownPkg -> R.string.report_reason_self
    !StandbyParsers.isValidPackage(pkg) -> R.string.report_reason_core
    bucket == StandbyBucket.RESTRICTED -> R.string.report_reason_already
    bucket == StandbyBucket.EXEMPTED || bucket == StandbyBucket.NEVER -> R.string.report_reason_system
    else -> R.string.report_reason_unknown
}

private fun bucketRes(b: StandbyBucket): Int = when (b) {
    StandbyBucket.EXEMPTED -> R.string.bucket_exempted
    StandbyBucket.ACTIVE -> R.string.bucket_active
    StandbyBucket.WORKING_SET -> R.string.bucket_working_set
    StandbyBucket.FREQUENT -> R.string.bucket_frequent
    StandbyBucket.RARE -> R.string.bucket_rare
    StandbyBucket.RESTRICTED -> R.string.bucket_restricted
    StandbyBucket.NEVER -> R.string.bucket_never
}
