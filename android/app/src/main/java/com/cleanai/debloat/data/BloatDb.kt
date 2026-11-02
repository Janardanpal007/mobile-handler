package com.cleanai.debloat.data

/**
 * How safe a package is to take away.
 *  RECOMMENDED – ads, telemetry and junk; removing it never breaks the phone.
 *  OPTIONAL    – a real app; safe to remove if the user doesn't use it.
 *  CAUTION     – some feature may stop working; only "Disable" is offered.
 * Anything not in this list is never offered for removal.
 */
enum class Safety { RECOMMENDED, OPTIONAL, CAUTION }

data class BloatInfo(
    val pkg: String,
    val name: String,
    val why: String,
    val safety: Safety,
    val group: String,
)

object BloatDb {
    private const val ADS = "Ads & tracking"
    private const val OEM = "Manufacturer extras"
    private const val PARTNER = "Preinstalled partner apps"
    private const val GOOGLE = "Google extras"
    private const val AOSP = "Android extras"

    private fun rec(pkg: String, name: String, group: String, why: String) =
        BloatInfo(pkg, name, why, Safety.RECOMMENDED, group)

    private fun opt(pkg: String, name: String, group: String, why: String) =
        BloatInfo(pkg, name, why, Safety.OPTIONAL, group)

    private fun cau(pkg: String, name: String, group: String, why: String) =
        BloatInfo(pkg, name, why, Safety.CAUTION, group)

