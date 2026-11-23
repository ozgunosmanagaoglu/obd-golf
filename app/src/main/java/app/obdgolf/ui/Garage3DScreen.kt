package app.obdgolf.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import app.obdgolf.LinkState
import app.obdgolf.core.CarData
import kotlin.math.roundToInt

private const val GARAGE_URL = "https://appassets.androidplatform.net/assets/garage3d/index.html"

/**
 * The 3D garage (three.js page bundled in assets/garage3d, works offline).
 * With the real car connected, the page's engine follows the live rpm;
 * otherwise it runs its own simulation with the "hold to rev" button.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun Garage3DScreen(d: CarData, link: LinkState, demo: Boolean) {
    val webView = remember { mutableStateOf<WebView?>(null) }
    val live = link is LinkState.Connected && !demo

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val loader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(ctx))
                .build()
            WebView(ctx).apply {
                setBackgroundColor(Color.rgb(5, 6, 8))
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)
                }
                loadUrl(GARAGE_URL)
                webView.value = this
            }
        },
        onRelease = { it.destroy() },
    )

    LaunchedEffect(live, d.rpm) {
        if (live) webView.value?.evaluateJavascript("window.golf && window.golf.setRpm(${(d.rpm ?: 0.0).roundToInt()})", null)
    }
}
