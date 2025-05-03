package com.gadarts.fme

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.gadarts.fme.sr.Modes
import com.gadarts.fme.sr.SceneRenderer
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.widget.Menu
import com.kotcrab.vis.ui.widget.MenuBar
import com.kotcrab.vis.ui.widget.MenuItem
import com.kotcrab.vis.ui.widget.VisTable

class FpsMapEditor : ApplicationAdapter() {
    private lateinit var menuBar: MenuBar
    private val sceneRenderer: SceneRenderer by lazy { SceneRenderer() }
    private val stage: Stage by lazy { Stage(ScreenViewport()) }

    override fun create() {
        Gdx.input.inputProcessor = InputMultiplexer(stage)
        VisUI.load()
        menuBar = addMenu()
        val root = VisTable()
        root.setFillParent(true)
        root.top()
        stage.addActor(root)
        val stack = Stack()
        stack.setFillParent(true)
        root.add(menuBar.table).fill().expandX()
        root.pack()
        sceneRenderer.init()
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
        sceneRenderer.render()
        stage.act()
        stage.draw()
    }

    private fun addMenu(): MenuBar {
        val menuBar = MenuBar()
        val fileMenu = Menu("File")
        val newItem = MenuItem("New")
        val saveItem = MenuItem("Save")
        addFileMenuItemClickListener(saveItem) { sceneRenderer.save() }
        val openItem = MenuItem("Open")
        addFileMenuItemClickListener(openItem) { sceneRenderer.load() }
        val exitItem = MenuItem("Exit")
        exitItem.setShortcut("Ctrl+Q")
        fileMenu.addItem(newItem)
        fileMenu.addItem(saveItem)
        fileMenu.addItem(openItem)
        fileMenu.addSeparator()
        fileMenu.addItem(exitItem)
        menuBar.addMenu(fileMenu)
        addModeMenu(menuBar)
        return menuBar
    }

    private fun addFileMenuItemClickListener(openItem: MenuItem, click: () -> Unit) {
        openItem.addListener(
            object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    super.clicked(event, x, y)
                    click.invoke()
                }
            }
        )
    }

    private fun addModeMenu(menuBar: MenuBar) {
        val editMenu = Menu("Mode")
        val cameraMenuItem = MenuItem("Camera")
        addClickListenerToModeMenuItem(cameraMenuItem, Modes.CAMERA)
        editMenu.addItem(cameraMenuItem)
        val createMenuItem = MenuItem("Create")
        addClickListenerToModeMenuItem(createMenuItem, Modes.CREATE)
        editMenu.addItem(createMenuItem)
        val selectFaceMenuItem = MenuItem("Faces")
        addClickListenerToModeMenuItem(selectFaceMenuItem, Modes.FACES)
        editMenu.addItem(selectFaceMenuItem)
        ButtonGroup(cameraMenuItem, createMenuItem, selectFaceMenuItem).apply {
            setMaxCheckCount(1)
            setMinCheckCount(1)
            setUncheckLast(true)
        }
        menuBar.addMenu(editMenu)
    }

    private fun addClickListenerToModeMenuItem(cameraMenuItem: MenuItem, mode: Modes) {
        cameraMenuItem.addListener(
            object : ClickListener() {
                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    super.clicked(event, x, y)
                    sceneRenderer.setMode(mode)
                }
            }
        )
    }

}
