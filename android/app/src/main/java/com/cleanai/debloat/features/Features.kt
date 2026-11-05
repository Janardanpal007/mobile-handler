package com.cleanai.debloat.features

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.cleanai.debloat.R
import com.cleanai.debloat.agent.Act
import com.cleanai.debloat.ui.AboutScreen
import com.cleanai.debloat.ui.BUILT_BY
import com.cleanai.debloat.ui.Butter
import com.cleanai.debloat.ui.CalculatorScreen
import com.cleanai.debloat.ui.FilesScreen
import com.cleanai.debloat.ui.NotesScreen
import com.cleanai.debloat.ui.PdfScreen
import com.cleanai.debloat.ui.PhotoScreen
import com.cleanai.debloat.ui.SheetScreen
import com.cleanai.debloat.ui.WordScreen
import com.cleanai.debloat.ui.CoralTint as Coral
import com.cleanai.debloat.ui.Mint
import com.cleanai.debloat.ui.Sky

/**
 * One tile on the home screen.
 *
 * To add a feature:
 *  1. Add an icon to res/drawable (a 24dp vector).
 *  2. Add a [Feature] to [Features.all] below.
 *     - [Opens.Assistant] runs an assistant action in the chat (good for guided, step-by-step tools).
 *     - [Opens.Screen] shows its own full screen; write it as a @Composable taking onBack (and openTool if it links to other tools).
 * The home grid, navigation and back button pick it up automatically.
 */
data class Feature(
    val id: String,
    val title: String,
    val subtitle: String,
    @DrawableRes val icon: Int,
    val tint: Color,
    val opens: Opens,
    val group: ToolGroup,
)

/** How tools are grouped on Home and in the Tools tab. */
enum class ToolGroup(val title: String) {
    DOCUMENTS("Documents"), EVERYDAY("Everyday"), MEDIA("Photos & scans"), DEVICE("Phone"), APP("Help & info"),
}

sealed interface Opens {
    /** Opens the assistant and, if [act] is set, runs it right away. */
    data class Assistant(val act: Act?) : Opens

    /** A feature with its own screen. [openTool] opens another feature by its id (e.g. the PDF studio opening "docscan"). */
    class Screen(val content: @Composable (onBack: () -> Unit, openTool: (String) -> Unit) -> Unit) : Opens
}

object Features {
    val all: List<Feature> = listOf(
        Feature("adblock", "Ad Blocker", "Block ads & trackers", R.drawable.ic_tool_shield, Sky, Opens.Assistant(Act.Trackers), ToolGroup.DEVICE),
        Feature("calculator", "Calculator", "Scientific, finance, currency", R.drawable.ic_tool_calc, Butter, Opens.Screen { onBack, _ -> CalculatorScreen(onBack) }, ToolGroup.EVERYDAY),
        Feature("qr", "Scan QR", "Links, Wi-Fi, text", R.drawable.ic_tool_qr, Coral, Opens.Assistant(Act.ScanQr), ToolGroup.MEDIA),
        Feature("docscan", "Scan Document", "Save pages as PDF", R.drawable.ic_tool_doc, Mint, Opens.Assistant(Act.ScanDoc), ToolGroup.DOCUMENTS),
        Feature("pdf", "Photos → PDF", "Combine images", R.drawable.ic_tool_pdf, Sky, Opens.Assistant(Act.MakePdf), ToolGroup.DOCUMENTS),
        Feature("pdfreader", "PDF Studio", "Read, edit, sign PDFs", R.drawable.ic_tool_reader, Coral, Opens.Screen { onBack, openTool -> PdfScreen(onBack, openTool) }, ToolGroup.DOCUMENTS),
        Feature("word", "Word", "Create & edit .docx", R.drawable.ic_tool_word, Sky, Opens.Screen { onBack, _ -> WordScreen(onBack) }, ToolGroup.DOCUMENTS),
        Feature("excel", "Excel", "Sheets, CSV & formulas", R.drawable.ic_tool_sheet, Mint, Opens.Screen { onBack, _ -> SheetScreen(onBack) }, ToolGroup.DOCUMENTS),
        Feature("notes", "Notes", "Lists, tags, lock", R.drawable.ic_tool_notes, Butter, Opens.Screen { onBack, _ -> NotesScreen(onBack) }, ToolGroup.EVERYDAY),
        Feature("photo", "Photo Enhancer", "HD, restore, colorize", R.drawable.ic_tool_photo, Coral, Opens.Screen { onBack, _ -> PhotoScreen(onBack) }, ToolGroup.MEDIA),
        Feature("assistant", "Assistant", "Help with app tools", R.drawable.ic_tool_assistant, Coral, Opens.Assistant(null), ToolGroup.APP),
        Feature("cleaner", "Phone Cleaner", "Remove bloatware", R.drawable.ic_tool_clean, Mint, Opens.Assistant(Act.Clean), ToolGroup.DEVICE),
        Feature("files", "My Files", "Scans & PDFs", R.drawable.ic_folder, Mint, Opens.Screen { onBack, _ -> FilesScreen(onBack) }, ToolGroup.DOCUMENTS),
        Feature("weather", "Weather", "Today & 3 days", R.drawable.ic_tool_weather, Butter, Opens.Assistant(Act.Weather), ToolGroup.EVERYDAY),
        Feature("restore", "Restore Apps", "Undo removals", R.drawable.ic_tool_restore, Coral, Opens.Assistant(Act.Restore), ToolGroup.DEVICE),
        Feature("about", "About & Privacy", BUILT_BY, R.drawable.ic_tool_info, Mint, Opens.Screen { onBack, _ -> AboutScreen(onBack) }, ToolGroup.APP),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}
