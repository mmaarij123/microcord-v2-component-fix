package io.github.mmaarij123.v2componentfix

import android.app.Application
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.discord.models.message.Message
import com.discord.stores.StoreStream
import dev.mantikafasi.manticord.plugins.api.ManticordPlugin
import dev.mantikafasi.manticord.plugins.api.PluginContextMenuContext
import dev.mantikafasi.manticord.plugins.api.PluginContextMenuSurface
import dev.mantikafasi.manticord.plugins.api.PluginMessageInterceptionType
import dev.mantikafasi.manticord.plugins.api.PluginScope
import dev.mantikafasi.manticord.plugins.api.pluginContextMenuItem
import dev.mantikafasi.manticord.plugins.api.pluginMessageInterceptor
import rx.Completable
import rx.schedulers.Schedulers

class V2ComponentFixPlugin : ManticordPlugin {
    private val compactCards = CompactCardLayout()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainThread = Schedulers.from { task -> mainHandler.post(task) }

    override fun start(scope: PluginScope): Completable = Completable.fromAction {
        scope.ui.registerMessageInterceptor(
            pluginMessageInterceptor(
                "repair_components_v2",
                PluginMessageInterceptionType.RECEIVE,
                transform = Message::repairComponentsV2,
            ),
        )
        scope.ui.registerContextMenuItem(
            pluginContextMenuItem(
                "preview_repaired_components_v2",
                "Open repaired Components V2",
                PluginContextMenuSurface.MESSAGE,
                onClick = { menu ->
                    val original = menu.loadedMessage() ?: return@pluginContextMenuItem
                    val repaired = original.repairComponentsV2()
                    if (repaired === original) return@pluginContextMenuItem
                    scope.dialogs.page("Components V2") { context ->
                        createRepairedPreview(context, original, repaired)
                    }
                },
            ) {
                isVisible = { menu ->
                    val original = menu.loadedMessage()
                    original != null && original.repairComponentsV2() !== original
                }
            },
        )
    }.andThen(onMainThread { compactCards.start(scope.applicationContext as Application) })

    override fun stop(): Completable = onMainThread(compactCards::stop)

    private fun onMainThread(action: () -> Unit): Completable =
        Completable.fromAction(action).subscribeOn(mainThread)
}

class PluginDiscoveryService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}

private fun PluginContextMenuContext.loadedMessage(): Message? {
    val channel = channelId ?: return null
    val message = messageId ?: return null
    return StoreStream.getMessages().getMessage(channel, message)
}
