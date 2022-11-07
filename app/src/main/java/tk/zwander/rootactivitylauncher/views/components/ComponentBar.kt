package tk.zwander.rootactivitylauncher.views.components

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil.compose.rememberAsyncImagePainter
import com.github.skgmn.composetooltip.AnchorEdge
import com.github.skgmn.composetooltip.Tooltip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tk.zwander.rootactivitylauncher.R
import tk.zwander.rootactivitylauncher.data.AppInfo
import tk.zwander.rootactivitylauncher.data.component.BaseComponentInfo
import tk.zwander.rootactivitylauncher.data.component.ComponentType
import tk.zwander.rootactivitylauncher.util.createShortcut
import tk.zwander.rootactivitylauncher.util.findExtrasForComponent
import tk.zwander.rootactivitylauncher.util.isActuallyEnabled
import tk.zwander.rootactivitylauncher.util.launch.launchActivity
import tk.zwander.rootactivitylauncher.util.launch.launchReceiver
import tk.zwander.rootactivitylauncher.util.launch.launchService
import tk.zwander.rootactivitylauncher.util.openAppInfo
import tk.zwander.rootactivitylauncher.util.setComponentEnabled
import tk.zwander.rootactivitylauncher.util.setPackageEnabled

enum class Availability(val labelRes: Int, val tintRes: Int) {
    EXPORTED(R.string.exported, R.color.colorExported),
    PERMISSION_REQUIRED(R.string.permission_required, R.color.colorNeedsPermission),
    UNEXPORTED(R.string.unexported, R.color.colorUnexported),
    NA(0, 0)
}

sealed class Button<T>(protected val data: T) {
    abstract val iconRes: Int
    abstract val labelRes: Int

    abstract fun onClick(context: Context)

    class ComponentInfoButton(data: Any, private val onClick: (info: Any) -> Unit) : Button<Any>(data) {
        override val iconRes = R.drawable.ic_baseline_help_outline_24
        override val labelRes = R.string.component_info

        override fun onClick(context: Context) {
            onClick(data)
        }
    }

    class IntentDialogButton(data: String, private val onClick: () -> Unit) : Button<String>(data) {
        override val iconRes = R.drawable.tune
        override val labelRes = R.string.intent

        override fun onClick(context: Context) {
            onClick()
        }
    }

    class AppInfoButton(data: String) : Button<String>(data) {
        override val iconRes = R.drawable.about_outline
        override val labelRes = R.string.app_info

        override fun onClick(context: Context) {
            context.openAppInfo(data)
        }
    }

    class SaveApkButton(data: AppInfo, private val onClick: (AppInfo) -> Unit) : Button<AppInfo>(data) {
        override val iconRes = R.drawable.save
        override val labelRes = R.string.extract_apk

        override fun onClick(context: Context) {
            onClick(data)
        }
    }

    class CreateShortcutButton(data: BaseComponentInfo) : Button<BaseComponentInfo>(data) {
        override val iconRes = R.drawable.ic_baseline_link_24
        override val labelRes = R.string.create_shortcut

        override fun onClick(context: Context) {
            context.createShortcut(
                label = data.label,
                icon = IconCompat.createWithBitmap(
                    (data.info.loadIcon(context.packageManager) ?: ContextCompat.getDrawable(
                        context,
                        R.mipmap.ic_launcher
                    ))!!.toBitmap()
                ),
                componentKey = data.component.flattenToString(),
                componentType = data.type()
            )
        }
    }

    class LaunchButton(data: BaseComponentInfo) : Button<BaseComponentInfo>(data) {
        override val iconRes = R.drawable.ic_baseline_open_in_new_24
        override val labelRes = R.string.launch

        override fun onClick(context: Context) {
            val componentKey = data.component.flattenToString()

            val extras = context.findExtrasForComponent(data.component.packageName) +
                    context.findExtrasForComponent(componentKey)

            when (data.type()) {
                ComponentType.ACTIVITY -> context.launchActivity(extras, componentKey)
                ComponentType.SERVICE -> context.launchService(extras, componentKey)
                ComponentType.RECEIVER -> context.launchReceiver(extras, componentKey)
            }
        }
    }
}

@Composable
fun AppBar(
    icon: Painter,
    name: String,
    app: AppInfo,
    whichButtons: List<Button<*>>,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
) {
    val context = LocalContext.current

    var enabled by remember {
        mutableStateOf(app.info.isActuallyEnabled(context))
    }

    BarGuts(
        icon = icon,
        name = name,
        subLabel = app.info.packageName,
        enabled = enabled,
        availability = Availability.NA,
        onEnabledChanged = {
            if (context.setPackageEnabled(app.info.packageName, it)) {
                enabled = it
            }
        },
        whichButtons = whichButtons,
        modifier = modifier,
        showActions = showActions
    )
}

@Composable
fun ComponentBar(
    icon: Painter,
    name: String,
    component: BaseComponentInfo,
    whichButtons: List<Button<*>>,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
) {
    val context = LocalContext.current

    var enabled by remember {
        mutableStateOf(component.info.isActuallyEnabled(context))
    }

    BarGuts(
        icon = icon,
        name = name,
        subLabel = component.component.flattenToString(),
        enabled = enabled,
        onEnabledChanged = {
            if (context.setComponentEnabled(component, it)) {
                enabled = it
            }
        },
        availability = run {
            val requiresPermission = (component.info as? ActivityInfo)?.permission != null ||
                    (component.info as? ServiceInfo)?.permission != null

            when {
                !component.info.exported -> Availability.UNEXPORTED
                requiresPermission -> Availability.PERMISSION_REQUIRED
                else -> Availability.EXPORTED
            }
        },
        whichButtons = whichButtons,
        modifier = modifier,
        showActions = showActions
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarGuts(
    icon: Painter,
    name: String,
    subLabel: String,
    enabled: Boolean,
    onEnabledChanged: suspend (Boolean) -> Unit,
    availability: Availability,
    whichButtons: List<Button<*>>,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
) {
    val actualButtons = whichButtons.filter { enabled || it !is Button.LaunchButton }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(if (availability == Availability.NA) 48.dp else 36.dp)
            ) {
                Image(
                    painter = icon,
                    contentDescription = name,
                    modifier = Modifier
                        .size(if (availability == Availability.NA) 48.dp else 32.dp)
                        .align(Alignment.Center)
                )

                if (availability != Availability.NA) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .align(Alignment.BottomEnd)
                            .background(colorResource(id = availability.tintRes))
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = subLabel,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            if (showActions) {
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        scope.launch(Dispatchers.Main) {
                            onEnabledChanged(it)
                        }
                    }
                )
            }
        }

        if (showActions) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                actualButtons.forEach { button ->
                    var showingTooltip by remember {
                        mutableStateOf(false)
                    }

                    IconButton(
                        onClick = { button.onClick(context) },
                        modifier = Modifier.combinedClickable(
                            interactionSource = remember {
                                MutableInteractionSource()
                            },
                            indication = rememberRipple(),
                            enabled = true,
                            onLongClick = {
                                showingTooltip = true
                            },
                            onClick = {
                                button.onClick(context)
                            }
                        )
                    ) {
                        Icon(
                            painter = rememberAsyncImagePainter(model = button.iconRes),
                            contentDescription = stringResource(id = button.labelRes)
                        )

                        if (showingTooltip) {
                            Tooltip(
                                anchorEdge = AnchorEdge.Top,
                                onDismissRequest = { showingTooltip = false }
                            ) {
                                Text(text = stringResource(id = button.labelRes))
                            }
                        }
                    }
                }
            }
        }
    }
}
