import ca.adamhammer.babelfit.samples.dnd.compose.BabelFitDndAppV2
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "BabelFit D&D") {
        BabelFitDndAppV2()
    }
}
