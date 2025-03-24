package com.gadarts.fme

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.VisWindow

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms. */
class FpsMapEditor : ApplicationAdapter() {
    override fun create() {
        VisUI.load()
        val stage = Stage(ScreenViewport())
        Gdx.input.inputProcessor = stage

        val window = VisWindow("My Window") // Title of the window
        window.setSize(300f, 200f)
        window.setPosition(100f, 100f)
        window.isMovable = true

        window.add(Label("Hello from VisUI", VisUI.getSkin())).row()
        window.add(TextButton("Click me", VisUI.getSkin()))

        stage.addActor(window)
    }
}
