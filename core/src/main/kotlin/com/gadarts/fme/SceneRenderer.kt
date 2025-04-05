package com.gadarts.fme

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable

class SceneRenderer : Table(), InputProcessor, Disposable {
    private val blocks = mutableListOf<ModelInstance>()
    private var mode: Modes? = Modes.CREATE
    private val auxiliaryModelInstances = AuxiliaryModelInstances()
    private val modelsBatch = ModelBatch()
    private val camera: PerspectiveCamera = createCamera()
    private val cameraController = CameraInputController(camera)
    private val cubeModel = createCubeModel()

    private fun createCubeModel(): Model {
        val modelBuilder = ModelBuilder()
        return modelBuilder.createBox(0.5F, 0.5F, 0.5F, Material(), ColorAttribute.Diffuse)
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
            modelsBatch.render(it)
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
                val modelInstance = ModelInstance(cubeModel)
                modelInstance.transform.setTranslation(point)
                blocks.add(modelInstance)
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
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    private val scenePlanes = listOf(
        ScenePlane(Plane(Vector3.Y, 0f), "floor"),                // y = 0
        ScenePlane(Plane(Vector3.Y.cpy().scl(-1f), 10f), "ceiling"),  // y = 10
        ScenePlane(Plane(Vector3.X, 0F), "leftWall"),           // x = -5
        ScenePlane(Plane(Vector3.X.cpy().scl(-1f), 5f), "rightWall"), // x = 5
        ScenePlane(Plane(Vector3.Z, 0F), "backWall"),           // z = -5
        ScenePlane(Plane(Vector3.Z.cpy().scl(-1f), 5f), "frontWall")  // z = 5
    )

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
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
