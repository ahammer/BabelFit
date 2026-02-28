import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.adamhammer.babelfit.samples.jsoneditor.compose.ComposeEditorController
import ca.adamhammer.babelfit.samples.jsoneditor.compose.JsonEditorApp

fun main() = application {
    val scope = rememberCoroutineScope()
    val controller = ComposeEditorController(scope)

    Window(
        onCloseRequest = ::exitApplication,
        title = "BabelFit JSON Editor",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MenuBar {
            Menu("File") {
                Item("New", onClick = { controller.newFile() }, shortcut = KeyShortcut(Key.N, ctrl = true))
                Item("Open…", onClick = { controller.openFile() }, shortcut = KeyShortcut(Key.O, ctrl = true))
                Item(
                    "Save As…",
                    onClick = { controller.saveAs() },
                    shortcut = KeyShortcut(Key.S, ctrl = true, shift = true)
                )
                Separator()
                Item("Exit", onClick = ::exitApplication)
            }
        }
        JsonEditorApp(controller)
    }
}
