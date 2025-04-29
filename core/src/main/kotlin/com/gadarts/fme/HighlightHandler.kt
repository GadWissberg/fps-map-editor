package com.gadarts.fme

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.gadarts.fme.GeneralUtils.getVerticesForMesh

class HighlightHandler : Disposable {
    var highlightedTriangleInstance: ModelInstance? = null
    private var highlightedMesh: Mesh? = null
    private var highlightedModel: Model? = null

    fun render(modelsBatch: ModelBatch, environment: Environment) {
        if (highlightedTriangleInstance != null) {
            modelsBatch.render(highlightedTriangleInstance, environment)
        }
    }

    fun highlight(rectangleUniqueVerts: MutableList<Vector3>) {
        highlightedTriangleInstance = createRectangleHighlight(rectangleUniqueVerts)
        highlightedTriangleInstance!!.transform.idt()
    }

    fun reset() {
        highlightedTriangleInstance = null
        highlightedModel?.dispose()
        highlightedModel = null
        highlightedMesh = null
    }

    override fun dispose() {
        highlightedModel?.dispose()
        highlightedMesh?.dispose()
    }

    fun updateHighlightVertices(selectedBlock: SceneRenderer.Block?, selectedFaceIndices: Set<Int>) {
        val block = selectedBlock ?: return
        val mesh = block.modelInstance.model.meshes.first()
        val vertices = getVerticesForMesh(mesh)
        val posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4

        if (selectedFaceIndices.isEmpty()) return

        val uniqueFaceIndices = selectedFaceIndices.distinct()

        val highlightVertices = FloatArray(4 * 3) // 4 vertices, 3 floats each

        for (i in 0 until 4) {
            val idx = uniqueFaceIndices[i]
            val baseIdx = idx * mesh.vertexSize / 4 + posOffset

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


}
