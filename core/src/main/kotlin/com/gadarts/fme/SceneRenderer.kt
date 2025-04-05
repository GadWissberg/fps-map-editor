package com.gadarts.fme

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.*
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs

class SceneRenderer : Table(), InputProcessor, Disposable {
    private val initialDrawingPoint = Vector3()
    private var drawingBlock: ModelInstance? = null
    private val blocks = mutableListOf<ModelInstance>()
    private var mode: Modes? = Modes.CREATE
    private val auxiliaryModelInstances = AuxiliaryModelInstances()
    private val modelsBatch = ModelBatch()
    private val camera: PerspectiveCamera = createCamera()
    private val cameraController = CameraInputController(camera)
    private val cubeModel = createCubeModel()
    private val environment = createEnvironment()

    private fun createEnvironment(): Environment {
        val environment = Environment()
        environment.set(ColorAttribute.createAmbientLight(0.5f, 0.5f, 0.5f, 1f))
        val directionalLight = DirectionalLight()
        directionalLight.set(Color.WHITE, 0.5f, -0.8f, -0.2f)
        environment.add(directionalLight)
        return environment
    }

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

    private fun createCamera(): PerspectiveCamera {
        val cam = PerspectiveCamera(67F, 1280F, 960F)
        cam.near = 0.01f
        cam.far = 100.0f
        cam.position[9.0f, 16.0f] = 9.0f
        cam.direction.rotate(Vector3.X, -55.0f)
        cam.direction.rotate(Vector3.Y, 45.0f)
        return cam
    }

    fun render() {
        cameraController.update()
        val screenPosition = localToScreenCoordinates(auxVector2.set(0F, 0F))
        Gdx.gl.glViewport(
            screenPosition.x.toInt(),
            screenPosition.y.toInt(),
            Gdx.graphics.backBufferWidth,
            Gdx.graphics.backBufferHeight
        )
        camera.update()
        renderModels()
    }

    private fun renderModels() {
        modelsBatch.begin(camera)
        auxiliaryModelInstances.render(modelsBatch)
        blocks.forEach {
            modelsBatch.render(it, environment)
        }
        if (drawingBlock != null) {
            modelsBatch.render(drawingBlock, environment)
        }
        modelsBatch.end()
    }

    fun init() {
        (Gdx.input.inputProcessor as InputMultiplexer).addProcessor(this)
    }

    fun setMode(mode: Modes) {
        this.mode = mode
        val inputMultiplexer = Gdx.input.inputProcessor as InputMultiplexer
        if (mode == Modes.CAMERA) {
            inputMultiplexer.addProcessor(cameraController)
        } else {
            inputMultiplexer.removeProcessor(cameraController)
        }
    }

    companion object {
        private val auxVector2 = Vector2()
        private val auxMatrix = Matrix4()
        private const val WORLD_SIZE = 40F
    }

    override fun keyDown(keycode: Int): Boolean {
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    data class ScenePlane(val plane: Plane, val name: String)

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (mode == Modes.CREATE) {

            val relativeX = screenX.toFloat()
            val relativeY = screenY.toFloat()

            camera.getPickRay(relativeX, relativeY)
            val hit = getFirstPlaneIntersection(screenX, screenY, camera, scenePlanes)
            if (hit != null) {
                val (_, point) = hit
                point.set(point.x.toInt().toFloat(), point.y.toInt().toFloat(), point.z.toInt().toFloat())
                val modelInstance = ModelInstance(cubeModel)
                modelInstance.transform.setTranslation(point)
                drawingBlock = modelInstance
                initialDrawingPoint.set(point)
                auxMatrix.set(drawingBlock!!.transform)
            }
            return true
        }
        return false
    }

    private fun getFirstPlaneIntersection(
        screenX: Int,
        screenY: Int,
        camera: PerspectiveCamera,
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

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (drawingBlock != null) {
            blocks.add(drawingBlock!!)
            drawingBlock = null
            return true
        }
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    private val scenePlanes = listOf(
        ScenePlane(Plane(Vector3.Y, 0f), "floor"),
        ScenePlane(Plane(Vector3.Y.cpy().scl(-1f), WORLD_SIZE), "ceiling"),
        ScenePlane(Plane(Vector3.X, 0F), "leftWall"),
        ScenePlane(Plane(Vector3.X.cpy().scl(-1f), WORLD_SIZE), "rightWall"),
        ScenePlane(Plane(Vector3.Z, 0F), "backWall"),
        ScenePlane(Plane(Vector3.Z.cpy().scl(-1f), WORLD_SIZE), "frontWall")
    )

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (drawingBlock != null) {
            val relativeX = screenX.toFloat()
            val relativeY = screenY.toFloat()

            camera.getPickRay(relativeX, relativeY)
            val hit = getFirstPlaneIntersection(screenX, screenY, camera, scenePlanes)
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

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return false
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }

    override fun dispose() {
        cubeModel.dispose()
        auxiliaryModelInstances.dispose()
    }

}
