package com.gadarts.fme

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

class HighlightHandler(private val camera: PerspectiveCamera, private val blocks: MutableList<Block>) : Disposable {
    fun render(modelsBatch: ModelBatch, environment: Environment) {
        if (highlightedTriangleInstance != null) {
            modelsBatch.render(highlightedTriangleInstance, environment)
        }
    }

    fun applySelectFace(screenX: Int, screenY: Int) {
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

    private fun createVerticesFlatArray(vertices: List<Vector3>): FloatArray {
        val verticesFlat = FloatArray(vertices.size * 3)
        vertices.forEachIndexed { idx, v ->
            val i = idx * 3
            verticesFlat[i] = v.x
            verticesFlat[i + 1] = v.y
            verticesFlat[i + 2] = v.z
        }
        return verticesFlat
    }

    private fun createRectangleHighlight(vertices: List<Vector3>): ModelInstance {
        val verticesFlat = createVerticesFlatArray(vertices)
        val mesh = Mesh(
            true, 6, 6,
            VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position")
        )
        mesh.setVertices(verticesFlat)
        mesh.setIndices(shortArrayOf(0, 1, 2, 3, 4, 5))
        val material = Material(ColorAttribute.createDiffuse(Color.RED))
        val nodePart = NodePart().apply {
            this.meshPart = MeshPart("rectangle", mesh, 0, 6, GL20.GL_TRIANGLES)
            this.material = material
        }
        highlightedModel?.dispose()
        highlightedModel = Model().apply {
            meshes.add(mesh)
            materials.add(material)
            nodes.add(Node().apply {
                id = "rectangle"
                parts.add(nodePart)
            }
            )
            manageDisposable(mesh)
        }
        return ModelInstance(highlightedModel)
    }

    private var highlightedModel: Model? = null
    private var highlightedTriangleInstance: ModelInstance? = null
    override fun dispose() {
        highlightedModel?.dispose()
    }

}
