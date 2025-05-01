package com.gadarts.fme.sr.handlers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.math.Vector3
import com.gadarts.fme.sr.Modes

class CameraHandler {
    fun update() {
        cameraController.update()
        camera.update()
    }

    fun modeUpdated(mode: Modes) {
        val inputMultiplexer = Gdx.input.inputProcessor as InputMultiplexer
        if (mode == Modes.CAMERA) {
            inputMultiplexer.addProcessor(cameraController)
        } else {
            inputMultiplexer.removeProcessor(cameraController)
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

    val camera: PerspectiveCamera = createCamera()
    private val cameraController = CameraInputController(camera)

}
