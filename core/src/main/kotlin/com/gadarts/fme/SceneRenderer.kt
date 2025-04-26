package com.gadarts.fme

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable

class SceneRenderer : Table(), InputProcessor, Disposable {
    private val auxiliaryModels = AuxiliaryModels()
    private val blocks = mutableListOf<Block>()
    private var mode: Modes? = Modes.CREATE
    private val handlers = SceneRendererHandlers(blocks, auxiliaryModels)


    fun render() {
        handlers.cameraHandler.update()
        val screenPosition = localToScreenCoordinates(auxVector2.set(0F, 0F))
        handlers.renderingHandler.render(screenPosition)
    }

    companion object {
        private val auxVector2 = com.badlogic.gdx.math.Vector2()
    }

    fun init() {
        (Gdx.input.inputProcessor as InputMultiplexer).addProcessor(this)
    }

    fun setMode(mode: Modes) {
        this.mode = mode
        handlers.cameraHandler.modeUpdated(mode)
    }

    override fun keyDown(keycode: Int): Boolean {
        if (mode == Modes.FACES) {
            return handlers.facesHandler.keyDown(keycode)
        }
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        if (mode == Modes.FACES) {
            return handlers.facesHandler.keyUp(keycode)
        }
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    data class ScenePlane(val plane: Plane, val name: String)

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (mode == Modes.CREATE) {
            handlers.drawingHandler.applyCreate(screenX, screenY)
            return true
        } else if (mode == Modes.FACES) {
            handlers.facesHandler.applySelectFace(screenX, screenY)
            return true
        }
        return false
    }


    data class Block(val modelInstance: ModelInstance, val bounds: BoundingBox)


    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return handlers.drawingHandler.touchUp()
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }


    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (mode == Modes.CREATE) {
            return handlers.drawingHandler.touchDragged(screenX, screenY)
        } else if (mode == Modes.FACES) {
            return handlers.facesHandler.touchDragged(screenX, screenY)
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
        handlers.dispose()
        auxiliaryModels.dispose()
    }

}
