package com.daniil.watchprivatedns

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrivateDnsApp() }
    }
}

@Composable
private fun PrivateDnsApp() {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val systemScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }
    val wearScheme = ColorScheme(
        primary = systemScheme.primary,
        primaryDim = systemScheme.primary,
        primaryContainer = systemScheme.primaryContainer,
        onPrimary = systemScheme.onPrimary,
        onPrimaryContainer = systemScheme.onPrimaryContainer,
        secondary = systemScheme.secondary,
        secondaryDim = systemScheme.secondary,
        secondaryContainer = systemScheme.secondaryContainer,
        onSecondary = systemScheme.onSecondary,
        onSecondaryContainer = systemScheme.onSecondaryContainer,
        tertiary = systemScheme.tertiary,
        tertiaryDim = systemScheme.tertiary,
        tertiaryContainer = systemScheme.tertiaryContainer,
        onTertiary = systemScheme.onTertiary,
        onTertiaryContainer = systemScheme.onTertiaryContainer,
        surfaceContainerLow = systemScheme.surfaceContainerLow,
        surfaceContainer = systemScheme.surfaceContainer,
        surfaceContainerHigh = systemScheme.surfaceContainerHigh,
        onSurface = systemScheme.onSurface,
        onSurfaceVariant = systemScheme.onSurfaceVariant,
        outline = systemScheme.outline,
        outlineVariant = systemScheme.outlineVariant,
        background = systemScheme.background,
        onBackground = systemScheme.onBackground,
        error = systemScheme.error,
        errorDim = systemScheme.error,
        errorContainer = systemScheme.errorContainer,
        onError = systemScheme.onError,
        onErrorContainer = systemScheme.onErrorContainer
    )

    MaterialTheme(colorScheme = wearScheme) {
        AppScaffold {
            PrivateDnsScreen()
        }
    }
}

@Composable
private fun PrivateDnsScreen() {
    val context = LocalContext.current
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()

    DnsPreferences.ensureInitialized(context)
    val initialDesired = remember { DnsPreferences.desired(context) }

    var systemState by remember { mutableStateOf(readDnsState(context)) }
    var selectedMode by remember { mutableStateOf(initialDesired.mode) }
    var hostname by remember { mutableStateOf(initialDesired.hostname.ifBlank { systemState.hostname }) }
    var permissionGranted by remember { mutableStateOf(hasWriteSecureSettings(context)) }
    var history by remember { mutableStateOf(DnsPreferences.history(context)) }
    var bluetoothProxy by remember { mutableStateOf(DnsPolicy.isBluetoothProxyAvailable(context)) }
    var fallbackReason by remember { mutableStateOf(DnsPreferences.fallbackReason(context)) }
    var failedHost by remember { mutableStateOf(DnsPreferences.failureHost(context)) }

    fun refresh() {
        permissionGranted = hasWriteSecureSettings(context)
        systemState = readDnsState(context)
        val desired = DnsPreferences.desired(context)
        selectedMode = desired.mode
        if (desired.hostname.isNotBlank()) hostname = desired.hostname
        history = DnsPreferences.history(context)
        bluetoothProxy = DnsPolicy.isBluetoothProxyAvailable(context)
        fallbackReason = DnsPreferences.fallbackReason(context)
        failedHost = DnsPreferences.failureHost(context)
    }

    fun apply() {
        if (!permissionGranted) {
            Toast.makeText(context, "Нужно разрешение WRITE_SECURE_SETTINGS", Toast.LENGTH_LONG).show()
            return
        }

        when (DnsPolicy.applyUserChoice(context, selectedMode, hostname)) {
            UserApplyResult.APPLIED -> {
                val message = if (selectedMode == DnsMode.MANUAL) {
                    "DNS применён, выполняется проверка"
                } else {
                    "Настройка применена"
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }

            UserApplyResult.DEFERRED_BY_BLUETOOTH -> Toast.makeText(
                context,
                "Сохранено. Через телефон используется Автоматически",
                Toast.LENGTH_LONG
            ).show()

            UserApplyResult.BAD_HOSTNAME -> Toast.makeText(
                context,
                "Введите hostname, например dns.google",
                Toast.LENGTH_LONG
            ).show()

            UserApplyResult.DENIED -> Toast.makeText(
                context,
                "Система отклонила изменение",
                Toast.LENGTH_LONG
            ).show()
        }
        refresh()
    }

    LaunchedEffect(Unit) {
        DnsPolicy.evaluateConnectivityPolicy(context)
        refresh()
    }

    DisposableEffect(Unit) {
        DnsConnectivityMonitor.start(context)
        onDispose { }
    }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(
                onClick = { apply() },
                enabled = permissionGranted,
                buttonSize = EdgeButtonSize.Small
            ) {
                Text("Применить")
            }
        }
    ) { contentPadding ->
        TransformingLazyColumn(
            modifier = Modifier.selectableGroup(),
            state = listState,
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text("Private DNS")
                }
            }

            item {
                StatusPanel(
                    state = systemState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                )
            }

            if (bluetoothProxy || fallbackReason == FallbackReason.DNS_VALIDATION_FAILED) {
                item {
                    AutomationPanel(
                        bluetoothProxy = bluetoothProxy,
                        fallbackReason = fallbackReason,
                        failedHost = failedHost,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
            }

            item {
                RadioButton(
                    selected = selectedMode == DnsMode.OFF,
                    onSelect = { selectedMode = DnsMode.OFF },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("Отключён") },
                    secondaryLabel = { Text("Не использовать Private DNS") }
                )
            }

            item {
                RadioButton(
                    selected = selectedMode == DnsMode.AUTO,
                    onSelect = { selectedMode = DnsMode.AUTO },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("Автоматически") },
                    secondaryLabel = { Text("Защищённый DNS при доступности") }
                )
            }

            item {
                RadioButton(
                    selected = selectedMode == DnsMode.MANUAL,
                    onSelect = { selectedMode = DnsMode.MANUAL },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    label = { Text("Вручную") },
                    secondaryLabel = { Text("DNS-over-TLS hostname") }
                )
            }

            if (selectedMode == DnsMode.MANUAL) {
                item {
                    HostnameEditor(
                        value = hostname,
                        onValueChange = { hostname = it },
                        onDone = { apply() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                    )
                }

                if (history.isNotEmpty()) {
                    item {
                        ListHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Text("Недавние DNS")
                        }
                    }

                    history.forEach { savedHost ->
                        item(key = "history_$savedHost") {
                            Button(
                                onClick = {
                                    hostname = savedHost
                                    selectedMode = DnsMode.MANUAL
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec),
                                transformation = SurfaceTransformation(transformationSpec),
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                label = {
                                    Text(
                                        savedHost,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                secondaryLabel = { Text("Выбрать из истории") }
                            )
                        }
                    }
                }
            }

            if (!permissionGranted) {
                item {
                    PermissionPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec)
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        DnsPolicy.evaluateConnectivityPolicy(context)
                        DnsPolicy.validatePendingManualDns(context)
                        refresh()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    label = { Text("Обновить") },
                    secondaryLabel = { Text("Перечитать состояние и автоматику") }
                )
            }
        }
    }
}

