package com.gadarts.fme

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.Menu
import com.kotcrab.vis.ui.widget.MenuBar
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.VisTable

class FpsMapEditor : ApplicationAdapter() {
    private val sceneRenderer: SceneRenderer by lazy { SceneRenderer() }
    private val stage: Stage by lazy { Stage(ScreenViewport()) }
    override fun create() {
        Gdx.input.inputProcessor = InputMultiplexer(stage)
        VisUI.load()
        val menuBar = addMenu()
        val root = VisTable()
        root.setFillParent(true)
        root.top()
        root.add(menuBar.table).expandX().fillX().row()
        stage.addActor(root)
        val heightUnderBars = WINDOW_HEIGHT - (menuBar.table.height)
        root.add(sceneRenderer).size(WINDOW_WIDTH, heightUnderBars)
        root.pack()
        sceneRenderer.init()
    }

    private fun addMenu(): MenuBar {
        val menuBar = MenuBar()
        val fileMenu = Menu("File")
        val newItem = MenuItem("New")
        val openItem = MenuItem("Open")
        val exitItem = MenuItem("Exit")
        exitItem.setShortcut("Ctrl+Q")
        fileMenu.addItem(newItem)
        fileMenu.addItem(openItem)
        fileMenu.addSeparator()
        fileMenu.addItem(exitItem)
        val editMenu = Menu("Edit")
        editMenu.addItem(MenuItem("Undo"))
        editMenu.addItem(MenuItem("Redo"))
        menuBar.addMenu(fileMenu)
        menuBar.addMenu(editMenu)
        return menuBar
    }

    override fun render() {
        super.render()
        Gdx.gl.glViewport(
            0,
            0,
            Gdx.graphics.backBufferWidth,
            Gdx.graphics.backBufferHeight
        )
        ScreenUtils.clear(Color.BLACK, true)
        stage.act()
        stage.draw()
        sceneRenderer.render()
    }

    companion object {
        const val WINDOW_WIDTH = 1280F
        const val WINDOW_HEIGHT = 960F
    }
}
