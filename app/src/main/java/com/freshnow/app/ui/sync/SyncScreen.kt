package com.freshnow.app.ui.sync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.freshnow.app.R
import com.freshnow.app.data.local.SyncPeer
import com.freshnow.app.data.sync.DiscoveredPeer
import com.freshnow.app.ui.component.FreshNowSubPage
import com.freshnow.app.ui.component.ListItemText
import com.freshnow.app.ui.component.SectionHeading
import com.freshnow.app.ui.openAppSettings
import com.freshnow.app.ui.showToast
import com.freshnow.app.ui.theme.FreshNowSpacing

/**
 * 局域网同步的总览页：本机是谁、已经和谁配上、附近还有谁、以及手动同步。
 *
 * 进页面就开始找设备、离开就停：发现要持组播锁，锁着不放会持续耗电，而用户看这一页的时间
 * 通常只有几秒。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = viewModel()
) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val discovered by viewModel.discovered.collectAsStateWithLifecycle()
    val pairingCode by viewModel.pairingCode.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val localAddresses by viewModel.localAddresses.collectAsStateWithLifecycle()

    var pairingWith by remember { mutableStateOf<DiscoveredPeer?>(null) }
    var manualPairing by remember { mutableStateOf(false) }
    val permission = rememberLocalNetworkPermission()

    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }

    FreshNowSubPage(
        title = stringResource(R.string.sync_title),
        onBack = onBack,
        modifier = modifier,
        actions = {
            // 同步期间置灰：一次同步要打包、往返、写库，连点只会让第二次在第一次还没写完时插进去
            IconButton(onClick = viewModel::syncNow, enabled = !syncing) {
                Icon(
                    painter = painterResource(R.drawable.ic_sync),
                    contentDescription = stringResource(R.string.sync_now)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            if (!permission.granted) {
                PermissionNotice(
                    blocked = permission.blocked,
                    onRequest = permission.request
                )
            }

            SectionHeading(
                text = stringResource(R.string.sync_local_device_title),
                modifier = Modifier.padding(top = FreshNowSpacing.sm)
            )
            // 设备名那一行把动作写在标题上，整行可点，尾部不放按钮：M3 的列表项尾部
            // 惯例是图标或开关，在一行本来以文字为主的地方塞一个文字按钮进去，
            // 会分不清哪个是标题、哪个能点
            ListItem(
                leadingContent = {
                    Icon(painter = painterResource(R.drawable.ic_devices), contentDescription = null)
                },
                headlineContent = {
                    ListItemText(
                        headline = stringResource(R.string.sync_show_pairing_code),
                        supporting = viewModel.deviceName
                    )
                },
                modifier = Modifier.clickable(onClick = viewModel::openPairingCode),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            // 手动填地址这一条与「显示配对码」并列：两者都是建立配对的路，
            // 只是一个等对方来、一个主动去连。原先它缩在「附近设备」那一块底下，
            // 读起来像那一块的子项，而它其实正是「附近一个都发现不到」时的替代
            ListItem(
                leadingContent = {
                    Icon(painter = painterResource(R.drawable.ic_add_link), contentDescription = null)
                },
                headlineContent = {
                    ListItemText(
                        stringResource(R.string.sync_manual_add),
                        stringResource(R.string.sync_manual_add_support)
                    )
                },
                modifier = Modifier.clickable { manualPairing = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )

            SectionHeading(
                text = stringResource(R.string.sync_paired_devices_title),
                modifier = Modifier.padding(top = FreshNowSpacing.md)
            )
            // 没配对过就说一句：这一段空着的话，用户看不出是「还没有」还是「没加载出来」
            if (peers.isEmpty()) {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(R.string.sync_no_paired_devices))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            } else {
                peers.forEach { peer ->
                    PairedDeviceRow(peer = peer, onForget = { viewModel.forget(peer.deviceId) })
                }
            }

            SectionHeading(
                text = stringResource(R.string.sync_nearby_devices_title),
                modifier = Modifier.padding(top = FreshNowSpacing.md)
            )
            // 已经配上过的不再列出来：用户要的是「还有谁可以配」，不是网络里所有设备
            val candidates = discovered.filterNot { found ->
                peers.any { it.deviceId == found.deviceId }
            }
            if (candidates.isEmpty()) {
                // 「还没出结果」与「确实没有」是两种状态，说的话也不同：
                // 前者只是在等，后者要告诉用户去查什么
                if (discovered.isEmpty()) {
                    ListItem(
                        headlineContent = {
                            Text(text = stringResource(R.string.sync_nearby_searching))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                } else {
                    // 空态给图标与说明，而不只是一行字：一行字借列表项的壳读起来像
                    // 「一条内容」，看不出这里是「没有内容」
                    ListItem(
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_devices),
                                contentDescription = null
                            )
                        },
                        headlineContent = {
                            ListItemText(
                                headline = stringResource(R.string.sync_nearby_empty),
                                supporting = stringResource(R.string.sync_nearby_empty_support)
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            } else {
                candidates.forEach { found ->
                    ListItem(
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_add_link),
                                contentDescription = null
                            )
                        },
                        headlineContent = {
                            Text(text = found.deviceName.ifBlank { found.deviceId.take(8) })
                        },
                        modifier = Modifier.clickable { pairingWith = found },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }

    // 上一次操作的结果用 Toast 报，不占列表里的一行。用的是全应用既有的那个约定
    // （见 ui/Toast.kt）：这类提示不承载可撤销的操作，为它单独搭一套 Snackbar 宿主
    // 会让同一件事在两个地方有两种长相。
    //
    // 报完就销掉：留在状态里的话，用户切走再回来会再弹一次，而那次什么操作都没发生。
    val context = LocalContext.current
    // 文案先在合成上下文里算好：text() 是 @Composable，而 LaunchedEffect 里跑的是普通协程
    val messageText = message?.text()
    message?.let {
        LaunchedEffect(it) {
            showToast(context, messageText.orEmpty())
            viewModel.consumeMessage()
        }
    }

    pairingCode?.let { code ->
        ModalBottomSheet(onDismissRequest = viewModel::closePairingCode) {
            PairingCodeContent(code = code, addresses = localAddresses)
        }
    }

    pairingWith?.let { peer ->
        PairingCodeDialog(
            peerName = peer.deviceName.ifBlank { peer.deviceId.take(8) },
            onDismiss = { pairingWith = null },
            onConfirm = { code ->
                viewModel.pair(peer, code)
                pairingWith = null
            }
        )
    }

    if (manualPairing) {
        ManualPairingDialog(
            onDismiss = { manualPairing = false },
            onConfirm = { address, code ->
                viewModel.pairWithAddress(address, code)
                manualPairing = false
            }
        )
    }
}

@Composable
private fun PairedDeviceRow(peer: SyncPeer, onForget: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(painter = painterResource(R.drawable.ic_mobile), contentDescription = null)
        },
        headlineContent = {
            ListItemText(
                peer.deviceName.ifBlank { peer.deviceId.take(8) },
                // 还没连上过就没有地址可说，此时不写这一行，而不是写一句占位的话——
                // 「尚未连接过」和「连接过一次但地址已失效」在用户眼里是同一件事
                peer.lastAddress.takeIf { it.isNotBlank() }
            )
        },
        trailingContent = {
            IconButton(onClick = onForget) {
                Icon(
                    painter = painterResource(R.drawable.ic_link_off),
                    contentDescription = stringResource(R.string.sync_forget)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

/** 本机配对码。字号给到 display：对面那台设备的人要照着念，越大越省事 */
@Composable
private fun PairingCodeContent(code: String, addresses: List<String>) {
    Column(modifier = Modifier.padding(bottom = FreshNowSpacing.lg)) {
        Text(
            text = stringResource(R.string.sync_pairing_code_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                start = FreshNowSpacing.sm,
                end = FreshNowSpacing.sm,
                bottom = FreshNowSpacing.xs
            )
        )
        Text(
            text = code,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = FreshNowSpacing.xs)
        )
        Text(
            text = stringResource(R.string.sync_pairing_code_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = FreshNowSpacing.sm)
        )

        // 地址连同配对码一起给：发现不到的时候，对方除了这个码还要知道往哪个地址连
        addresses.forEach { address ->
            Text(
                text = "${stringResource(R.string.sync_local_address_title)}：$address",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(
                    start = FreshNowSpacing.sm,
                    end = FreshNowSpacing.sm,
                    top = FreshNowSpacing.xs
                )
            )
        }
    }
}

