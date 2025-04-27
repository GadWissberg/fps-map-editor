package com.gadarts.fme

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Disposable
import com.gadarts.fme.SceneRenderer.Block

private enum class Axis { X, Y, Z }
class FacesHandler(private val camera: PerspectiveCamera, private val blocks: MutableList<Block>) : Disposable {
    private var selectedBlock: Block? = null
    private var selectedFaceIndices: Set<Int> = emptySet()
    private var connectedVertices: Set<Int> = emptySet()
    private var highlightedMesh: Mesh? = null
    private var selectedAxis: Axis? = null
    fun render(modelsBatch: ModelBatch, environment: Environment) {
        if (highlightedTriangleInstance != null) {
            modelsBatch.render(highlightedTriangleInstance, environment)
        }
    }


    fun applySelectFace(screenX: Int, screenY: Int) {
        // Clear old highlight
        highlightedTriangleInstance = null
        highlightedModel?.dispose()
        highlightedModel = null
        highlightedMesh = null

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
            selectedBlock = closestBlock

            // Now, hit test on this specific block
            selectedFaceIndices = getLastHitFaceIndices(closestBlock.modelInstance, ray)
            connectedVertices = selectedFaceIndices

            if (selectedFaceIndices.isNotEmpty()) {
                // Build the highlight mesh from the real selectedFaceIndices (not a new raycast)
                val rectangleUniqueVerts = mutableListOf<Vector3>()
                val mesh = closestBlock.modelInstance.model.meshes.first()
                val vertices = getVerticesForMesh(mesh)
                val posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
                val stride = mesh.vertexSize / 4

                for (idx in selectedFaceIndices) {
                    val baseIdx = idx * stride + posOffset
                    val localVertex = Vector3(
                        vertices[baseIdx],
                        vertices[baseIdx + 1],
                        vertices[baseIdx + 2]
                    )
                    val worldVertex = localVertex.prj(closestBlock.modelInstance.transform)
                    rectangleUniqueVerts.add(worldVertex)
                }

                highlightedTriangleInstance = createRectangleHighlight(rectangleUniqueVerts)
                highlightedTriangleInstance!!.transform.idt()
            }

            lastScreenY = screenY
        }
    }

    private fun updateHighlightVertices() {
        val block = selectedBlock ?: return
        val mesh = block.modelInstance.model.meshes.first()
        val vertices = getVerticesForMesh(mesh)
        val posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
        val stride = mesh.vertexSize / 4

        if (selectedFaceIndices.isEmpty()) return

        val uniqueFaceIndices = selectedFaceIndices.distinct()

        val highlightVertices = FloatArray(4 * 3) // 4 vertices, 3 floats each

        for (i in 0 until 4) {
            val idx = uniqueFaceIndices[i]
            val baseIdx = idx * stride + posOffset

            val localVertex = Vector3(
                vertices[baseIdx],
                vertices[baseIdx + 1],
                vertices[baseIdx + 2]
            )
            val worldVertex = localVertex.prj(block.modelInstance.transform)

            highlightVertices[i * 3] = worldVertex.x
            highlightVertices[i * 3 + 1] = worldVertex.y
            highlightVertices[i * 3 + 2] = worldVertex.z
        }

        highlightedMesh?.setVertices(highlightVertices)
    }

    private fun getLastHitFaceIndices(instance: ModelInstance, ray: Ray): Set<Int> {
        val mesh = instance.model.meshes.first()
        val node = instance.nodes.first()
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
            val faceIndices = mutableSetOf<Int>()
            for (i in hitFaceStartIndex until hitFaceStartIndex + 6) {
                faceIndices.add(indices[i].toInt())
            }
            return faceIndices
        }

        return emptySet()
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

    private fun transformVertex(v: Vector3, instance: ModelInstance, node: Node): Vector3 {
        return v.cpy().mul(node.globalTransform).mul(instance.transform)
    }


    private fun createRectangleHighlight(vertices: List<Vector3>): ModelInstance {
        if (vertices.size != 4) throw IllegalArgumentException("Expected exactly 4 vertices for rectangle highlight!")

        val verticesFlat = FloatArray(4 * 3)

        for (i in 0 until 4) {
            verticesFlat[i * 3] = vertices[i].x
            verticesFlat[i * 3 + 1] = vertices[i].y
            verticesFlat[i * 3 + 2] = vertices[i].z
        }

        val indices = shortArrayOf(
            0, 1, 2,
            2, 3, 0
        )

        val mesh = Mesh(
            true, 4, 6,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position")
        )
        mesh.setVertices(verticesFlat)
        mesh.setIndices(indices)

        val material = Material(ColorAttribute.createDiffuse(Color.RED))

        highlightedModel?.dispose()
        highlightedModel = Model().apply {
            meshes.add(mesh)
            materials.add(material)
            nodes.add(Node().apply {
                id = "rectangle"
                parts.add(NodePart().apply {
                    meshPart = MeshPart("rectangle", mesh, 0, 6, GL20.GL_TRIANGLES)
                    this.material = material
                })
            })
            manageDisposable(mesh)
        }

        highlightedMesh = mesh

        return ModelInstance(highlightedModel)
    }

    private var moveAlongY: Boolean = false
    private var highlightedModel: Model? = null
    private var highlightedTriangleInstance: ModelInstance? = null
    private var lastScreenY = 0
    override fun dispose() {
        highlightedModel?.dispose()
    }

    private fun moveSelectedFace(deltaX: Float, deltaY: Float, deltaZ: Float) {
        val block = selectedBlock ?: return
        val mesh = block.modelInstance.model.meshes.first()

        val vertices = getVerticesForMesh(mesh)
        val posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
        val stride = mesh.vertexSize / 4

        val worldMove = Vector3(deltaX, deltaY, deltaZ)
        // Correct local move: remove translation first
        val transform = block.modelInstance.transform.cpy()
        transform.setTranslation(0f, 0f, 0f) // 🚨 zero out translation
        val localMove = worldMove.prj(transform.inv())

        for (idx in connectedVertices) {
            val baseIdx = idx * stride + posOffset
            vertices[baseIdx] += localMove.x
            vertices[baseIdx + 1] += localMove.y
            vertices[baseIdx + 2] += localMove.z

        }

        mesh.setVertices(vertices)
    }

    fun keyDown(keycode: Int): Boolean {
        if (highlightedTriangleInstance != null && selectedAxis != null) {
            var delta = 0f
            if (keycode == Input.Keys.UP) {
                delta = 1f
            } else if (keycode == Input.Keys.DOWN) {
                delta = -1f
            }

            if (delta != 0f) {
                when (selectedAxis) {
                    Axis.X -> moveSelectedFace(delta, 0f, 0f)
                    Axis.Y -> moveSelectedFace(0f, delta, 0f)
                    Axis.Z -> moveSelectedFace(0f, 0f, delta)
                    else -> {}
                }
                updateHighlightVertices()
                return true
            }
        }

        // Your old ALT key handling
        if (keycode == Input.Keys.ALT_LEFT) {
            moveAlongY = true
            return true
        }
        return false
    }

    fun keyUp(keycode: Int): Boolean {
        if (keycode == Input.Keys.ALT_LEFT) {
            moveAlongY = false
            return true
        }
        return false
    }

    fun keyTyped(character: Char): Boolean {
        when (character.lowercaseChar()) {
            'x' -> {
                selectedAxis = Axis.X
                return true
            }

            'y' -> {
                selectedAxis = Axis.Y
                return true
            }

            'z' -> {
                selectedAxis = Axis.Z
                return true
            }
        }
        return false
    }

}

