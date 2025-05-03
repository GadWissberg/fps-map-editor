package com.gadarts.fme.sr.handlers

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.gadarts.fme.sr.AuxiliaryModels
import com.gadarts.fme.sr.SceneRenderer
import com.gadarts.fme.utils.MeshUtils

class RenderingHandler(
    private val blocks: MutableList<SceneRenderer.Block>,
    private val auxiliaryModels: AuxiliaryModels,
    private val drawingHandler: DrawingHandler,
    private val highlightHandler: ModeFacesHandler,
    private val cameraHandler: CameraHandler
) : Disposable {
    private val modelsBatch = ModelBatch()
    private val environment = createEnvironment()
    private val shapeRenderer = ShapeRenderer()
    fun render(screenPosition: Vector2) {
        Gdx.gl.glViewport(
            screenPosition.x.toInt(),
            screenPosition.y.toInt(),
            Gdx.graphics.backBufferWidth,
            Gdx.graphics.backBufferHeight
        )
        modelsBatch.begin(cameraHandler.camera)
        auxiliaryModels.render(modelsBatch)
        blocks.forEach {
            modelsBatch.render(it.modelInstance, environment)
        }
        if (drawingHandler.drawingBlock != null) {
            modelsBatch.render(drawingHandler.drawingBlock, environment)
        }
        highlightHandler.render(modelsBatch, environment)
        modelsBatch.end()
        renderBlockGrids()
    }

    private fun renderBlockGrids() {
        shapeRenderer.projectionMatrix = cameraHandler.camera.combined
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(true)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.DARK_GRAY

        for (block in blocks) {
            val transform = block.modelInstance.transform
            val mesh = block.modelInstance.model.meshes.first()
            val indices = MeshUtils.getIndicesForMesh(mesh)
            val vertices = MeshUtils.getVerticesForMesh(mesh)
            val posOffset = mesh.getVertexAttribute(VertexAttributes.Usage.Position).offset / 4
            val stride = mesh.vertexSize / 4

            val node = block.modelInstance.nodes.first()

            for (i in indices.indices step 6) {
                // Get the 4 unique corner vertices of the rectangle (2 triangles = 1 quad)
                val quadVertices = mutableListOf<Vector3>()
                val used = mutableSetOf<Int>()

                for (j in i until i + 6) {
                    val idx = indices[j].toInt()
                    if (idx !in used) {
                        val base = idx * stride + posOffset
                        val local = Vector3(
                            vertices[base],
                            vertices[base + 1],
                            vertices[base + 2]
                        )
                        val fullTransform = Matrix4(node.globalTransform).mul(transform)
                        val world = local.cpy().mul(fullTransform)
                        quadVertices.add(world)
                        used.add(idx)
                    }
                }

                if (quadVertices.size == 4) {
                    drawGridOnQuad(quadVertices)
                }
            }
        }

        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    private fun drawGridOnQuad(corners: List<Vector3>) {
        val v0 = corners[0]
        val v1 = corners[1]
        val v3 = corners[3]

        val edgeU = v1.cpy().sub(v0)
        val edgeV = v3.cpy().sub(v0)

        val lenU = edgeU.len()
        val lenV = edgeV.len()

        val stepsU = (lenU).toInt()
        val stepsV = (lenV).toInt()

        val stepU = 1f / stepsU.toFloat()
        val stepV = 1f / stepsV.toFloat()

        drawGridLines(stepsU, stepU, v0, edgeU, edgeV)
        drawGridLines(stepsV, stepV, v0, edgeV, edgeU)
    }

    private fun drawGridLines(
        stepsU: Int,
        stepU: Float,
        v0: Vector3,
        edgeU: Vector3,
        edgeV: Vector3?
    ) {
        for (i in 0..stepsU) {
            val u = i * stepU
            val offset = Vector3(cameraHandler.camera.direction).nor().scl(-0.01f)
            val p0 = v0.cpy().add(edgeU.cpy().scl(u)).add(offset)
            val p1 = p0.cpy().add(edgeV).add(offset)
            shapeRenderer.line(p0, p1)
        }
    }

    private fun createEnvironment(): Environment {
        val environment = Environment()
        environment.set(ColorAttribute.createAmbientLight(0.5f, 0.5f, 0.5f, 1f))
        val directionalLight = DirectionalLight()
        directionalLight.set(Color.WHITE, 0.5f, -0.8f, -0.2f)
        environment.add(directionalLight)
        return environment
    }

    override fun dispose() {
        shapeRenderer.dispose()
    }

}
