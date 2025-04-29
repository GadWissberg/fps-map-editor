package com.gadarts.fme

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.model.MeshPart
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.model.NodePart
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Plane
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Disposable
import com.gadarts.fme.SceneRenderer.Block
import com.gadarts.fme.SceneRenderer.ScenePlane
import kotlin.math.abs

class DrawingHandler(
    private val blocks: MutableList<Block>,
    private val camera: PerspectiveCamera
) : Disposable {
    private val cubeModel = createIndexedCubeModel()

    private fun createIndexedCubeModel(): Model {
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

    private fun cloneModel(original: Model): Model {
        val clonedModel = Model()

        // Deep clone each mesh
        for (originalMesh in original.meshes) {
            // Create new mesh with same attributes
            val mesh = Mesh(
                true,
                originalMesh.numVertices,
                originalMesh.numIndices,
                originalMesh.vertexAttributes
            )

            // Copy vertex and index data
            val vertices = FloatArray(originalMesh.numVertices * originalMesh.vertexSize / 4)
            originalMesh.getVertices(vertices)
            mesh.setVertices(vertices)

            val indices = ShortArray(originalMesh.numIndices)
            originalMesh.getIndices(indices)
            mesh.setIndices(indices)

            clonedModel.meshes.add(mesh)
            clonedModel.manageDisposable(mesh)
        }

        // Clone materials
        for (originalMaterial in original.materials) {
            val clonedMaterial = Material()
            for (attribute in originalMaterial) {
                clonedMaterial.set(attribute.copy())
            }
            clonedModel.materials.add(clonedMaterial)
        }

        // Clone nodes and create proper references
        for (originalNode in original.nodes) {
            val clonedNode = Node()
            clonedNode.id = originalNode.id
            clonedNode.translation.set(originalNode.translation)
            clonedNode.rotation.set(originalNode.rotation)
            clonedNode.scale.set(originalNode.scale)

            // Clone node parts and set references to new meshes/materials
            for (originalPart in originalNode.parts) {
                val clonedPart = NodePart()

                // Create a new mesh part that references the cloned mesh
                val meshPartIndex = original.meshes.indexOf(originalPart.meshPart.mesh)
                if (meshPartIndex >= 0) {
                    val clonedMesh = clonedModel.meshes.get(meshPartIndex)
                    clonedPart.meshPart = MeshPart(
                        originalPart.meshPart.id,
                        clonedMesh,
                        originalPart.meshPart.offset,
                        originalPart.meshPart.size,
                        originalPart.meshPart.primitiveType
                    )
                }

                // Set material reference
                val materialIndex = original.materials.indexOf(originalPart.material)
                if (materialIndex >= 0) {
                    clonedPart.material = clonedModel.materials.get(materialIndex)
                }

                clonedNode.parts.add(clonedPart)
            }

            clonedModel.nodes.add(clonedNode)
        }

        clonedModel.calculateTransforms()

        return clonedModel
    }

    fun applyCreate(screenX: Int, screenY: Int) {
        val relativeX = screenX.toFloat()
        val relativeY = screenY.toFloat()
        camera.getPickRay(relativeX, relativeY)
        val hit = getFirstPlaneIntersection(screenX, screenY, scenePlanes)
        if (hit != null) {
            val (_, point) = hit
            point.set(point.x.toInt().toFloat(), point.y.toInt().toFloat(), point.z.toInt().toFloat())
            val modelCopy = cloneModel(cubeModel)
            val modelInstance = ModelInstance(modelCopy)
            modelInstance.transform.setTranslation(point)
            drawingBlock = modelInstance
            initialDrawingPoint.set(point)
            auxMatrix.set(drawingBlock!!.transform)
        }

    }

    private val scenePlanes = listOf(
        ScenePlane(Plane(Vector3.Y, 0f), "floor"),
        ScenePlane(Plane(Vector3.Y.cpy().scl(-1f), WORLD_SIZE), "ceiling"),
        ScenePlane(Plane(Vector3.X, 0F), "leftWall"),
        ScenePlane(Plane(Vector3.X.cpy().scl(-1f), WORLD_SIZE), "rightWall"),
        ScenePlane(Plane(Vector3.Z, 0F), "backWall"),
        ScenePlane(Plane(Vector3.Z.cpy().scl(-1f), WORLD_SIZE), "frontWall")
    )


    fun touchUp(): Boolean {
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

    fun touchDragged(screenX: Int, screenY: Int): Boolean {
        if (drawingBlock != null) {
            val relativeX = screenX.toFloat()
            val relativeY = screenY.toFloat()

            camera.getPickRay(relativeX, relativeY)
            val hit = getFirstPlaneIntersection(screenX, screenY, scenePlanes)
            if (hit != null) {
                val (_, point) = hit
                point.set(point.x.toInt().toFloat(), point.y.toInt().toFloat(), point.z.toInt().toFloat())

                // Get scaling values, keeping your original logic
                val scaleX = if (abs(point.x - initialDrawingPoint.x) > 1F) point.x - initialDrawingPoint.x else 1F
                val scaleZ = if (abs(point.z - initialDrawingPoint.z) > 1F) point.z - initialDrawingPoint.z else 1F

                drawingBlock!!.transform.set(auxMatrix)
                drawingBlock!!.transform.scl(scaleX, 1F, scaleZ)

                // Check if we need to flip face culling
                if (scaleX < 0 || scaleZ < 0) {
                    // Toggle face culling
                    Gdx.gl.glFrontFace(GL20.GL_CW) // Change to clockwise (normally it's GL_CCW)
                } else {
                    // Reset to default
                    Gdx.gl.glFrontFace(GL20.GL_CCW) // Counter-clockwise is the default
                }

                return true
            }
        }
        return false
    }


    private fun getFirstPlaneIntersection(
        screenX: Int,
        screenY: Int,
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

    private val initialDrawingPoint = Vector3()
    var drawingBlock: ModelInstance? = null

    companion object {
        private val auxMatrix = Matrix4()
        private const val WORLD_SIZE = 40F

    }

    override fun dispose() {
        cubeModel.dispose()
    }
}
