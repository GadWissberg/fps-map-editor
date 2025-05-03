package com.gadarts.fme.utils

import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.math.Vector3

object MeshUtils {
    fun createIndexedCubeModel(): Model {
        // Create mesh manually
        val attributes = VertexAttributes(
            VertexAttribute.Position(),
            VertexAttribute.Normal()
        )

        // Define 8 shared corners
        val vertices = arrayOf(
            Vector3(0f, 0f, 0f), // 0
            Vector3(1f, 0f, 0f), // 1
            Vector3(1f, 1f, 0f), // 2
            Vector3(0f, 1f, 0f), // 3
            Vector3(0f, 0f, 1f), // 4
            Vector3(1f, 0f, 1f), // 5
            Vector3(1f, 1f, 1f), // 6
            Vector3(0f, 1f, 1f)  // 7
        )

        val indices = shortArrayOf(
            // Bottom
            2, 1, 0, 0, 3, 2,
            // Top
            7, 4, 5, 5, 6, 7,
            // Front
            6, 2, 3, 3, 7, 6,
            // Back
            1, 5, 4, 4, 0, 1,
            // Left
            3, 0, 4, 4, 7, 3,
            // Right
            2, 6, 5, 5, 1, 2
        )
        // Calculate flat normals per face first (one normal per vertex later)
        val vertexNormals = Array(vertices.size) { Vector3() }

        for (i in indices.indices step 3) {
            val v1 = vertices[indices[i].toInt()]
            val v2 = vertices[indices[i + 1].toInt()]
            val v3 = vertices[indices[i + 2].toInt()]

            val normal = v2.cpy().sub(v1).crs(v3.cpy().sub(v1)).nor()

            vertexNormals[indices[i].toInt()].add(normal)
            vertexNormals[indices[i + 1].toInt()].add(normal)
            vertexNormals[indices[i + 2].toInt()].add(normal)
        }

        // Normalize all normals
        for (i in vertexNormals.indices) {
            vertexNormals[i].nor()
        }

        // Now flatten vertex and normal into FloatArray
        val vertexData = FloatArray(vertices.size * 6)
        for (i in vertices.indices) {
            vertexData[i * 6 + 0] = vertices[i].x
            vertexData[i * 6 + 1] = vertices[i].y
            vertexData[i * 6 + 2] = vertices[i].z
            vertexData[i * 6 + 3] = vertexNormals[i].x
            vertexData[i * 6 + 4] = vertexNormals[i].y
            vertexData[i * 6 + 5] = vertexNormals[i].z
        }

        val mesh = Mesh(
            true, // static
            vertices.size, indices.size,
            attributes
        )

        mesh.setVertices(vertexData)
        mesh.setIndices(indices)

        // Now wrap mesh into a Model manually
        val model = Model()
        val material = Material(ColorAttribute.createDiffuse(Color.GRAY))

        val meshPart = MeshPart("cube", mesh, 0, indices.size, GL20.GL_TRIANGLES)
        val nodePart = NodePart()
        nodePart.meshPart = meshPart
        nodePart.material = material

        val node = Node()
        node.id = "cube"
        node.parts.add(nodePart)

        model.meshes.add(mesh)
        model.materials.add(material)
        model.nodes.add(node)

        model.manageDisposable(mesh)

        model.calculateTransforms()

        return model
    }

    fun getVerticesForMesh(mesh: Mesh): FloatArray {
        val vertices = FloatArray(mesh.numVertices * mesh.vertexSize / 4)
        mesh.getVertices(vertices)
        return vertices
    }

    fun getIndicesForMesh(mesh: Mesh): ShortArray {
        val indices = ShortArray(mesh.numIndices)
        mesh.getIndices(indices)
        return indices
    }


}
