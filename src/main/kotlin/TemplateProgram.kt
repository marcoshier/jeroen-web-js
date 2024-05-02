import kotlinx.browser.document
import kotlinx.browser.window
import org.openrndr.applicationAsync
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.shape.Rectangle
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.cos

suspend fun main() {

    val canvas = document.getElementById("openrndr-canvas") as HTMLCanvasElement

    fun patchCanvas() {
        val ctx = canvas.toDataURL()
        console.log(ctx)
    }

    applicationAsync {



        window.addEventListener("resize", {
            patchCanvas()
        }, false)

        program {

            val rectangles = (0..20).map {
                Rectangle(0.0, 0.0, width * 1.0, height * 1.0).offsetEdges(-20.0 * it)
            }

            extend {
                val a = rgb("#ff0000")
                drawer.clear(a)
                drawer.fill = ColorRGBa.WHITE
                drawer.rectangles(rectangles)
            }
        }
    }

}