@Composable
private fun ManualPairingDialog(
    onDismiss: () -> Unit,
    onConfirm: (address: String, code: String) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sync_manual_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.trim() },
                    label = { Text(text = stringResource(R.string.sync_manual_address_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        code = input.filter { it.isDigit() }.take(PAIRING_CODE_LENGTH)
                    },
                    label = { Text(text = stringResource(R.string.sync_enter_code_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = FreshNowSpacing.xs)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(address, code) },
                enabled = address.isNotEmpty() && code.length == PAIRING_CODE_LENGTH
            ) {
                Text(text = stringResource(R.string.sync_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun PairingCodeDialog(
    peerName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.sync_enter_code_title)) },
        text = {
            Column {
                Text(text = peerName, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        // 只收数字且最多 6 位：配对码就是这个形状，早挡住比让用户输完再报错好
                        code = input.filter { it.isDigit() }.take(PAIRING_CODE_LENGTH)
                    },
                    label = { Text(text = stringResource(R.string.sync_enter_code_label)) },
                    supportingText = { Text(text = stringResource(R.string.sync_enter_code_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = FreshNowSpacing.xs)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(code) },
                enabled = code.length == PAIRING_CODE_LENGTH
            ) {
                Text(text = stringResource(R.string.sync_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun PermissionNotice(blocked: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        headlineContent = {
            ListItemText(
                stringResource(R.string.sync_permission_title),
                stringResource(
                    if (blocked) R.string.sync_permission_blocked else R.string.sync_permission_support
                )
            )
        },
        trailingContent = {
            TextButton(
                onClick = {
                    if (blocked) {
                        openAppSettings(context)
                    } else {
                        onRequest()
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        if (blocked) R.string.sync_open_system_settings else R.string.sync_permission_grant
                    )
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private class LocalNetworkPermissionState(
    val granted: Boolean,
    val blocked: Boolean,
    val request: () -> Unit
)

@Composable
private fun rememberLocalNetworkPermission(): LocalNetworkPermissionState {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasLocalNetworkPermission()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        granted = result
        asked = true
    }

    // 权限在系统设置里也能改，回来时以真实权限为准
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasLocalNetworkPermission()
    }

    return LocalNetworkPermissionState(
        granted = granted,
        blocked = asked && !granted,
        request = { launcher.launch(LOCAL_NETWORK_PERMISSION) }
    )
}

/**
 * 这个权限是 Android 17（API 37）才有的，在那之前的系统上它根本不存在——直接去检查会一律回
 * 「未授予」，于是所有老设备一进本页就顶着一句要授权的提示，而那句提示在那里毫无意义。
 * 所以低版本一律当作「不需要」，不去检查也不去请求。
 */
private fun Context.hasLocalNetworkPermission(): Boolean =
    Build.VERSION.SDK_INT < LOCAL_NETWORK_PERMISSION_API ||
        ContextCompat.checkSelfPermission(this, LOCAL_NETWORK_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

private const val PAIRING_CODE_LENGTH = 6

/** 见 [Context.hasLocalNetworkPermission] 的说明 */
private const val LOCAL_NETWORK_PERMISSION_API = 37

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
