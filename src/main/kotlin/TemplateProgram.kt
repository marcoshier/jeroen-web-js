import kotlinx.browser.window
import org.openrndr.applicationAsync
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.fx.Post
import org.openrndr.extra.fx.distort.Perturb
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.math.*
import org.openrndr.shape.Rectangle
import org.openrndr.webgl.ColorBufferWebGLCors
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.Image
import org.w3c.dom.get
import kotlin.math.pow


suspend fun main() {
    applicationAsync {

        val windowRef = window
        //val statusElement = windowRef.document.getElementById("status")!!


        program {
            val canvas = windowRef.document.getElementsByTagName("canvas")[0] as HTMLCanvasElement
            val ctx = canvas.getContext("webgl2") as WebGL2RenderingContext
            console.log("context $ctx")

            val imgs = (0..14).map { ColorBufferWebGLCors.fromUrlSuspend(ctx, "images/$it.jpg")}

            val accelerometer = object {
                var beta = 0.0
                    set(value) {
                        field = field * 0.97 + value * 0.03
                    }
                var gamma = 0.0
                    set(value) {
                        field = field * 0.97 + value * 0.03
                    }
            }

            val rectangles = (0..14).map {
                drawer.bounds.scaledBy(0.9.pow(it))
            }

            var currentMode = 1

            var movingRectangles = rectangles
            var cidx = -1
            var mousePos = Vector2.ZERO
            mouse.buttonDown.listen {
                //statusElement.innerHTML = "DOWN"
                mousePos = it.position

                val targetRect = movingRectangles.withIndex().filter { mousePos in it.value }.maxByOrNull { it.index }
                cidx = targetRect?.index ?: -1
            }


            //val font = loadFont("data/fonts/default.otf", 25.0)

            val p = Perturb().apply {
                this.gain = 0.01
                this.radius = 1.4
                this.bicubicFiltering = true
            }
            extend(Post()) {
                post { input, output ->
                    p.apply(input, output)
                }
            }
            extend {

                p.phase = seconds * 0.03

                val accelElement = windowRef.document.getElementById("accel")!!

                accelerometer.beta = accelElement.getAttribute("data-beta")!!.toDouble()
                accelerometer.gamma = accelElement.getAttribute("data-gamma")!!.toDouble()
                currentMode = accelElement.getAttribute("data-mode")!!.toInt()

                drawer.clear(ColorRGBa.WHITE)
                drawer.fill = ColorRGBa.WHITE


                val xOff = map(-40.0, 40.0, 0.0, 1.0, accelerometer.gamma, true)
                val yOff = map(120.0, 60.0, 0.0, 1.0, accelerometer.beta, true)

                drawer.stroke = ColorRGBa.BLACK
                drawer.fill = ColorRGBa.WHITE

                val uv = Vector2(xOff, yOff)

                movingRectangles = rectangles.map {
                    val invuv = Vector2.ONE - uv
                    rectangleFromUV(
                        invuv.copy(x = 1.0 - invuv.x),
                        drawer.bounds,
                        it.width,
                        it.height
                    )
                }


                drawer.fill = ColorRGBa.WHITE
                if (currentMode == 1) {
                    for ((i, mrect) in movingRectangles.withIndex()) {
                        drawer.fill = if(cidx == i) ColorRGBa.RED else ColorRGBa.WHITE
                        drawer.rectangle(mrect)
                        if (i == cidx) drawer.imageFit(imgs[i], mrect)
                        drawer.shadeStyle = null
                    }
                }
                if (currentMode == 2)  {
                    for ((i, mrect) in movingRectangles.withIndex()) {
                        drawer.fill = if(cidx == i) ColorRGBa.RED else ColorRGBa.WHITE
                        drawer.imageFit(imgs[i], mrect)
                        drawer.shadeStyle = null
                    }
                }




            }
        }
    }

}

fun Rectangle.uv(pos: Vector2): Vector2 {
    return pos.map(corner, corner + dimensions, Vector2.ZERO, Vector2.ONE, true)
}

fun rectangleFromUV(uv: Vector2, outer: Rectangle, width: Double, height: Double) : Rectangle {
    return Rectangle(outer.position(uv) - Vector2(width, height) * uv, width, height)
}