    val all: Map<String, BloatInfo> = listOf(
        // ---- Ads, telemetry & hidden installers ----
        rec("com.facebook.appmanager", "Facebook App Manager", ADS, "Hidden Facebook updater that runs in the background even if you never installed Facebook."),
        rec("com.facebook.services", "Facebook Services", ADS, "Background service that sends usage data to Facebook."),
        rec("com.facebook.system", "Facebook App Installer", ADS, "Lets Facebook silently install and update its apps."),
        rec("com.miui.analytics", "MIUI Analytics", ADS, "Xiaomi telemetry that collects and uploads how you use your phone."),
        rec("com.miui.msa.global", "MIUI System Ads", ADS, "The service that shows ads inside Xiaomi system apps."),
        rec("com.glance.internet", "Glance", ADS, "Lock-screen feed full of ads and sponsored stories."),
        rec("com.miui.android.fashiongallery", "Wallpaper Carousel", ADS, "Xiaomi lock-screen wallpaper feed (Glance) with ads."),
        rec("com.heytap.pictorial", "Lock Screen Magazine", ADS, "OPPO / Realme lock-screen feed with ads."),
        rec("com.opos.ads", "OPPO Ads", ADS, "Ad service used by OPPO / Realme / OnePlus system apps."),
        rec("com.samsung.android.dqagent", "Device Quality Agent", ADS, "Samsung telemetry that reports device usage."),
        rec("com.samsung.android.rubin.app", "Customization Service", ADS, "Samsung profiling service used for personalised ads and suggestions."),
        rec("com.netflix.partner.activation", "Netflix Partner Activation", ADS, "Pre-install tracker that reports Netflix sign-ups to the manufacturer."),
        rec("com.amazon.appmanager", "Amazon App Manager", ADS, "Background installer and updater for preinstalled Amazon apps."),
        rec("com.google.android.feedback", "Market Feedback Agent", ADS, "Sends crash feedback to Google. Nothing depends on it."),
        cau("com.miui.daemon", "MIUI Daemon", ADS, "Collects system statistics. Usually fine to disable, but a few MIUI features use it."),
        cau("com.xiaomi.joyose", "Joyose", ADS, "Xiaomi analytics plus game performance profiles. Disabling can change performance and heat."),

        // ---- Xiaomi / Redmi / POCO ----
        rec("com.miui.yellowpage", "Yellow Pages", OEM, "Business caller-ID directory that sends number lookups to Xiaomi."),
        rec("com.miui.bugreport", "Bug Report", OEM, "Feedback tool for MIUI beta testers."),
        rec("com.xiaomi.glgm", "Games", OEM, "Xiaomi game store with promoted games."),
        rec("com.miui.hybrid", "Quick Apps", OEM, "Runs instant 'quick apps' and pushes promoted content."),
        rec("com.miui.hybrid.accessory", "Quick Apps Accessory", OEM, "Companion service for Quick Apps."),
        rec("com.xiaomi.mipicks", "GetApps", OEM, "Xiaomi app store that pushes app ads. The Play Store keeps working."),
        opt("com.mi.globalbrowser", "Mi Browser", OEM, "Xiaomi browser with ads. Remove it if you use Chrome or another browser."),
        opt("com.miui.videoplayer", "Mi Video", OEM, "Video player with an online feed. Any other video player works."),
        opt("com.miui.player", "Mi Music", OEM, "Music player with online ads."),
        opt("com.xiaomi.midrop", "ShareMe", OEM, "File-sharing app. Android's Quick Share does the same."),
        opt("com.miui.weather2", "Weather", OEM, "Weather app. Its home-screen widget disappears if removed."),
        opt("com.mi.android.globalminusscreen", "App Vault", OEM, "Feed left of the home screen with shortcuts and ads."),
        opt("com.mipay.wallet.in", "Mi Pay", OEM, "Xiaomi payments app."),
        opt("com.xiaomi.discover", "System Apps Updater", OEM, "Updates Xiaomi apps between system updates."),
        cau("com.miui.cloudservice", "Xiaomi Cloud", OEM, "Needed for Find Device and Xiaomi cloud backup."),
        cau("com.facemoji.lite.xiaomi", "Facemoji Keyboard", OEM, "Keyboard with ads. Only disable it if you use another keyboard."),

        // ---- Samsung ----
        rec("com.samsung.android.app.spage", "Samsung Free", OEM, "News and promo feed left of the home screen."),
        rec("com.samsung.android.app.tips", "Tips", OEM, "Samsung tips notifications."),
        rec("com.samsung.android.app.watchmanagerstub", "Galaxy Wearable stub", OEM, "Placeholder that nags you to install the watch app."),
        opt("com.samsung.android.bixby.agent", "Bixby Voice", OEM, "Samsung's voice assistant."),
        opt("com.samsung.android.bixby.wakeup", "Bixby Wake-up", OEM, "Listens for 'Hi Bixby'."),
        opt("com.samsung.android.game.gamehome", "Game Launcher", OEM, "Samsung game hub."),
        opt("com.samsung.android.game.gametools", "Game Booster", OEM, "In-game toolbar. Only matters if you game."),
        opt("com.samsung.android.aremoji", "AR Emoji", OEM, "AR avatar stickers."),
        opt("com.samsung.android.ardrawing", "AR Doodle", OEM, "AR drawing in the camera."),
        opt("com.sec.android.app.sbrowser", "Samsung Internet", OEM, "Samsung's browser. Remove it if you use another one."),
        opt("com.samsung.android.tvplus", "Samsung TV Plus", OEM, "Free TV streaming with ads."),
        opt("com.samsung.android.spay", "Samsung Wallet", OEM, "Samsung payments. Keep it if you tap-to-pay with it."),
        opt("com.samsung.android.kidsinstaller", "Samsung Kids", OEM, "Kids mode."),
        cau("com.samsung.android.visionintelligence", "Bixby Vision", OEM, "Used by some camera features like scene recognition."),
        cau("com.samsung.android.app.routines", "Modes and Routines", OEM, "Settings > Modes may stop working."),
        cau("com.samsung.android.mobileservice", "Samsung Experience Service", OEM, "Needed for Samsung account and Find My Mobile."),
        cau("com.samsung.android.scloud", "Samsung Cloud", OEM, "Samsung backup and sync."),

        // ---- OPPO / Realme / OnePlus ----
        rec("com.nearme.gamecenter", "Game Center", OEM, "Game store with promoted games."),
        opt("com.heytap.browser", "HeyTap Browser", OEM, "Browser with news ads. Remove it if you use another browser."),
        opt("com.heytap.market", "App Market", OEM, "OPPO app store. The Play Store keeps working."),
        opt("com.coloros.gamespaceui", "Game Space", OEM, "Game hub and in-game toolbar."),
        opt("com.realmestore.app", "realme Store", OEM, "Shopping app for realme products."),
        opt("com.realmecomm.app", "realme Community", OEM, "realme fan forum."),
        opt("com.realme.link", "realme Link", OEM, "Controls realme smart-home gadgets. Remove it if you don't own any."),
        opt("com.coloros.compass2", "Compass", OEM, "Compass app."),
        opt("com.coloros.weather2", "Weather", OEM, "Weather app. Its home-screen widget disappears if removed."),
        opt("com.netflix.Speedtest", "FAST Speed Test", PARTNER, "Netflix's internet speed test."),
        cau("com.heytap.openid", "HeyTap OpenID", OEM, "Provides an advertising ID to apps. A few HeyTap apps rely on it, so only disable."),
        cau("com.heytap.themestore", "Theme Store", OEM, "Disabling can reset or break themes and wallpapers."),
        cau("com.heytap.cloud", "HeyTap Cloud", OEM, "OPPO backup and Find My Phone."),
        cau("com.heytap.mcs", "HeyTap Push", OEM, "Delivers notifications for some system apps."),

        // ---- vivo / iQOO ----
        opt("com.vivo.browser", "vivo Browser", OEM, "Browser with news ads."),
        opt("com.vivo.appstore", "V-Appstore", OEM, "vivo app store. The Play Store keeps working."),
        opt("com.vivo.easyshare", "EasyShare", OEM, "Phone-to-phone transfer app."),
        cau("com.bbk.theme", "i Theme", OEM, "Disabling can reset or break themes and wallpapers."),

        // ---- Partner apps preinstalled by the manufacturer ----
        opt("com.facebook.katana", "Facebook", PARTNER, "Social app. Remove it if you don't use it."),
        opt("com.facebook.orca", "Messenger", PARTNER, "Facebook chat app."),
        opt("com.instagram.android", "Instagram", PARTNER, "Social app."),
        opt("com.linkedin.android", "LinkedIn", PARTNER, "Professional network app."),
        opt("com.microsoft.skydrive", "OneDrive", PARTNER, "Microsoft cloud storage."),
        opt("com.microsoft.office.officehubrow", "Microsoft 365", PARTNER, "Office apps."),
        opt("com.netflix.mediaclient", "Netflix", PARTNER, "Streaming app."),
        opt("com.spotify.music", "Spotify", PARTNER, "Music streaming app."),
        opt("in.amazon.mShop.android.shopping", "Amazon India", PARTNER, "Shopping app."),
        opt("com.amazon.mShop.android.shopping", "Amazon Shopping", PARTNER, "Shopping app."),
        opt("com.amazon.avod.thirdpartyclient", "Prime Video", PARTNER, "Streaming app."),
        opt("com.eterno", "Dailyhunt", PARTNER, "News app with heavy ads."),
        opt("com.eterno.shortvideos", "Josh", PARTNER, "Short-video app."),
        opt("in.mohalla.sharechat", "ShareChat", PARTNER, "Social app."),
        opt("in.mohalla.video", "Moj", PARTNER, "Short-video app."),
        opt("com.yy.hiyo", "Hago", PARTNER, "Games and chat app."),
        opt("com.myairtelapp", "Airtel Thanks", PARTNER, "Carrier app. Keep it if you recharge with it."),
        opt("com.jio.myjio", "MyJio", PARTNER, "Carrier app. Keep it if you recharge with it."),
        opt("net.one97.paytm", "Paytm", PARTNER, "Payments app."),
        opt("com.phonepe.app", "PhonePe", PARTNER, "Payments app."),
        opt("com.snapchat.android", "Snapchat", PARTNER, "Social app."),
        opt("com.booking", "Booking.com", PARTNER, "Travel booking app."),
        opt("com.opera.browser", "Opera", PARTNER, "Browser."),
        opt("com.king.candycrushsaga", "Candy Crush Saga", PARTNER, "Preinstalled game."),
        opt("com.truecaller", "Truecaller", PARTNER, "Caller-ID app that uploads your contacts."),

        // ---- Google extras ----
        opt("com.google.android.apps.tachyon", "Google Meet", GOOGLE, "Video calling."),
        opt("com.google.android.apps.youtube.music", "YouTube Music", GOOGLE, "Music streaming."),
        opt("com.google.android.videos", "Google TV", GOOGLE, "Movie store."),
        opt("com.google.android.apps.books", "Play Books", GOOGLE, "E-books."),
        opt("com.google.android.apps.magazines", "Google News", GOOGLE, "News reader."),
        opt("com.google.android.apps.podcasts", "Google Podcasts", GOOGLE, "Discontinued podcast app."),
        opt("com.google.android.apps.subscriptions.red", "Google One", GOOGLE, "Google storage plans and backup UI."),
        opt("com.google.android.apps.docs", "Google Drive", GOOGLE, "Cloud storage."),
        opt("com.google.android.youtube", "YouTube", GOOGLE, "Video app."),
        opt("com.google.android.apps.photos", "Google Photos", GOOGLE, "Gallery and photo backup. Keep it if it's your gallery."),
        opt("com.google.android.gm", "Gmail", GOOGLE, "Email app. Your Google account keeps syncing without it."),
        opt("com.google.android.apps.maps", "Google Maps", GOOGLE, "Maps and navigation."),
        opt("com.google.ar.lens", "Google Lens", GOOGLE, "Visual search."),
        opt("com.google.android.apps.nbu.paisa.user", "Google Pay", GOOGLE, "Payments app."),
        opt("com.google.android.projection.gearhead", "Android Auto", GOOGLE, "Car display support."),
        opt("com.google.android.apps.wellbeing", "Digital Wellbeing", GOOGLE, "Screen-time stats and Bedtime mode."),
        opt("com.google.android.printservice.recommendation", "Print Service Recommendation", GOOGLE, "Suggests printer plugins."),
        opt("com.google.android.marvin.talkback", "TalkBack", GOOGLE, "Screen reader. Keep it if anyone using this phone needs accessibility features."),
        cau("com.android.chrome", "Chrome", GOOGLE, "Keep at least one browser. On Android 9 and older it can also power WebView."),
        cau("com.google.android.googlequicksearchbox", "Google app", GOOGLE, "Powers Assistant, the search bar and the Discover feed."),
        cau("com.google.android.apps.turbo", "Device Health Services", GOOGLE, "Adaptive battery and battery-time estimates."),

        // ---- AOSP extras ----
        rec("com.android.egg", "Android Easter Egg", AOSP, "The hidden mini-game in About phone."),
        opt("com.android.dreams.basic", "Basic Screensaver", AOSP, "Colour screensaver while charging."),
        opt("com.android.wallpaper.livepicker", "Live Wallpaper Picker", AOSP, "Only needed for live wallpapers."),
        opt("com.android.bips", "Default Print Service", AOSP, "Wi-Fi printing."),
        cau("com.android.stk", "SIM Toolkit", AOSP, "Carrier SIM menus. Some carriers use it for services."),
    ).associateBy { it.pkg }

    val groupOrder = listOf(ADS, OEM, PARTNER, GOOGLE, AOSP)
}
