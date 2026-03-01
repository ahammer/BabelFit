import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.adamhammer.babelfit.samples.traceviewer.compose.TraceViewerApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BabelFit Trace Viewer",
        state = rememberWindowState(width = 1300.dp, height = 850.dp)
    ) {
        TraceViewerApp()
    }
}
