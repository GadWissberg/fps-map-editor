package com.gadarts.fme

import com.badlogic.gdx.utils.Disposable

data class SceneRendererHandlers(
    private val blocks: MutableList<SceneRenderer.Block>,
    private val auxiliaryModels: AuxiliaryModels
) : Disposable {
    val cameraHandler = CameraHandler()
    val drawingHandler = DrawingHandler(blocks, cameraHandler.camera)
    val highlightHandler = HighlightHandler(cameraHandler.camera, blocks)
    val renderingHandler =
        RenderingHandler(blocks, auxiliaryModels, drawingHandler, highlightHandler, cameraHandler)

    override fun dispose() {
        drawingHandler.dispose()
        highlightHandler.dispose()
        auxiliaryModels.dispose()
    }

}
