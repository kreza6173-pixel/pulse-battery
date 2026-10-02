package io.github.kreza6173pixel.pulsebattery.ui.report

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.github.kreza6173pixel.pulsebattery.ui.common.CopyShareButtons
import io.github.kreza6173pixel.pulsebattery.ui.common.LtrMonoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_ROWS = 20

/**
 * Overnight drain report: start a period before bed, read the top alarm wakers in the
 * morning, restrict an offender in one tap (read back, revertable).
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
    var lastAction by remember { mutableStateOf<ActionResult?>(null) }
    var showStar by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    val connected = bridge.connectionState == ConnectionState.CONNECTED

    LaunchedEffect(connected, reloadTick) {
        if (!connected) return@LaunchedEffect
        val t = withContext(Dispatchers.IO) { repo.readTotals() }
        totals = t
        if (t is DiagResult.Ok) {
            val r = DrainReport.compute(baseline, t.value, System.currentTimeMillis(), SystemClock.elapsedRealtime())
            val top = r.offenders.take(MAX_ROWS).map { it.pkg }
            buckets = withContext(Dispatchers.IO) { repo.readBuckets(top) }
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
                onStart = { startPeriod() },
            )
        }
        if (busy) {
            item { Text(stringResource(R.string.standby_working)) }
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
                val firstRate = report.elapsedMs?.takeIf { it >= DrainReport.MIN_RATE_PERIOD_MS }
                Text(
                    text = if (firstRate != null) {
                        val perHour = report.total / (firstRate / 3_600_000.0)
                        stringResource(R.string.report_total_rate, report.total, DrainReport.formatRate(perHour))
                    } else {
                        stringResource(R.string.report_total, report.total)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (rows.isEmpty()) {
                item { Text(stringResource(R.string.report_none)) }
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
    onStart: () -> Unit,
) {
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
            Text(
                text = if (baseline == null) {
                    stringResource(R.string.report_no_period)
                } else {
                    val elapsed = System.currentTimeMillis() - baseline.timeMs
                    stringResource(R.string.report_period_started, DrainReport.formatDuration(elapsed))
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (rebooted) {
                Text(
                    text = stringResource(R.string.report_rebooted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(onClick = onStart, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.report_start))
            }
        }
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
        else -> stringResource(R.string.diag_unknown)
    }
    val rate = o.perHour
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
            LtrMonoText(text = o.pkg, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (rate != null) {
                    stringResource(R.string.report_row_meta_rate, o.wakeups, DrainReport.formatRate(rate))
                } else {
                    stringResource(R.string.report_row_meta, o.wakeups)
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
