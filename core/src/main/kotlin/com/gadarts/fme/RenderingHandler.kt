package com.gadarts.fme

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.Vector2

class RenderingHandler(
    private val blocks: MutableList<SceneRenderer.Block>,
    private val auxiliaryModels: AuxiliaryModels,
    private val drawingHandler: DrawingHandler,
    private val highlightHandler: FacesHandler,
    private val cameraHandler: CameraHandler
) {
    private val modelsBatch = ModelBatch()
    private val environment = createEnvironment()

    fun render(screenPosition: Vector2) {
        Gdx.gl.glViewport(
            screenPosition.x.toInt(),
            screenPosition.y.toInt(),
            Gdx.graphics.backBufferWidth,
            Gdx.graphics.backBufferHeight
        )
        modelsBatch.begin(cameraHandler.camera)
        auxiliaryModels.render(modelsBatch)
        blocks.forEach {
            modelsBatch.render(it.modelInstance, environment)
        }
        if (drawingHandler.drawingBlock != null) {
            modelsBatch.render(drawingHandler.drawingBlock, environment)
        }
        highlightHandler.render(modelsBatch, environment)
        modelsBatch.end()
    }

    private fun createEnvironment(): Environment {
        val environment = Environment()
        environment.set(ColorAttribute.createAmbientLight(0.5f, 0.5f, 0.5f, 1f))
        val directionalLight = DirectionalLight()
        directionalLight.set(Color.WHITE, 0.5f, -0.8f, -0.2f)
        environment.add(directionalLight)
        return environment
    }

}
