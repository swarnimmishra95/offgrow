package app.offgrow.garden

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Draws the garden. The engine (assets/garden/garden.js) builds an SVG; a hidden WebView
 * rasterises it to a JPEG, which we hand back as bytes.
 */
object GardenRenderer {
    private const val TAG = "GardenRenderer"
    private val mutex = Mutex()
    private var engineJs: String? = null

    /** When the app is open, the activity lends its window so the WebView renders attached. */
    @Volatile
    var host: WeakReference<ViewGroup>? = null

    suspend fun renderJpeg(context: Context, cfg: JSONObject, sizePx: Int): ByteArray? = mutex.withLock {
        val app = context.applicationContext
        val js = engineJs ?: withContext(Dispatchers.IO) {
            app.assets.open("garden/garden.js").bufferedReader().use { it.readText() }
        }.also { engineJs = it }
        withTimeoutOrNull(30_000) {
            withContext(Dispatchers.Main) { renderOnMain(app, js, cfg, sizePx) }
        }
    }

    suspend fun renderBitmap(context: Context, cfg: JSONObject, sizePx: Int): Bitmap? {
        val bytes = renderJpeg(context, cfg, sizePx) ?: return null
        return withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private suspend fun renderOnMain(context: Context, engine: String, cfg: JSONObject, size: Int): ByteArray? =
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            val web = try {
                WebView(context)
            } catch (t: Throwable) {
                // Android System WebView missing, disabled or mid-update.
                Log.w(TAG, "WebView unavailable", t)
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            var finished = false
            val parent = host?.get()

            fun finish(result: ByteArray?) {
                main.post {
                    if (finished) return@post
                    finished = true
                    try {
                        (web.parent as? ViewGroup)?.removeView(web)
                        web.stopLoading()
                        web.destroy()
                    } catch (_: Exception) {
                    }
                    if (cont.isActive) cont.resume(result)
                }
            }

            web.settings.javaScriptEnabled = true
            web.settings.allowFileAccess = false
            web.settings.allowContentAccess = false
            web.setBackgroundColor(0)
            web.addJavascriptInterface(object {
                @JavascriptInterface
                fun done(dataUrl: String) {
                    val bytes = try {
                        val comma = dataUrl.indexOf(',')
                        Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
                    } catch (e: Exception) {
                        Log.w(TAG, "decode failed", e)
                        null
                    }
                    finish(bytes)
                }

                @JavascriptInterface
                fun fail(reason: String) {
                    Log.w(TAG, "render failed: $reason")
                    finish(null)
                }
            }, "OffgrowBridge")
            web.webViewClient = object : WebViewClient() {
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    Log.w(TAG, "render process gone")
                    finish(null)
                    return true
                }
            }

            if (parent != null) {
                try {
                    web.alpha = 0f
                    web.visibility = View.VISIBLE
                    parent.addView(web, 0, ViewGroup.LayoutParams(2, 2))
                } catch (_: Exception) {
                }
            } else {
                web.measure(
                    View.MeasureSpec.makeMeasureSpec(2, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(2, View.MeasureSpec.EXACTLY),
                )
                web.layout(0, 0, 2, 2)
            }

            cont.invokeOnCancellation { finish(null) }
            val html = buildHtml(engine, cfg, size)
            web.loadDataWithBaseURL("https://garden.offgrow.app/", html, "text/html", "utf-8", null)
        }

    private fun buildHtml(engine: String, cfg: JSONObject, size: Int): String {
        // Keep "</script>" out of the inline engine source.
        val safeEngine = engine.replace("</script", "<\\/script")
        val safeCfg = cfg.toString().replace("</", "<\\/")
        return """<!doctype html><html><head><meta charset="utf-8"></head><body style="margin:0;background:#F3EEE3">
<script>$safeEngine</script>
<script>
(function () {
  function fail(m) { try { OffgrowBridge.fail(String(m)); } catch (e) {} }
  try {
    var size = $size;
    var svg = OffgrowGarden.render($safeCfg);
    svg = svg.replace('width="400" height="400"', 'width="' + size + '" height="' + size + '"');
    var url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' }));
    var img = new Image();
    img.onload = function () {
      try {
        var c = document.createElement('canvas');
        c.width = size; c.height = size;
        var g = c.getContext('2d');
        g.fillStyle = '#F3EEE3';
        g.fillRect(0, 0, size, size);
        g.drawImage(img, 0, 0, size, size);
        URL.revokeObjectURL(url);
        OffgrowBridge.done(c.toDataURL('image/jpeg', 0.9));
      } catch (e) { fail('draw: ' + e); }
    };
    img.onerror = function () { fail('image load'); };
    img.src = url;
  } catch (e) { fail('engine: ' + e); }
})();
</script></body></html>"""
    }
}
