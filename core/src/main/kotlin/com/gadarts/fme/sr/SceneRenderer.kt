package com.gadarts.fme.sr

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import com.gadarts.fme.sr.handlers.SceneRendererHandlers
import com.gadarts.fme.utils.MeshUtils
import com.gadarts.fme.utils.MeshUtils.createIndexedCubeModel
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class SceneRenderer : Table(), InputProcessor, Disposable {
    private val auxiliaryModels = AuxiliaryModels()
    private val blocks = mutableListOf<Block>()
    private var mode: Modes? = Modes.CREATE
    private val handlers = SceneRendererHandlers(blocks, auxiliaryModels)

    init {
        GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Vector3::class.java, Vector3Adapter())
            .create()
    }

    fun render() {
        handlers.cameraHandler.update()
        val screenPosition = localToScreenCoordinates(auxVector2.set(0F, 0F))
        handlers.renderingHandler.render(screenPosition)
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
            return handlers.modeFacesHandler.keyDown(keycode)
        }
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        if (mode == Modes.FACES) {
            return handlers.modeFacesHandler.keyTyped(character)
        }
        return false
    }

    data class ScenePlane(val plane: Plane, val name: String)

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (mode == Modes.CREATE) {
            handlers.drawingHandler.applyCreate(screenX, screenY)
            return true
        } else if (mode == Modes.FACES) {
            handlers.modeFacesHandler.applySelectFace(screenX, screenY)
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

    fun save() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val blockDataList = blocks.map { block ->
            val pos = Vector3()
            block.modelInstance.transform.getTranslation(pos)

            val mesh = block.modelInstance.model.meshes.first()
            val vertexData = MeshUtils.getVerticesForMesh(mesh)
            val posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
            val stride = mesh.vertexSize / 4

            val vertices = mutableListOf<Vector3Data>()
            for (i in 0 until mesh.numVertices) {
                val base = i * stride + posOffset
                val x = vertexData[base]
                val y = vertexData[base + 1]
                val z = vertexData[base + 2]
                vertices.add(Vector3Data(x, y, z))
            }

            BlockData(Vector3Data(pos.x, pos.y, pos.z), vertices)
        }

        val json = gson.toJson(blockDataList)
        Gdx.files.local(TEMP_FILE).writeString(json, false)
    }

    fun load() {
        val file = Gdx.files.local(TEMP_FILE)
        val gson = Gson()
        val json = file.readString()

        val type = object : TypeToken<List<BlockData>>() {}.type
        val loadedBlocks: List<BlockData> = gson.fromJson(json, type)

        blocks.clear()

        for (blockData in loadedBlocks) {
            val pos = Vector3(blockData.position.x, blockData.position.y, blockData.position.z)
            val vertices = blockData.vertices.map { Vector3(it.x, it.y, it.z) }

            val model = createModelFromVertices(vertices)
            val instance = ModelInstance(model)
            instance.transform.setTranslation(pos)

            val bounds = BoundingBox()
            instance.calculateBoundingBox(bounds)
            bounds.mul(instance.transform)

            blocks.add(Block(instance, bounds))
        }
    }

    private fun createModelFromVertices(newPositions: List<Vector3>): Model {
        require(newPositions.size == 8) { "Expected 8 vertex positions for cube restoration." }

        val model = createIndexedCubeModel()

        val mesh = model.meshes.first()
        val vertexSize = mesh.vertexSize / 4
        val posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
        val normalOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Normal)?.offset?.div(4) ?: (posOffset + 3)

        val vertices = FloatArray(mesh.numVertices * vertexSize)
        mesh.getVertices(vertices)

        // Recalculate flat normals per face based on new positions
        val indices = ShortArray(mesh.numIndices)
        mesh.getIndices(indices)
        val vertexNormals = Array(newPositions.size) { Vector3() }

        for (i in indices.indices step 3) {
            val i1 = indices[i].toInt()
            val i2 = indices[i + 1].toInt()
            val i3 = indices[i + 2].toInt()

            val v1 = newPositions[i1]
            val v2 = newPositions[i2]
            val v3 = newPositions[i3]

            val normal = v2.cpy().sub(v1).crs(v3.cpy().sub(v1)).nor()
            vertexNormals[i1].add(normal)
            vertexNormals[i2].add(normal)
            vertexNormals[i3].add(normal)
        }
        vertexNormals.forEach { it.nor() }

        // Overwrite vertex positions and normals
        for (i in newPositions.indices) {
            val base = i * vertexSize
            vertices[base + posOffset] = newPositions[i].x
            vertices[base + posOffset + 1] = newPositions[i].y
            vertices[base + posOffset + 2] = newPositions[i].z

            vertices[base + normalOffset] = vertexNormals[i].x
            vertices[base + normalOffset + 1] = vertexNormals[i].y
            vertices[base + normalOffset + 2] = vertexNormals[i].z
        }

        mesh.setVertices(vertices)

        return model
    }

    companion object {
        private val auxVector2 = com.badlogic.gdx.math.Vector2()
        private const val TEMP_FILE = "blocks.json"
    }
}

@Suppress("unused")
data class Vector3Data(val x: Float, val y: Float, val z: Float) {
    companion object {
        fun fromVector3(v: Vector3): Vector3Data = Vector3Data(v.x, v.y, v.z)
        fun toVector3(data: Vector3Data): Vector3 = Vector3(data.x, data.y, data.z)
    }
}

data class BlockData(
    val position: Vector3Data,
    val vertices: List<Vector3Data>
)

class Vector3Adapter : JsonSerializer<Vector3>, JsonDeserializer<Vector3> {
    override fun serialize(src: Vector3, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val obj = JsonObject()
        obj.addProperty("x", src.x)
        obj.addProperty("y", src.y)
        obj.addProperty("z", src.z)
        return obj
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Vector3 {
        val obj = json.asJsonObject
        return Vector3(
            obj.get("x").asFloat,
            obj.get("y").asFloat,
            obj.get("z").asFloat
        )
    }
}
