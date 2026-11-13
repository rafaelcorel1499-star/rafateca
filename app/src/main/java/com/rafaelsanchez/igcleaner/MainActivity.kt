package com.rafaelsanchez.igcleaner

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.delay
import org.json.JSONObject

private val Purple = Color(0xFF6A11FF)
private val BgColor = Color(0xFFFBF5FF)
private val LavenderCard = Color(0xFFEADFFA)
private val DangerRed = Color(0xFFC62828)
private val Ink = Color(0xFF14102B)
private val Muted = Color(0xFF5C5670)
private val IgGradient = Brush.horizontalGradient(
    listOf(Color(0xFF6A11FF), Color(0xFFE5129B), Color(0xFFFF7A45), Color(0xFFFFC82C))
)
private const val LOGIN_URL = "https://www.instagram.com/accounts/login/"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Purple)) { App() }
        }
    }
}

@Composable
fun App(vm: MainViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    Box(Modifier.fillMaxSize().background(BgColor).systemBarsPadding()) {
        when (s.screen) {
            Screen.LOGIN -> LoginScreen(vm, s)
            Screen.WEBLOGIN -> WebLoginScreen(vm)
            Screen.PANEL -> PanelScreen(vm, s)
        }
    }
}

@Composable
fun Signature() {
    Text(
        "BY RAFAEL SANCHEZ",
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp
    )
}

@Composable
fun InstaLogo(dp: Dp, corner: Float = 0.26f) {
    Canvas(Modifier.size(dp)) {
        val s = this.size.minDimension
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xFF5B2BFF), Color(0xFFD10FB5), Color(0xFFFF7A2F), Color(0xFFFFD23C)),
                start = Offset(0f, 0f), end = Offset(s, s)
            ),
            cornerRadius = CornerRadius(s * corner)
        )
        val st = s * 0.065f
        drawRoundRect(Color.White, Offset(s * 0.17f, s * 0.17f), Size(s * 0.66f, s * 0.66f),
            CornerRadius(s * 0.18f), Stroke(st))
        drawCircle(Color.White, s * 0.16f, Offset(s / 2, s / 2), style = Stroke(st))
        drawCircle(Color.White, s * 0.04f, Offset(s * 0.69f, s * 0.31f))
    }
}

@Composable
fun GradientButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) IgGradient else SolidColor(Color(0xFFCFC6DA)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 3.sp)
    }
}

// ---------------------------------------------------------------- LOGIN

