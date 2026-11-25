package com.personal.myfiles

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.personal.myfiles.core.AppContainer
import com.personal.myfiles.core.Storage
import kotlin.concurrent.thread

class MyFilesApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Storage.init(this)
        container = AppContainer(this)
        thread(name = "trash-purge") {
            runCatching { container.trash.purge(container.settings.trashDays) }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
