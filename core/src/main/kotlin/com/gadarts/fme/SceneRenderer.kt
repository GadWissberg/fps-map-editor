package com.gadarts.fme

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.scenes.scene2d.ui.Table

class SceneRenderer : Table() {
    private var mode: Modes? = null
    private val auxiliaryModelInstances = AuxiliaryModelInstances()
    private val modelsBatch = ModelBatch()
    private val camera: PerspectiveCamera = createCamera()
    private val cameraController = CameraInputController(camera)

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
            stage.height.toInt() - screenPosition.y.toInt(),
            Gdx.graphics.backBufferWidth,
            Gdx.graphics.backBufferHeight
        )
        camera.update()
        renderModels()
    }

    private fun renderModels() {
        modelsBatch.begin(camera)
        auxiliaryModelInstances.render(modelsBatch)
        modelsBatch.end()
    }

    fun init() {
        (Gdx.input.inputProcessor as InputMultiplexer).addProcessor(cameraController)
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
}
