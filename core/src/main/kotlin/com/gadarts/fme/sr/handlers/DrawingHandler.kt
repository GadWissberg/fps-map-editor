package com.gadarts.fme.sr.handlers

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
import com.gadarts.fme.GeneralUtils
import com.gadarts.fme.sr.SceneRenderer.Block
import com.gadarts.fme.sr.SceneRenderer.ScenePlane
import kotlin.math.*

class DrawingHandler(
    private val blocks: MutableList<Block>,
    private val camera: PerspectiveCamera
) : Disposable {
    private val cubeModel = createIndexedCubeModel()
    private val unitCubeVertices: FloatArray = run {
        val mesh = cubeModel.meshes.first()
        FloatArray(mesh.numVertices * mesh.vertexSize / 4).apply(mesh::getVertices)
    }

    private fun resizeMeshXZ(mesh: Mesh, width: Float, depth: Float) {
        val vsize = mesh.vertexSize / 4
        val posOff = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4

        // start from pristine unit-cube data
        val verts = unitCubeVertices.copyOf()

        // vertices 1,2,5,6 have x = 1 → set to width
        for (i in arrayOf(1, 2, 5, 6)) {
            verts[i * vsize + posOff] = width
        }

        // vertices 4,5,6,7 have z = 1 → set to depth
        for (i in arrayOf(4, 5, 6, 7)) {
            verts[i * vsize + posOff + 2] = depth
        }

        mesh.setVertices(verts)
    }

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
        val ray = camera.getPickRay(screenX.toFloat(), screenY.toFloat())

        var closestDist = Float.MAX_VALUE
        val intersection = Vector3()
        val hitNormal = Vector3()
        var placementPos: Vector3? = null            // final spawn cell

        // ───── 1. Ray-cast against every block ───────────────────────────
        blocks.forEach { block ->
            val mesh = block.modelInstance.model.meshes.first()
            val transform = block.modelInstance.transform
            val node = block.modelInstance.nodes.first()

            val idx = GeneralUtils.getIndicesForMesh(mesh)
            val vtx = GeneralUtils.getVerticesForMesh(mesh)
            val off = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
            val stride = mesh.vertexSize / 4

            fun vert(i: Int): Vector3 {
                val b = i * stride + off
                return Vector3(vtx[b], vtx[b + 1], vtx[b + 2])
                    .mul(node.globalTransform).mul(transform)          // world
            }

            for (i in idx.indices step 3) {
                val v1 = vert(idx[i].toInt())
                val v2 = vert(idx[i + 1].toInt())
                val v3 = vert(idx[i + 2].toInt())

                if (Intersector.intersectRayTriangle(ray, v1, v2, v3, intersection)) {
                    val d2 = ray.origin.dst2(intersection)
                    if (d2 < closestDist) {
                        closestDist = d2

                        // axis-aligned normal (±1,0,0) | (0,±1,0) | (0,0,±1)
                        hitNormal.set(axisClamp(v2.cpy().sub(v1).crs(v3.cpy().sub(v1)).nor()))

// ─── bias-snap so we never stay inside the old cube ───
                        val eps = 0.001f                                    // tiny push toward the face
                        fun snap(c: Float, n: Int) = floor(c - eps * n) + n // n = −1 | 0 | +1

                        placementPos = Vector3(
                            snap(intersection.x, hitNormal.x.toInt()),
                            snap(intersection.y, hitNormal.y.toInt()),
                            snap(intersection.z, hitNormal.z.toInt())
                        )
                    }
                }
            }
        }

        // ───── 2. Empty scene fallback: snap to ground (Y = 0) ───────────
        if (placementPos == null) {
            val groundHit = getFirstPlaneIntersection(
                screenX, screenY,
                listOf(ScenePlane(Plane(Vector3.Y, 0f), "ground"))
            )
            placementPos = groundHit?.second?.let { Vector3(floor(it.x), 0f, floor(it.z)) }
        }

        // ───── 3. Spawn the new cube ─────────────────────────────────────
        placementPos?.let { pos ->
            val inst = ModelInstance(cloneModel(cubeModel))
            inst.transform.setTranslation(pos)
            drawingBlock = inst
            initialDrawingPoint.set(pos)
            auxMatrix.set(inst.transform)
        }
    }

    /** returns (±1,0,0) or (0,±1,0) or (0,0,±1) */
    private fun axisClamp(n: Vector3): Vector3 = when {
        abs(n.x) > abs(n.y) && abs(n.x) > abs(n.z) -> Vector3(sign(n.x), 0f, 0f)
        abs(n.y) > abs(n.z) -> Vector3(0f, sign(n.y), 0f)
        else -> Vector3(0f, 0f, sign(n.z))
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
        if (drawingBlock == null) return false

        val hit = getFirstPlaneIntersection(screenX, screenY, scenePlanes) ?: return false
        val (_, p) = hit
        p.set(p.x.toInt().toFloat(), p.y.toInt().toFloat(), p.z.toInt().toFloat())

        val w = max(1f, abs(p.x - initialDrawingPoint.x))
        val d = max(1f, abs(p.z - initialDrawingPoint.z))

        val mesh = drawingBlock!!.model.meshes.first()
        resizeMeshXZ(mesh, w, d)            // 🔥 update vertices

        // keep modelInstance transform identity so grid math stays exact
        drawingBlock!!.transform.idt()
        drawingBlock!!.transform.setTranslation(
            min(p.x, initialDrawingPoint.x),
            initialDrawingPoint.y,                 // ← keep original layer
            min(p.z, initialDrawingPoint.z)
        )
        return true
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
