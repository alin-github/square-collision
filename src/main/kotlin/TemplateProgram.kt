import org.openrndr.WindowMultisample
import org.openrndr.application
import org.openrndr.color.ColorRGBa
import org.openrndr.draw.Drawer
import org.openrndr.extra.olive.oliveProgram
import org.openrndr.math.Vector2
import org.openrndr.shape.Rectangle
import kotlin.math.abs
import kotlin.math.max

val windowSize = Vector2(800.0, 600.0)

var speedModifier = 0.3
var collisionProtectionMilliseconds = 1000
var restitutionCoefficient = 1.0

val squares = arrayOf(
    Square(
        color = ColorRGBa.BLUE,
        center = Vector2(50.0, 35.0),
        size = 50.0,
        rotation = 15.0,
        speed = Vector2(200.0, 100.0),
        rotationSpeed = -200.0,
        mass = 1.0
    ),
    Square(
        color = ColorRGBa.GREEN,
        center = Vector2(300.0, 350.0),
        size = 70.0,
        rotation = 0.0,
        speed = Vector2(-100.0, -200.0),
        rotationSpeed = 100.0,
        mass = 0.1
    )
)
lateinit var prevSquares: Array<Square>

fun main() = application {
    configure {
        width = windowSize.x.toInt()
        height = windowSize.y.toInt()
        multisample = WindowMultisample.SampleCount(4)
    }

    oliveProgram {
        extend {
            squares.forEach { it.move(program.deltaTime) }

            if (!collisionProtectionBetweenSquares.isProtected()) {
                revertToExactCollision(squares[0], squares[1], prevSquares[0], prevSquares[1], program.deltaTime)?.let { collisionBetweenSquares ->
                    squares[0] = prevSquares[0]
                    squares[1] = prevSquares[1]
                    collisionBetweenSquares.applyNewSpeedsAndRotations()
                    collisionProtectionBetweenSquares.startProtection()
                }
            }

            for (i in 0..1) {
                val square = squares[i]
                val prevSquare = prevSquares[i]

                for (wall in walls) {
                    if (!square.collisionProtectionWithWalls.getValue(wall).isProtected()) {
                        revertToExactCollision(square, wall, prevSquare, wall, program.deltaTime)?.let { collisionWithWall ->
                            squares[i] = prevSquare
                            collisionWithWall.applyNewSpeedsAndRotations(onlyToFirstParam = true)
                            square.collisionProtectionWithWalls.getValue(wall).startProtection()
                        }
                    }
                }
            }

            prevSquares = squares.map { it.copy() }.toTypedArray()

            drawer.clear(ColorRGBa.WHITE)
            squares.forEach { it.draw(drawer) }
        }
    }
}

val wallSize = max(windowSize.x, windowSize.y) + 10.0 // a number that exceeds both width and height of the window
val walls = listOf(
    Square( // left
        center = Vector2(-wallSize / 2, windowSize.y / 2),
        size = wallSize,
        mass = Double.MAX_VALUE
    ),
    Square( // right
        center = Vector2(windowSize.x + wallSize / 2, windowSize.y / 2),
        size = wallSize,
        mass = Double.MAX_VALUE
    ),
    Square( // top
        center = Vector2(windowSize.x / 2, -wallSize / 2),
        size = wallSize,
        mass = Double.MAX_VALUE
    ),
    Square( // bottom
        center = Vector2(windowSize.x / 2, windowSize.y + wallSize / 2),
        size = wallSize,
        mass = Double.MAX_VALUE
    )
)

class CollisionProtection {
    fun startProtection() {
        ignoreCollisionsUntil = System.currentTimeMillis() + collisionProtectionMilliseconds
    }

    fun isProtected() = ignoreCollisionsUntil?.let { protectedUntil -> System.currentTimeMillis() < protectedUntil } ?: false

    private var ignoreCollisionsUntil: Long? = null // number of milliseconds since 1970
}

/**
 * Find time of collision between time = 0.0 and time = [deltaSeconds].
 *
 * Input assumptions:
 *  - there is no collision at time = 0.0
 *  - there is a collision at time = [deltaSeconds]
 *  - the collision started at some point between the 2
 *
 * @param prevX square at time = 0.0
 * @param prevY square at time = 0.0
 * @param deltaSeconds max number of seconds to move
 * @return time in seconds at which the collision started
 */
fun findCollisionTime(prevX: Square, prevY: Square, deltaSeconds: Double): Double {
    val nIterations = 8
    var left = 0.0
    var right = deltaSeconds
    repeat(nIterations) {
        val middle = left / 2 + right / 2
        val prevXAtMiddleTime = prevX.copy().apply { move(middle) }
        val prevYAtMiddleTime = prevY.copy().apply { move(middle) }
        val collisionAtMiddleTime = (findCollision(prevXAtMiddleTime, prevYAtMiddleTime) != null)
        if (collisionAtMiddleTime) {
            right = middle
        }
        else {
            left = middle
        }
    }
    return left / 2 + right / 2
}

