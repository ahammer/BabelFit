import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.adamhammer.babelfit.samples.jsoneditor.compose.ComposeEditorController
import ca.adamhammer.babelfit.samples.jsoneditor.compose.JsonEditorApp
import java.io.File

fun main() = application {
    val scope = rememberCoroutineScope()
    val controller = remember { ComposeEditorController(scope) }
    var showCloseDialog by remember { mutableStateOf(false) }

    val fileName = controller.filePath?.let { File(it).name } ?: "untitled.json"
    val dirty = if (controller.isDirty) "*" else ""
    val windowTitle = "$fileName$dirty \u2014 BabelFit JSON Editor"

    Window(
        onCloseRequest = {
            if (controller.isDirty) showCloseDialog = true else exitApplication()
        },
        title = windowTitle,
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        MenuBar {
            Menu("File") {
                Item("New", onClick = { controller.newFile() }, shortcut = KeyShortcut(Key.N, ctrl = true))
                Item("Open\u2026", onClick = { controller.openFile() }, shortcut = KeyShortcut(Key.O, ctrl = true))
                Item("Save", onClick = { controller.save() }, shortcut = KeyShortcut(Key.S, ctrl = true))
                Item(
                    "Save As\u2026",
                    onClick = { controller.saveAs() },
                    shortcut = KeyShortcut(Key.S, ctrl = true, shift = true)
                )
                Separator()
                Item("Exit", onClick = {
                    if (controller.isDirty) showCloseDialog = true else exitApplication()
                })
            }
            Menu("Edit") {
                Item("Undo", onClick = { controller.undo() }, shortcut = KeyShortcut(Key.Z, ctrl = true))
                Item("Redo", onClick = { controller.redo() }, shortcut = KeyShortcut(Key.Y, ctrl = true))
            }
            Menu("Trace") {
                Item("Export Trace\u2026", onClick = { controller.exportTrace() })
            }
        }
        JsonEditorApp(controller)

        if (showCloseDialog) {
            AlertDialog(
                onDismissRequest = { showCloseDialog = false },
                title = { Text("Unsaved Changes") },
                text = { Text("Do you want to save changes to $fileName?") },
                confirmButton = {
                    TextButton(onClick = {
                        showCloseDialog = false
                        controller.save()
                        exitApplication()
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCloseDialog = false
                        exitApplication()
                    }) { Text("Don't Save") }
                }
            )
        }
    }
}