@Composable
private fun StatusPanel(
    state: DnsState,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    val primary = when (state.mode) {
        DnsMode.OFF -> "Сейчас: отключён"
        DnsMode.AUTO -> "Сейчас: автоматически"
        DnsMode.MANUAL -> if (state.hostname.isBlank()) "Сейчас: вручную" else "Сейчас: ${state.hostname}"
    }
    Button(
        onClick = {},
        modifier = modifier,
        transformation = transformation,
        enabled = false,
        label = {
            Text(primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        secondaryLabel = {
            Text(if (state.readable) "Фактическая системная настройка" else "Не удалось прочитать")
        }
    )
}

@Composable
private fun AutomationPanel(
    bluetoothProxy: Boolean,
    fallbackReason: FallbackReason,
    failedHost: String,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    val label: String
    val secondary: String

    if (bluetoothProxy) {
        label = "Интернет через телефон"
        secondary = "Private DNS временно: Автоматически. Выбранный режим вернётся после отключения Bluetooth-прокси."
    } else if (fallbackReason == FallbackReason.DNS_VALIDATION_FAILED) {
        label = "DNS не прошёл проверку"
        secondary = if (failedHost.isBlank()) {
            "Включён режим Автоматически"
        } else {
            "$failedHost недоступен. Включён режим Автоматически."
        }
    } else {
        label = "Автоматическое управление"
        secondary = "Активно"
    }

    Button(
        onClick = {},
        modifier = modifier,
        transformation = transformation,
        enabled = false,
        label = { Text(label, maxLines = 2) },
        secondaryLabel = { Text(secondary, maxLines = 4) }
    )
}

@Composable
private fun HostnameEditor(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(28.dp)
            )
            .clickable { focusRequester.requestFocus() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            "Провайдер",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onDone()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            "dns.google",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

@Composable
private fun PermissionPanel(
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null
) {
    Button(
        onClick = {},
        modifier = modifier,
        transformation = transformation,
        enabled = false,
        label = { Text("Нет системного разрешения") },
        secondaryLabel = {
            Text("Выдайте WRITE_SECURE_SETTINGS через ADB один раз", maxLines = 3)
        }
    )
}
