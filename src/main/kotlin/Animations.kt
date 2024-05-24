import org.openrndr.extra.noise.simplex
import org.openrndr.extra.noise.uniform
import org.openrndr.math.Vector2
import org.openrndr.math.smoothstep
import kotlin.math.*
import kotlin.random.Random

fun step(x: Double): Double {
    return if (x > 0.0001) 0.0 else 1.0
}

fun function0(t: Double): List<Double> {
    return List (n) {
        val speed = 1.0
        val frequency =  0.05
        val t2 = (1.0 - t) * speed.coerceAtLeast(1.0)
        val width = frequency * 10.0
        val formant = sin( PI * smoothstep(t2 - width, t2, it * 1.0 / (n + (width * 2.0 * n))))
        1.0 - step(sin(it * n * frequency - t2) * formant)
    }
}

fun function1(t: Double): List<Double> {
    return List (n) {
        val speed = 1.0
        val frequency =  0.01
        val t2 = (1.0 - t) * speed.coerceAtLeast(1.0)
        val width = frequency * 10.0
        val formant = sin( PI * smoothstep(t2 - width, t2, it * 1.0 / (n + (width * 2.0 * n))))
        cos(it * n * frequency - t2) * formant
    }
}

fun function2(t: Double): List<Double> {
    return List (n) {
        var s = smoothstep(it * 1.0 / n, 1.0, t * 2.0)
        s
    }
}

fun function3(t: Double): List<Double> {
    return List (n) {
        val formant = sin( PI * t * 2.0)
        val n = simplex(123, it * 0.1 + t * 4.0) * 2.0
        n * formant
    }
}

fun function4(t: Double): List<Double> {
    return List (n) {
        val formant = sin( PI * t * 2.0)
        1.0 - step(sin(it + t * 20.0) * formant)
    }
}

fun function5(t: Double): List<Double> {
    return List (n) {
        val t0 = Double.uniform(0.0, 0.5, Random(it))
        val t1 = Double.uniform(t0, 1.0, Random(it))
        val formant = sin( PI * t * 2.0)
        smoothstep(t0, t1, t * 5.0)  * smoothstep(0.0, 1.0, formant)
    }
}

fun function7(t: Double): List<Double> {
    return List (n) {
        smoothstep(it * 1.0 / n, 1.0, 1.0 - t)
    }
}