fun revertToExactCollision(x: Square, y: Square, prevX: Square, prevY: Square, deltaSeconds: Double): Collision? {
    if (findCollision(x, y) != null) {
        val collisionTime = findCollisionTime(prevX, prevY, deltaSeconds)
        prevX.move(collisionTime)
        prevY.move(collisionTime)
        return findCollision(prevX, prevY)!!
    }
    return null
}

fun findCollision(x: Square, y: Square): Collision? {
    var intrudedCorner = x.findCornerInside(y)
    if (intrudedCorner != null) {
        return Collision(intrudedCorner, a = x, b = y)
    }

    intrudedCorner = y.findCornerInside(x)
    if (intrudedCorner != null) {
        return Collision(intrudedCorner, a = y, b = x)
    }

    return null
}

/**
 * @param intrudedCorner a corner of [a] that is inside [b]. Considered the "point of contact".
 * @param a the square that has a corner inside b
 */
class Collision(val intrudedCorner: Vector2, val a: Square, val b: Square) {
    val contactNormalB = b.computeContactNormal(innerCollisionPoint = intrudedCorner)
    val contactNormalA = -contactNormalB

    private val pointOfContact = intrudedCorner

    /** Displacement from center of rotation to point of contact */
    private val rA = pointOfContact - a.center
    private val rB = pointOfContact - b.center

    /** http://www.cs.uu.nl/docs/vakken/mgp/2016-2017/Lecture%203%20-%20Collisions.pdf, slide 30 */
    private val jFactor = (-(1 + restitutionCoefficient) * (a.speed - b.speed).dot(contactNormalA)) /
            ((1 / a.mass + 1 / b.mass) +
                    ((rA.vector3().cross(contactNormalA.vector3()) / a.momentOfInertia()).cross(rA.vector3()).xy
                            + (rB.vector3().cross(contactNormalA.vector3()) / b.momentOfInertia()).cross(rB.vector3()).xy).dot(contactNormalA))

    /** Outgoing rotation speed (the z-value of the angular velocity vector) */
    val newRotationSpeedA = a.rotationSpeed + rA.cross(contactNormalA * jFactor) / a.momentOfInertia()
    val newRotationSpeedB = b.rotationSpeed - rB.cross(contactNormalA * jFactor) / b.momentOfInertia()

    /** Outgoing speed */
    val newSpeedA = a.speed + contactNormalA * jFactor / a.mass
    val newSpeedB = b.speed - contactNormalA * jFactor / b.mass

    fun applyNewSpeedsAndRotations(onlyToFirstParam: Boolean = false) {
        a.speed = newSpeedA
        a.rotationSpeed = newRotationSpeedA
        if (!onlyToFirstParam) {
            b.speed = newSpeedB
            b.rotationSpeed = newRotationSpeedB
        }
    }
}

val collisionProtectionBetweenSquares = CollisionProtection()

data class Square(
    var color: ColorRGBa = ColorRGBa.BLACK,
    var center: Vector2,
    val size: Double,
    var rotation: Double = 0.0,
    var speed: Vector2 = Vector2.ZERO,
    var rotationSpeed: Double = 0.0,
    val mass: Double
) {

    val collisionProtectionWithWalls by lazy { walls.associateWith { CollisionProtection() } }

    fun momentOfInertia() = mass * size * size / 6

    fun move(seconds: Double) {
        center += speed * speedModifier * seconds
        rotation += rotationSpeed * speedModifier * seconds
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

    private fun unrotatedCorners() = listOf(
        Vector2(center.x - size / 2, center.y - size / 2), // top left
        Vector2(center.x - size / 2, center.y + size / 2), // bottom left
        Vector2(center.x + size / 2, center.y - size / 2), // top right
        Vector2(center.x + size / 2, center.y + size / 2)  // bottom right
    )

    fun findCornerInside(other: Square): Vector2? {
        return unrotatedCorners()
            .map { it.rotate(degrees = rotation, origin = center) }
            .find(other::contains)
    }

    /**
     * Unit vector perpendicular to the edge closest to [innerCollisionPoint],
     * and pointing towards the interior of the square.
     */
    fun computeContactNormal(innerCollisionPoint: Vector2): Vector2 {
        val rotated = innerCollisionPoint.rotate(degrees = -rotation, origin = center)
        val distanceToEdges = mapOf(
            abs(rotated.x - (center.x - size / 2)) to "left",
            abs(rotated.x - (center.x + size / 2)) to "right",
            abs(rotated.y - (center.y - size / 2)) to "top",
            abs(rotated.y - (center.y + size / 2)) to "bottom"
        )

        val shortestDistance = distanceToEdges.keys.minOrNull()!!
        val closestEdge = distanceToEdges.getValue(shortestDistance)

        val unrotatedContactNormal = when (closestEdge) {
            "left" -> Vector2(1.0, 0.0)
            "right" -> Vector2(-1.0, 0.0)
            "top" -> Vector2(0.0, 1.0)
            else -> Vector2(0.0, -1.0)
        }

        return unrotatedContactNormal.rotate(degrees = rotation)
    }
}