@Composable
fun LoginScreen(vm: MainViewModel, s: UiState) {
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        InstaLogo(130.dp)
        Spacer(Modifier.height(28.dp))
        Text("Connect your\nInstagram account", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center, color = Ink, lineHeight = 36.sp)
        Spacer(Modifier.height(12.dp))
        Text("Enter your credentials to\nmanage your profile feed.", fontSize = 17.sp,
            textAlign = TextAlign.Center, color = Muted)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = user, onValueChange = { user = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Username or email") },
            leadingIcon = { Icon(Icons.Outlined.Person, null) }
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Password") },
            leadingIcon = { Icon(Icons.Outlined.Lock, null) },
            trailingIcon = {
                IconButton({ show = !show }) {
                    Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null)
                }
            },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        s.message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = DangerRed, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))
        GradientButton("SIGN IN", enabled = user.isNotBlank() && pass.isNotEmpty()) {
            vm.startLogin(user, pass)
            pass = ""
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text("  or  ", color = Muted)
            HorizontalDivider(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(LavenderCard).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = Purple, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(14.dp))
            Text("Your credentials are used only for this session and are never stored.",
                color = Ink, fontSize = 15.sp)
        }
        Spacer(Modifier.height(20.dp))
        TextButton({ help = true }) {
            Text("Need help?", color = Purple, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.weight(1f))
        Signature()
    }

    if (help) AlertDialog(
        onDismissRequest = { help = false },
        confirmButton = { TextButton({ help = false }) { Text("OK") } },
        title = { Text("Need help?") },
        text = {
            Text("Sign in with your Instagram username or email and password. If Instagram asks for a " +
                "verification code or to confirm it's you, complete it in the window that opens.")
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(vm: MainViewModel) {
    BackHandler { vm.cancelLogin() }
    LaunchedEffect(Unit) { while (true) { delay(1000); vm.checkSession() } }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }
    var url by remember { mutableStateOf("") }
    var console by remember { mutableStateOf("") }
    var web by remember { mutableStateOf<WebView?>(null) }
    val wvVersion = remember {
        try { WebView.getCurrentWebViewPackage()?.versionName ?: "unknown" } catch (e: Exception) { "unknown" }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Instagram sign in", Modifier.weight(1f).padding(start = 8.dp),
                fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Ink)
            TextButton({ error = null; web?.loadUrl(LOGIN_URL) }) { Text("Reload", color = Purple) }
            TextButton({ vm.cancelLogin() }) { Text("Cancel", color = Purple) }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Purple)
        }
        Text(
            "WebView $wvVersion  |  $progress%\n$url" + if (console.isNotEmpty()) "\nJS: $console" else "",
            color = Muted, fontSize = 10.sp, lineHeight = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp), maxLines = 5
        )
        error?.let {
            Text(it, color = DangerRed, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                var injected = 0
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.databaseEnabled = true
                    settings.javaScriptCanOpenWindowsAutomatically = true
                    settings.setSupportMultipleWindows(false)
                    settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    // Polyfills para WebViews antiguos: Instagram usa APIs JS que Chromium < ~110 no tiene
                    // y, si fallan, la página queda en blanco.
                    val docStart = try {
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            WebViewCompat.addDocumentStartJavaScript(this, POLYFILLS, setOf("https://www.instagram.com"))
                            true
                        } else false
                    } catch (e: Throwable) { false }
                    settings.userAgentString = vm.ua
                    setBackgroundColor(android.graphics.Color.WHITE)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) { progress = newProgress }
                        override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                            if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) console = m.message().take(120)
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, u: String?, favicon: android.graphics.Bitmap?) {
                            url = u ?: ""; error = null
                            if (!docStart) view.evaluateJavascript(POLYFILLS, null)
                        }
                        override fun onPageFinished(view: WebView, u: String?) {
                            url = u ?: ""
                            vm.checkSession()
                            // Detecta página en blanco y avisa con la causa probable
                            view.postDelayed({
                                view.evaluateJavascript(
                                    "(document.body&&document.body.innerText||'').trim().length"
                                ) { r ->
                                    val n = r?.trim('"')?.toIntOrNull() ?: 0
                                    if (n == 0 && (view.url ?: "").contains("instagram.com")) {
                                        error = "The page rendered blank. Your WebView ($wvVersion) is probably too old for " +
                                            "Instagram. Update 'Android System WebView' / Chrome in Google Play " +
                                            "(or use an emulator image that includes Google Play), then press Reload."
                                    }
                                }
                            }, 6000)
                            val c = vm.peekCredentials()
                            if (c != null && u?.contains("accounts/login") == true && injected < 2) {
                                injected++
                                view.evaluateJavascript(fillScript(c.first, c.second), null)
                            }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceError) {
                            if (request.isForMainFrame) error = "Could not load Instagram: ${err.description} (${err.errorCode}). Check internet and press Reload."
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, r: WebResourceResponse) {
                            if (request.isForMainFrame) error = "HTTP error ${r.statusCode} loading ${request.url.host}"
                        }
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, e: SslError) {
                            handler.cancel()
                            error = "SSL error. Check the date/time of the device or emulator."
                        }
                    }
                    web = this
                    loadUrl(LOGIN_URL)
                }
            },
            onRelease = { it.destroy() }
        )
    }
}

private val POLYFILLS = """
(function(){
  try {
    var d=function(o,n,f){ if(!o[n]) Object.defineProperty(o,n,{value:f,writable:true,configurable:true}); };
    var at=function(i){ i=Math.trunc(i)||0; if(i<0) i+=this.length; return this[i]; };
    d(Array.prototype,'at',at);
    d(String.prototype,'at',at);
    d(Array.prototype,'findLast',function(f,t){ for(var i=this.length-1;i>=0;i--) if(f.call(t,this[i],i,this)) return this[i]; });
    d(Array.prototype,'findLastIndex',function(f,t){ for(var i=this.length-1;i>=0;i--) if(f.call(t,this[i],i,this)) return i; return -1; });
    d(Array.prototype,'toReversed',function(){ return Array.prototype.slice.call(this).reverse(); });
    d(Array.prototype,'toSorted',function(c){ return Array.prototype.slice.call(this).sort(c); });
    d(Array.prototype,'with',function(i,v){ var a=Array.prototype.slice.call(this); i=Math.trunc(i)||0; if(i<0) i+=a.length; if(i<0||i>=a.length) throw new RangeError('Invalid index'); a[i]=v; return a; });
    d(Object,'hasOwn',function(o,k){ return Object.prototype.hasOwnProperty.call(o,k); });
    d(Promise,'withResolvers',function(){ var res,rej; var p=new Promise(function(a,b){res=a;rej=b;}); return {promise:p,resolve:res,reject:rej}; });
    if(!window.structuredClone) window.structuredClone=function(v){ return v===undefined?v:JSON.parse(JSON.stringify(v)); };
  } catch(e) { console.error('polyfill: '+e); }
})();
"""

private fun fillScript(u: String, p: String): String = """
(function(){
  var U=${JSONObject.quote(u)}, P=${JSONObject.quote(p)}, tries=0;
  function setVal(el,v){
    var s=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
    s.call(el,v); el.dispatchEvent(new Event('input',{bubbles:true}));
  }
  var iv=setInterval(function(){
    var a=document.querySelector('input[name="username"]');
    var b=document.querySelector('input[name="password"]');
    if(a&&b){
      clearInterval(iv); setVal(a,U); setVal(b,P);
      setTimeout(function(){
        var btn=document.querySelector('button[type="submit"]');
        if(btn) btn.click();
      },600);
    }
    if(++tries>40) clearInterval(iv);
  },500);
})();
"""

