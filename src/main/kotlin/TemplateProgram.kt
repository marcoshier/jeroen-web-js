import kotlinx.browser.window
import org.openrndr.animatable.Animatable
import org.openrndr.animatable.easing.Easing
import org.openrndr.applicationAsync
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.*
import org.openrndr.extra.fx.Post
import org.openrndr.extra.fx.distort.Perturb
import org.openrndr.extra.imageFit.imageFit
import org.openrndr.math.*
import org.openrndr.shape.Rectangle
import org.openrndr.webgl.ColorBufferWebGLCors
import org.w3c.dom.*
import kotlin.math.pow

val n = 15

suspend fun main() {
    applicationAsync {

        //val statusElement = windowRef.document.getElementById("status")!!

        val windowRef = window
        val canvas = windowRef.document.getElementsByTagName("canvas")[0] as HTMLCanvasElement
        val ctx = canvas.getContext("webgl2") as WebGL2RenderingContext

        program {
            val imgs = (0 until n).map { ColorBufferWebGLCors.fromUrlSuspend(ctx, "images/$it.jpg")}

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

            val rectangles = (0 until n).map {
                drawer.bounds.scaledBy(0.9.pow(it))
            }

            var currentMode: Int
            var movingRectangles = rectangles
            var cidx = -1
            var mousePos: Vector2
            mouse.buttonDown.listen {
                //statusElement.innerHTML = "DOWN"
                mousePos = it.position

                val targetRect = movingRectangles.withIndex().filter { mousePos in it.value }.maxByOrNull { it.index }
                cidx = targetRect?.index ?: -1
            }

            var state = object: Animatable() {
                var currentAnim = 0
                    set(value) {
                        if (field != value) {
                            field = value
                            playhead = 0.0
                            pulse()
                        }
                    }
                var playhead = 0.0

                fun pulse() {
                    cancel()
                    ::playhead.animate(1.0, 2500, Easing.CubicInOut)
                }
            }

            var functions = listOf(
                ::function0,
                ::function1,
                ::function2,
                ::function3,
                ::function4,
                ::function5,
                ::function7
            )

            windowRef.document.getElementById("bottom-buttons")!!.children.asList().forEachIndexed { i, it ->
                (it as HTMLButtonElement).addEventListener("click", {
                    it.stopPropagation()
                    state.currentAnim = i
                })
            }

            state.pulse()

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
                state.updateAnimation()

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
                        drawer.fill = ColorRGBa.WHITE.mix(ColorRGBa.RED, functions[state.currentAnim](state.playhead)[i])
                        drawer.rectangle(mrect)
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
