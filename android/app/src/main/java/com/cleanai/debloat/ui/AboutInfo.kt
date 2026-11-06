package com.cleanai.debloat.ui

/** Facts shown on the About screen. Keep [Changelog] in step with the GitHub release notes. */

const val DEVELOPER = "Janardan Pal"
const val BUILT_BY = "Built by $DEVELOPER"
const val TAGLINE = "Your phone. Simplified."

class AboutLink(val name: String, val detail: String, val url: String)

val ProjectLinks = listOf(
    AboutLink("Website", "janardanpal007.github.io/mobile-handler", "https://janardanpal007.github.io/mobile-handler/"),
    AboutLink("Source code", "github.com/Janardanpal007/mobile-handler", "https://github.com/Janardanpal007/mobile-handler"),
)

val DeveloperLinks = listOf(
    AboutLink("GitHub", "github.com/Janardanpal007", "https://github.com/Janardanpal007"),
    AboutLink("LinkedIn", "linkedin.com/in/janardan-pal", "https://www.linkedin.com/in/janardan-pal"),
    AboutLink("Discord", "Add me on Discord", "https://discord.dog/704385778809307226"),
)

class Release(val version: String, val date: String, val notes: List<String>)

val Changelog = listOf(
    Release(
        "2.0.1", "6 Nov 2026",
        listOf(
            "PDF Editor: All tools is now grouped into Edit & sign, Pages, Protect & share and Create a PDF, with a line on what each tool does",
            "PDF Editor: delete a file from Recent files (you're asked first)",
        ),
    ),
    Release(
        "2.0", "6 Nov 2026",
        listOf(
            "New look: Home with Sparky, quick actions you can change, and your PDF, Word and Excel editors",
            "New bottom bar: Home, Tools, Files and Ask; Settings is at the top of Home",
            "Word and Excel: saving keeps tables, pictures, links, charts and formatting from the file you opened",
            "Word and Excel: reopen recent files in one tap",
            "PDF Editor: new tool grid, recent PDFs with previews, and Find text",
            "New About page with what's new, permissions and open-source licences",
            "Private mode removed: weather, currency and web answers simply go online when you ask",
        ),
    ),
    Release(
        "1.9", "4 Nov 2026",
        listOf(
            "PDF Editor: keep up to 6 signatures and sign with a tap",
            "PDF Editor: stamps such as APPROVED, PAID or DRAFT, with today's date if you like",
            "PDF tools: watermark every page",
            "PDF tools: lock a PDF with a password (AES-256), or remove a password you know",
        ),
    ),
    Release(
        "1.8.1", "4 Nov 2026",
        listOf(
            "Calculator: edit a sum, C then AC, and history of your last 100 calculations",
            "Notes: pick any colour from a colour chart",
            "PDF Editor: signatures land on the page you are looking at",
            "Calculator turned sideways no longer cuts anything off",
        ),
    ),
    Release(
        "1.8", "4 Nov 2026",
        listOf(
            "PDF Editor: change text, draw, highlight, add pictures and signatures, rearrange pages, undo and redo",
            "Notes: checklists, colours, pins, tags, trash and fingerprint lock",
            "Floating calculator over other apps",
            "About: Connect with me links",
        ),
    ),
)

/** Every permission in the app's manifest and why it is there. */
val PermissionReasons = listOf(
    "Internet" to "For weather, currency rates and web answers when you ask for them, and a once-a-day check on GitHub for a new version.",
    "Network state" to "To show which DNS your phone is using right now (\"What DNS am I using?\").",
    "Approximate location" to "Only when you ask for the weather without naming a city. Asked the first time you need it.",
    "Notifications" to "To tell you when a new version is out. You can turn this off in Settings.",
    "Display over other apps" to "Only for the floating calculator, and only after you allow it.",
    "Fingerprint or screen lock" to "To open notes you have locked.",
    "Foreground service" to "Keeps the floating calculator running while it is on screen.",
)

class License(val name: String, val license: String, val url: String)

val OpenSourceLicenses = listOf(
    License("Jetpack Compose and AndroidX", "Apache-2.0", "https://developer.android.com/jetpack/androidx"),
    License("Kotlin coroutines", "Apache-2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
    License("PdfBox-Android", "Apache-2.0", "https://github.com/TomRoush/PdfBox-Android"),
    License("LiteRT (TensorFlow Lite)", "Apache-2.0", "https://github.com/google-ai-edge/LiteRT"),
    License("Real-ESRGAN (photo model)", "BSD-3-Clause", "https://github.com/xinntao/Real-ESRGAN"),
    License("DDColor (colour model)", "Apache-2.0", "https://github.com/piddnad/DDColor"),
    License("Shizuku API", "MIT", "https://github.com/RikkaApps/Shizuku-API"),
    License("ML Kit scanners", "Google Play services terms (free, not open source)", "https://developers.google.com/ml-kit/terms"),
)