// ---------------------------------------------------------------- PANEL

@Composable
fun PanelScreen(vm: MainViewModel, s: UiState) {
    var confirm by remember { mutableStateOf<TaskKind?>(null) }
    var menu by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val busy = s.task?.phase == Phase.SCANNING || s.task?.phase == Phase.RUNNING

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFEFE6FB)).padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton({ (ctx as? android.app.Activity)?.finish() }) { Icon(Icons.Filled.ArrowBack, null, tint = Ink) }
            Text("Control Panel", Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
            Box {
                IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = Ink) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { menu = false; vm.loadProfile() })
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp)) {
            // Perfil
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InstaLogo(76.dp, corner = 0.5f)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("@${s.profile?.username ?: "…"}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text("${fmt(s.profile?.mediaCount ?: 0)} posts  •  Active", color = Muted, fontSize = 16.sp)
                }
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF1FA84F), modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(14.dp))

            // Aviso
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFDE9D3)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Warning, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    buildWarning(), color = Color(0xFFB71C1C), fontSize = 16.sp
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("BULK ACTIONS", color = Muted, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))

            ActionCard(
                icon = Icons.Outlined.DeleteOutline, iconTint = DangerRed,
                title = "Delete all posts\nfrom feed", subtitle = "Permanently removed",
                bg = Color.White, border = BorderStroke(2.dp, DangerRed), enabled = !busy
            ) { confirm = TaskKind.DELETE }
            Spacer(Modifier.height(16.dp))
            ActionCard(
                icon = Icons.Outlined.Archive, iconTint = Ink,
                title = "Archive all posts", subtitle = "Hidden without deleting",
                bg = LavenderCard, border = BorderStroke(1.dp, Color(0xFFD9C8F3)), enabled = !busy
            ) { confirm = TaskKind.ARCHIVE }
            Spacer(Modifier.height(16.dp))

            s.task?.let { TaskCard(it, vm) }
            s.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = DangerRed, fontSize = 14.sp)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { vm.signOut() }, modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp), border = BorderStroke(2.dp, DangerRed)
            ) {
                Text("SIGN OUT", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp)
            }
        }
        Signature()
    }

    confirm?.let { kind ->
        val del = kind == TaskKind.DELETE
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(if (del) "Delete ALL posts?" else "Archive ALL posts?") },
            text = {
                Text(
                    if (del) "Every post on your feed will be permanently deleted. This CANNOT be undone."
                    else "Every post on your feed will be archived (hidden). You can restore them later from Instagram."
                )
            },
            confirmButton = {
                TextButton({ confirm = null; vm.startTask(kind) }) {
                    Text(if (del) "DELETE ALL" else "ARCHIVE ALL", color = if (del) DangerRed else Purple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } }
        )
    }
}

private fun buildWarning() = androidx.compose.ui.text.buildAnnotatedString {
    append("These actions are ")
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold))
    append("IRREVERSIBLE")
    pop()
    append(" and affect ")
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold))
    append("ALL")
    pop()
    append(" posts on your feed.")
}

private fun fmt(n: Int) = String.format("%,d", n)

@Composable
fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, iconTint: Color,
    title: String, subtitle: String, bg: Color, border: BorderStroke, enabled: Boolean, onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
            .border(border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) iconTint else Color.Gray, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (enabled) Ink else Color.Gray, lineHeight = 26.sp)
            Text(subtitle, color = Muted, fontSize = 16.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = Muted, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun TaskCard(t: TaskState, vm: MainViewModel) {
    val verb = if (t.kind == TaskKind.DELETE) "Deleting" else "Archiving"
    val active = t.phase == Phase.SCANNING || t.phase == Phase.RUNNING
    val frac = if (t.total > 0) ((t.done + t.failed).toFloat() / t.total).coerceIn(0f, 1f) else 0f
    val title = when (t.phase) {
        Phase.SCANNING -> "Scanning posts… ${fmt(t.total)} found"
        Phase.RUNNING -> "$verb ${fmt(t.done)} of ${fmt(t.total)} posts"
        Phase.DONE -> if (t.total == 0) t.message else
            "Finished: ${fmt(t.done)} of ${fmt(t.total)} done" + if (t.failed > 0) ", ${t.failed} failed" else ""
        Phase.ERROR -> t.message
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = if (t.phase == Phase.ERROR) DangerRed else Ink)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(14.dp).clip(CircleShape).background(Color(0xFFE2E0EA))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (t.phase == Phase.DONE) 1f else frac).clip(CircleShape).background(IgGradient))
            }
            Spacer(Modifier.width(10.dp))
            Text("${(frac * 100).toInt().let { if (t.phase == Phase.DONE && t.total > 0) 100 else it }}%", fontSize = 16.sp, color = Ink)
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = { if (active) vm.cancelTask() else vm.dismissTask() },
                shape = RoundedCornerShape(12.dp), border = BorderStroke(2.dp, Purple),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) { Text(if (active) "CANCEL" else "OK", color = Purple, fontWeight = FontWeight.Bold) }
        }
    }
}
