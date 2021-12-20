import org.openrndr.Program
import org.openrndr.WindowMultisample
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle

val initialWindowSize = Vector2(800.0, 600.0)
var speedModifier = 0.1
val squares = listOf(
    Square(
        originalColor = ColorRGBa.BLUE,
        center = Vector2(50.0, 35.0),
        size = 50.0,
        rotation = 0.0,
        speed = Vector2(200.0, 100.0),
        rotationSpeed = -1.0
    ),
    Square(
        originalColor = ColorRGBa.GREEN,
        center = Vector2(500.0, 500.0),
        size = 70.0,
        rotation = 20.0,
        speed = Vector2(-100.0, -200.0),
        rotationSpeed = 2.0
    )
)

fun main() = application {
    configure {
        width = initialWindowSize.x.toInt()
        height = initialWindowSize.y.toInt()
        multisample = WindowMultisample.SampleCount(4)
        windowResizable = true
    }

    oliveProgram {
        extend {
            drawer.clear(ColorRGBa.WHITE)
            drawGrid(this)

            squares.forEach { it.move(program) }

            val touchPoint = squares[0].findCommonPoint(squares[1])
            if (touchPoint != null) {
                squares.forEach { it.emphasize() }
            }
            else {
                squares.forEach { it.deEmphasize() }
            }
            squares.forEach { it.draw(drawer) }
            if (touchPoint != null) {
                drawer.strokeWeight = 6.0
                drawer.stroke = ColorRGBa.BLACK
                drawer.circle(position = touchPoint, radius = 6.0)
                speedModifier = 0.01
            }
        }
    }
}

data class Square(
    val originalColor: ColorRGBa,
    var color: ColorRGBa = originalColor,
    var center: Vector2,
    val size: Double,
    var rotation: Double,
    var speed: Vector2,
    var rotationSpeed: Double
) {
    fun move(program: Program) {
        center = (center + speed * speedModifier * program.deltaTime).positiveMod(program.window.size)
        rotation += rotationSpeed * speedModifier * program.deltaTime
    }

    fun draw(drawer: Drawer) {
        drawer.pushTransforms()
        drawer.fill = color
        drawer.stroke = null
        drawer.translate(center)
        drawer.rotate(rotation)
        drawer.rectangle(x = -size / 2, y = -size / 2, width = size, height = size)
        drawer.popTransforms()
    }

    private fun contains(point: Vector2): Boolean {
        val rotated = point.rotate(degrees = -rotation, origin = center)
        return Rectangle(center - size / 2, size, size).containsInclusive(rotated)
    }

    private fun Rectangle.containsInclusive(point: Vector2): Boolean {
        return (point.x >= corner.x &&
                point.x <= corner.x + width &&
                point.y >= corner.y &&
                point.y <= corner.y + height)
    }

    private fun corners() = sequenceOf(
        Vector2(center.x - size / 2, center.y - size / 2),
        Vector2(center.x - size / 2, center.y + size / 2),
        Vector2(center.x + size / 2, center.y - size / 2),
        Vector2(center.x + size / 2, center.y + size / 2)
    )

    fun findCommonPoint(other: Square): Vector2? {
        return other.corners().find(this::contains)
            ?: this.corners().find(other::contains)
    }

    fun deEmphasize() {
        color = originalColor
    }

    fun emphasize() {
        color = ColorRGBa.RED
    }
}

fun Vector2.positiveMod(divisor: Vector2) = Vector2(x.mod(divisor.x), y.mod(divisor.y))

fun drawGrid(program: Program) {
    val gridDistance = 50

    program.apply {
        drawer.stroke = ColorRGBa.BLACK
        drawer.strokeWeight = 0.1
        for (step in 0..height step gridDistance) {
            drawer.lineSegment(0.0, step.toDouble(), width.toDouble(), step.toDouble())
        }
        for (step in 0..width step gridDistance) {
            drawer.lineSegment(step.toDouble(), 0.0, step.toDouble(), height.toDouble())
        }
    }
}