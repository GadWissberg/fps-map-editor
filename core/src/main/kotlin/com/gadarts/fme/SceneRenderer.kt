package com.gadarts.fme

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.*
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import kotlin.math.abs

class SceneRenderer : Table(), InputProcessor, Disposable {
    private var highlightedModel: Model? = null
    private var highlightedTriangleInstance: ModelInstance? = null
    private val initialDrawingPoint = Vector3()
    private var drawingBlock: ModelInstance? = null
    private val blocks = mutableListOf<Block>()
    private var mode: Modes? = Modes.CREATE
    private val auxiliaryModelInstances = AuxiliaryModelInstances()
    private val modelsBatch = ModelBatch()
    private val camera: PerspectiveCamera = createCamera()
    private val cameraController = CameraInputController(camera)
    private val cubeModel = createCubeModel()
    private val environment = createEnvironment()

    private fun createEnvironment(): Environment {
        val environment = Environment()
        environment.set(ColorAttribute.createAmbientLight(0.5f, 0.5f, 0.5f, 1f))
        val directionalLight = DirectionalLight()
        directionalLight.set(Color.WHITE, 0.5f, -0.8f, -0.2f)
        environment.add(directionalLight)
        return environment
    }

    private fun createCubeModel(): Model {
        val modelBuilder = ModelBuilder()

        return modelBuilder.createBox(
            1f, 1f, 1f, // width, height, depth
            Material(ColorAttribute.createDiffuse(Color.GRAY)),
            (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
        ).apply {
            nodes.first().translation.set(0.5f, 0.5f, 0.5f)
            calculateTransforms()
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

    fun render() {
        cameraController.update()
        val screenPosition = localToScreenCoordinates(auxVector2.set(0F, 0F))
        Gdx.gl.glViewport(
            screenPosition.x.toInt(),
            screenPosition.y.toInt(),
            Gdx.graphics.backBufferWidth,
            Gdx.graphics.backBufferHeight
        )
        camera.update()
        renderModels()
    }

    private fun renderModels() {
        modelsBatch.begin(camera)
        auxiliaryModelInstances.render(modelsBatch)
        blocks.forEach {
            modelsBatch.render(it.modelInstance, environment)
        }
        if (drawingBlock != null) {
            modelsBatch.render(drawingBlock, environment)
        }
        if (highlightedTriangleInstance != null) {
            modelsBatch.render(highlightedTriangleInstance, environment)
        }
        modelsBatch.end()
    }

    fun init() {
        (Gdx.input.inputProcessor as InputMultiplexer).addProcessor(this)
    }

    fun setMode(mode: Modes) {
        this.mode = mode
        val inputMultiplexer = Gdx.input.inputProcessor as InputMultiplexer
        if (mode == Modes.CAMERA) {
            inputMultiplexer.addProcessor(cameraController)
        } else {
            inputMultiplexer.removeProcessor(cameraController)
        }
    }

    companion object {
        private val auxVector2 = Vector2()
        private val auxMatrix = Matrix4()
        private const val WORLD_SIZE = 40F
    }

    override fun keyDown(keycode: Int): Boolean {
        return false
    }

    override fun keyUp(keycode: Int): Boolean {
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    data class ScenePlane(val plane: Plane, val name: String)

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (mode == Modes.CREATE) {
            applyCreate(screenX, screenY)
            return true
        } else if (mode == Modes.SELECT_FACE) {
            applySelectFace(screenX, screenY)
            return true
        }
        return false
    }

    private fun applySelectFace(screenX: Int, screenY: Int) {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        var closestBlock: Block? = null
        var minDistance = Float.MAX_VALUE
        for (block in blocks) {
            if (Intersector.intersectRayBoundsFast(ray, block.bounds)) {
                val center = Vector3()
                block.bounds.getCenter(center)
                val dist = ray.origin.dst2(center)
                if (dist < minDistance) {
                    minDistance = dist
                    closestBlock = block
                }
            }
        }
        if (closestBlock != null) {
            val rectangleVerts = getHitRectangle(ray, closestBlock.modelInstance)
            if (rectangleVerts != null) {
                highlightedTriangleInstance = createRectangleHighlight(rectangleVerts)
                highlightedTriangleInstance!!.transform.idt() // FIX: use identity transform here
            }
        }
    }

    private fun createRectangleHighlight(vertices: List<Vector3>): ModelInstance {
        val verticesFlat = FloatArray(vertices.size * 3)
        vertices.forEachIndexed { idx, v ->
            verticesFlat[idx * 3] = v.x
            verticesFlat[idx * 3 + 1] = v.y
            verticesFlat[idx * 3 + 2] = v.z
        }

        val indices = shortArrayOf(0, 1, 2, 3, 4, 5) // Two triangles forming the rectangle

        val mesh = Mesh(
            true, 6, 6,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position")
        )

        mesh.setVertices(verticesFlat)
        mesh.setIndices(indices)

        val material = Material(ColorAttribute.createDiffuse(Color.RED))
        val meshPart = MeshPart("rectangle", mesh, 0, 6, GL20.GL_TRIANGLES)
        val nodePart = NodePart().apply {
            this.meshPart = meshPart
            this.material = material
        }

        val node = Node().apply {
            id = "rectangle"
            parts.add(nodePart)
        }

        highlightedModel?.dispose()
        highlightedModel = Model().apply {
            meshes.add(mesh)
            materials.add(material)
            nodes.add(node)
            manageDisposable(mesh)
        }

        return ModelInstance(highlightedModel)
    }

    private fun transformVertex(v: Vector3, instance: ModelInstance, node: Node): Vector3 {
        return v.cpy().mul(node.globalTransform).mul(instance.transform)
    }

    private fun getHitRectangle(ray: Ray, instance: ModelInstance): List<Vector3>? {
        val mesh = instance.model.meshes.first()
        val node = instance.nodes.first() // crucial step: getting the node
        val vertices = getVerticesForMesh(mesh)
        val indices = getIndicesForMesh(mesh)
        val posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
        val stride = mesh.vertexSize / 4

        var minDist = Float.MAX_VALUE
        var hitFaceStartIndex = -1
        val intersection = Vector3()

        for (i in indices.indices step 3) {
            val i1 = indices[i].toInt()
            val i2 = indices[i + 1].toInt()
            val i3 = indices[i + 2].toInt()

            val v1 = transformVertex(createVertexVector(vertices, i1, stride, posOffset), instance, node)
            val v2 = transformVertex(createVertexVector(vertices, i2, stride, posOffset), instance, node)
            val v3 = transformVertex(createVertexVector(vertices, i3, stride, posOffset), instance, node)

            if (Intersector.intersectRayTriangle(ray, v1, v2, v3, intersection)) {
                val dist = ray.origin.dst2(intersection)
                if (dist < minDist) {
                    minDist = dist
                    hitFaceStartIndex = i - (i % 6)
                }
            }
        }

        if (hitFaceStartIndex != -1) {
            val rectangleVertices = mutableListOf<Vector3>()
            for (i in hitFaceStartIndex until hitFaceStartIndex + 6) {
                val idx = indices[i].toInt()
                val vert = transformVertex(createVertexVector(vertices, idx, stride, posOffset), instance, node)
                rectangleVertices.add(vert)
            }
            return rectangleVertices
        }

        return null
    }

    private fun createVertexVector(
        vertices: FloatArray,
        i1: Int,
        stride: Int,
        posOffset: Int,
    ): Vector3 {
        return Vector3(
            vertices[i1 * stride + posOffset],
            vertices[i1 * stride + posOffset + 1],
            vertices[i1 * stride + posOffset + 2]
        )
    }

    private fun getIndicesForMesh(mesh: Mesh): ShortArray {
        val indices = ShortArray(mesh.numIndices)
        mesh.getIndices(indices)
        return indices
    }

    private fun getVerticesForMesh(mesh: Mesh): FloatArray {
        val vertices = FloatArray(mesh.numVertices * mesh.vertexSize / 4)
        mesh.getVertices(vertices)
        return vertices
    }

    data class Block(val modelInstance: ModelInstance, val bounds: BoundingBox)

    private fun applyCreate(screenX: Int, screenY: Int) {
        val relativeX = screenX.toFloat()
        val relativeY = screenY.toFloat()
        camera.getPickRay(relativeX, relativeY)
        val hit = getFirstPlaneIntersection(screenX, screenY, camera, scenePlanes)
        if (hit != null) {
            val (_, point) = hit
            point.set(point.x.toInt().toFloat(), point.y.toInt().toFloat(), point.z.toInt().toFloat())
            val modelInstance = ModelInstance(cubeModel)
            modelInstance.transform.setTranslation(point)
            drawingBlock = modelInstance
            initialDrawingPoint.set(point)
            auxMatrix.set(drawingBlock!!.transform)
        }
    }

    private fun getFirstPlaneIntersection(
        screenX: Int,
        screenY: Int,
        camera: PerspectiveCamera,
        scenePlanes: List<ScenePlane>
    ): Pair<ScenePlane, Vector3>? {
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        var closestIntersection: Vector3? = null
        var closestPlane: ScenePlane? = null
        var closestDist = Float.MAX_VALUE

        for (scenePlane in scenePlanes) {
            val intersection = Vector3()
            if (Intersector.intersectRayPlane(ray, scenePlane.plane, intersection)) {
                val dist = ray.origin.dst2(intersection) // squared distance
                if (dist < closestDist) {
                    closestDist = dist
                    closestIntersection = intersection
                    closestPlane = scenePlane
                }
            }
        }

        return if (closestIntersection != null && closestPlane != null) {
            closestPlane to closestIntersection
        } else null
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (drawingBlock != null) {
            val bounds = BoundingBox()
            drawingBlock!!.calculateBoundingBox(bounds)
            bounds.mul(drawingBlock!!.transform)
            val block = Block(drawingBlock!!, bounds)
            blocks.add(block)
            drawingBlock = null
            return true
        }
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    private val scenePlanes = listOf(
        ScenePlane(Plane(Vector3.Y, 0f), "floor"),
        ScenePlane(Plane(Vector3.Y.cpy().scl(-1f), WORLD_SIZE), "ceiling"),
        ScenePlane(Plane(Vector3.X, 0F), "leftWall"),
        ScenePlane(Plane(Vector3.X.cpy().scl(-1f), WORLD_SIZE), "rightWall"),
        ScenePlane(Plane(Vector3.Z, 0F), "backWall"),
        ScenePlane(Plane(Vector3.Z.cpy().scl(-1f), WORLD_SIZE), "frontWall")
    )

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        if (drawingBlock != null) {
            val relativeX = screenX.toFloat()
            val relativeY = screenY.toFloat()

            camera.getPickRay(relativeX, relativeY)
            val hit = getFirstPlaneIntersection(screenX, screenY, camera, scenePlanes)
            if (hit != null) {
                val (_, point) = hit
                point.set(point.x.toInt().toFloat(), point.y.toInt().toFloat(), point.z.toInt().toFloat())
                drawingBlock!!.transform.set(auxMatrix)
                drawingBlock!!.transform.scl(
                    if (abs(point.x - initialDrawingPoint.x) > 1F) point.x - initialDrawingPoint.x else 1F,
                    1F,
                    if (abs(point.z - initialDrawingPoint.z) > 1F) point.z - initialDrawingPoint.z else 1F
                )
                return true
            }
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
        cubeModel.dispose()
        auxiliaryModelInstances.dispose()
        highlightedModel?.dispose()
    }

}
