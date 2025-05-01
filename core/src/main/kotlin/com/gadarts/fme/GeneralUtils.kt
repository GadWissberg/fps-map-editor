package com.gadarts.fme

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.utils.Disposable
import java.lang.reflect.Field
import java.util.*

object GeneralUtils {
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

    fun <T> disposeObject(instance: T, clazz: Class<T>) {
        val fields = clazz.declaredFields
        Arrays.stream(fields).forEach { field: Field ->
            if (Disposable::class.java.isAssignableFrom(field.type)) {
                field.isAccessible = true

                try {
                    val fieldValue = field[instance]
                    if (fieldValue is Disposable) {
                        fieldValue.dispose()
                    }
                } catch (e: IllegalAccessException) {
                    throw RuntimeException(e)
                }
            }
        }
    }
}
