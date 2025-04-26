package com.gadarts.fme

import com.badlogic.gdx.utils.Disposable

data class SceneRendererHandlers(
    private val blocks: MutableList<SceneRenderer.Block>,
    private val auxiliaryModels: AuxiliaryModels
) : Disposable {
    val cameraHandler = CameraHandler()
    val drawingHandler = DrawingHandler(blocks, cameraHandler.camera)
    val facesHandler = FacesHandler(cameraHandler.camera, blocks)
    val renderingHandler =
        RenderingHandler(blocks, auxiliaryModels, drawingHandler, facesHandler, cameraHandler)

    override fun dispose() {
        drawingHandler.dispose()
        facesHandler.dispose()
        auxiliaryModels.dispose()
    }

}
