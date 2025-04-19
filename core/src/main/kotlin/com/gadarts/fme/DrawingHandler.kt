package com.gadarts.fme

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Disposable
import com.gadarts.fme.SceneRenderer.Block
import com.gadarts.fme.SceneRenderer.ScenePlane
import kotlin.math.abs

class DrawingHandler(
    private val blocks: MutableList<Block>,
    private val camera: PerspectiveCamera
) : Disposable {
    private val cubeModel = createCubeModel()
    private fun createCubeModel(): Model {
        val modelBuilder = ModelBuilder()

        return modelBuilder.createBox(
            1f, 1f, 1f, // width, height, depth
            Material(ColorAttribute.createDiffuse(Color.GRAY)),
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        ).apply {
            nodes.first().translation.set(0.5f, 0.5f, 0.5f)
            calculateTransforms()
        }
    }

    fun applyCreate(screenX: Int, screenY: Int) {
        val relativeX = screenX.toFloat()
        val relativeY = screenY.toFloat()
        camera.getPickRay(relativeX, relativeY)
        val hit = getFirstPlaneIntersection(screenX, screenY, scenePlanes)
        if (hit != null) {
            val (_, point) = hit
            point.set(point.x.toInt().toFloat(), point.y.toInt().toFloat(), point.z.toInt().toFloat())
            val modelInstance = ModelInstance(cubeModel)
            modelInstance.transform.setTranslation(point)
            drawingBlock = modelInstance
            initialDrawingPoint.set(point)
            auxMatrix.set(drawingBlock!!.transform)
        }

    }

    private val scenePlanes = listOf(
        ScenePlane(Plane(Vector3.Y, 0f), "floor"),
        ScenePlane(Plane(Vector3.Y.cpy().scl(-1f), WORLD_SIZE), "ceiling"),
        ScenePlane(Plane(Vector3.X, 0F), "leftWall"),
        ScenePlane(Plane(Vector3.X.cpy().scl(-1f), WORLD_SIZE), "rightWall"),
        ScenePlane(Plane(Vector3.Z, 0F), "backWall"),
        ScenePlane(Plane(Vector3.Z.cpy().scl(-1f), WORLD_SIZE), "frontWall")
    )


    fun touchUp(): Boolean {
        if (drawingBlock != null) {
            val bounds = BoundingBox()
            drawingBlock!!.calculateBoundingBox(bounds)
            bounds.mul(drawingBlock!!.transform)
            val block = Block(drawingBlock!!, bounds)
            blocks.add(block)
            drawingBlock = null
            return true
        }
        return false
    }

    fun touchDragged(screenX: Int, screenY: Int): Boolean {
        if (drawingBlock != null) {
            val relativeX = screenX.toFloat()
            val relativeY = screenY.toFloat()

            camera.getPickRay(relativeX, relativeY)
            val hit = getFirstPlaneIntersection(screenX, screenY, scenePlanes)
            if (hit != null) {
                val (_, point) = hit
                point.set(point.x.toInt().toFloat(), point.y.toInt().toFloat(), point.z.toInt().toFloat())
                drawingBlock!!.transform.set(auxMatrix)
                drawingBlock!!.transform.scl(
                    if (abs(point.x - initialDrawingPoint.x) > 1F) point.x - initialDrawingPoint.x else 1F,
                    1F,
                    if (abs(point.z - initialDrawingPoint.z) > 1F) point.z - initialDrawingPoint.z else 1F
                )
                return true
            }
        }
        return false
    }

    private fun getFirstPlaneIntersection(
        screenX: Int,
        screenY: Int,
        scenePlanes: List<ScenePlane>
    ): Pair<ScenePlane, Vector3>? {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        var closestIntersection: Vector3? = null
        var closestPlane: ScenePlane? = null
        var closestDist = Float.MAX_VALUE

        for (scenePlane in scenePlanes) {
            val intersection = Vector3()
            if (Intersector.intersectRayPlane(ray, scenePlane.plane, intersection)) {
                val dist = ray.origin.dst2(intersection) // squared distance
                if (dist < closestDist) {
                    closestDist = dist
                    closestIntersection = intersection
                    closestPlane = scenePlane
                }
            }
        }

        return if (closestIntersection != null && closestPlane != null) {
            closestPlane to closestIntersection
        } else null
    }

    private val initialDrawingPoint = Vector3()
    var drawingBlock: ModelInstance? = null

    companion object {
        private val auxMatrix = Matrix4()
        private const val WORLD_SIZE = 40F

    }

    override fun dispose() {
        cubeModel.dispose()
    }
}
