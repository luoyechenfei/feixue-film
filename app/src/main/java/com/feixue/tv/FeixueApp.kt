package com.feixue.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.feixue.tv.data.source.SourceManager
import com.feixue.tv.settings.AppSettings
import okhttp3.OkHttpClient

class FeixueApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        SourceManager.init(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    if (request.url.host.contains("doubanio")) {
                        // 豆瓣图床有防盗链，必须带来源页与浏览器 UA
                        chain.proceed(
                            request.newBuilder()
                                .header("Referer", "https://movie.douban.com/")
                                .header("User-Agent", BROWSER_UA)
                                .build(),
                        )
                    } else {
                        chain.proceed(request)
                    }
                }
                .build()
        }
        .crossfade(true)
        .build()

    private companion object {
        const val BROWSER_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
