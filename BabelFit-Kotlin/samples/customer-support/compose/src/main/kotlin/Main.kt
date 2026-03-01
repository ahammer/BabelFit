import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import ca.adamhammer.babelfit.samples.customersupport.compose.CustomerSupportApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "BabelFit Customer Support",
        state = rememberWindowState(width = 1100.dp, height = 750.dp)
    ) {
        CustomerSupportApp()
    }
}
