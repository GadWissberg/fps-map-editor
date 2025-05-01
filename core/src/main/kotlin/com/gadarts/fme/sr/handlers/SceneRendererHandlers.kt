package com.gadarts.fme.sr.handlers

import com.badlogic.gdx.utils.Disposable
import com.gadarts.fme.AuxiliaryModels
import com.gadarts.fme.sr.SceneRenderer

data class SceneRendererHandlers(
    private val blocks: MutableList<SceneRenderer.Block>,
    private val auxiliaryModels: AuxiliaryModels
) : Disposable {
    val cameraHandler = CameraHandler()
    val drawingHandler = DrawingHandler(blocks, cameraHandler.camera)
    val modeFacesHandler = ModeFacesHandler(cameraHandler.camera, blocks)
    val renderingHandler =
        RenderingHandler(blocks, auxiliaryModels, drawingHandler, modeFacesHandler, cameraHandler)

    override fun dispose() {
        drawingHandler.dispose()
        modeFacesHandler.dispose()
        auxiliaryModels.dispose()
    }

}
