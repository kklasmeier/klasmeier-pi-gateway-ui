package com.klasmeier.internetgatewaypath.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.klasmeier.internetgatewaypath.MainActivity
import com.klasmeier.internetgatewaypath.R
import com.klasmeier.internetgatewaypath.data.SettingsRepository
import com.klasmeier.internetgatewaypath.ui.PathVisuals
import java.text.DateFormat
import java.util.Date

class PathWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = SettingsRepository(context).widgetSnapshot()
        provideContent {
            PathWidgetContent(
                context = context,
                pathName = snapshot.pathName,
                checkedAtMs = snapshot.checkedAtEpochMs,
                configured = snapshot.configured,
            )
        }
    }
}

@Composable
private fun PathWidgetContent(
    context: Context,
    pathName: String?,
    checkedAtMs: Long?,
    configured: Boolean,
) {
    val size = LocalSize.current
    val compact = size.width < 180.dp || size.height < 110.dp
    val label = if (configured) {
        PathVisuals.label(context, pathName)
    } else {
        context.getString(R.string.widget_setup_needed)
    }
    val iconRes = if (configured) PathVisuals.iconRes(pathName) else R.drawable.ic_path_unknown
    val checkedText = checkedAtMs?.let {
        context.getString(R.string.widget_last_checked, formatTime(it))
    } ?: context.getString(R.string.widget_not_checked)
    val openApp = androidx.glance.action.actionStartActivity<MainActivity>()

    val containerModifier = GlanceModifier
        .fillMaxSize()
        .background(R.color.widget_background)
        .cornerRadius(16.dp)
        .padding(12.dp)
        .clickable(openApp)

    if (compact) {
        Column(
            modifier = containerModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(36.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(R.color.widget_text),
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 2,
            )
        }
    } else {
        Row(
            modifier = containerModifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = label,
                modifier = GlanceModifier.size(48.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(GlanceModifier.width(12.dp))
            Column {
                Text(
                    text = label,
                    style = TextStyle(
                        color = ColorProvider(R.color.widget_text),
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 2,
                )
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = checkedText,
                    style = TextStyle(color = ColorProvider(R.color.widget_subtext)),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun formatTime(epochMs: Long): String {
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMs))
}

class PathWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PathWidget()
}
