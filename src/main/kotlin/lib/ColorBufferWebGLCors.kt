package org.openrndr.webgl

import WebGLRenderingFixedCompressedTexImage
import kotlinx.coroutines.await
import org.khronos.webgl.*
import org.openrndr.draw.*
import org.openrndr.internal.Driver
import org.openrndr.shape.IntRectangle
import org.openrndr.utils.buffer.MPPBuffer
import org.w3c.dom.Image
import kotlin.js.Promise
import kotlin.math.log2
import WebGL2RenderingContext
import org.khronos.webgl.Float32Array
import org.openrndr.color.ColorRGBa
import org.khronos.webgl.WebGLRenderingContext as GL


internal fun promImage(url: String): Promise<Image> {
    return Promise<Image>() { resolve, _ ->
        val image = Image()
        image.addEventListener("load", {
            resolve(image)

        }, false)
        image.crossOrigin = ""
        image.src = url
    }
}


class ColorBufferWebGLCors(
    val context: WebGL2RenderingContext,
    val target: Int,
    val texture: WebGLTexture,
    override val width: Int,
    override val height: Int,
    override val contentScale: Double,
    override val format: ColorFormat,
    override val type: ColorType,
    override val levels: Int,
    override val multisample: BufferMultisample,
    override val session: Session?

) : ColorBuffer() {

    companion object {
        fun create(
            context: WebGL2RenderingContext,
            width: Int,
            height: Int,
            contentScale: Double = 1.0,
            format: ColorFormat = ColorFormat.RGBa,
            type: ColorType = ColorType.UINT8,
            multisample: BufferMultisample,
            levels: Int,
            session: Session?
        ): ColorBufferWebGLCors {
            if (type == ColorType.FLOAT16) {
                require((Driver.instance as DriverWebGL).capabilities.halfFloatTextures) {
                    """no support for half float textures."""
                }
            }
            if (type == ColorType.FLOAT32) {
                require((Driver.instance as DriverWebGL).capabilities.floatTextures) {
                    """no support for float textures"""
                }
            }
            val texture = context.createTexture() ?: error("failed to create texture")
            context.activeTexture(GL.TEXTURE0)
            when (multisample) {
                BufferMultisample.Disabled -> context.bindTexture(GL.TEXTURE_2D, texture)
                is BufferMultisample.SampleCount -> error("multisample not supported on WebGL(1)")
            }
            val effectiveWidth = (width * contentScale).toInt()
            val effectiveHeight = (height * contentScale).toInt()
            if (levels > 1) {
                //context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAX_LEVEL, levels - 1)
            }
            val (internalFormat, glformat, gltype) = internalFormat2(format, type)

            if (!type.compressed) {
                for (level in 0 until levels) {
                    val div = 1 shl level
                    context.texImage2D(
                        GL.TEXTURE_2D,
                        level,
                        internalFormat,
                        effectiveWidth / div,
                        effectiveHeight / div,
                        0,
                        glformat,
                        gltype,
                        null
                    )
                    context.checkErrors("texture creation failed: $type $format")
                }
            } else {
                for (level in 0 until levels) {
                    val div = 1 shl level
                    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                    val fcontext = context as? WebGLRenderingFixedCompressedTexImage ?: error("cast failed")
                    fcontext.compressedTexImage2D(
                        GL.TEXTURE_2D,
                        level,
                        internalFormat,
                        effectiveWidth / div,
                        effectiveHeight / div,
                        0,
                        null
                    )
                }
            }

            val caps = (Driver.instance as DriverWebGL).capabilities
            if (type == ColorType.UINT8 ||
                (type == ColorType.FLOAT16 && caps.halfFloatTexturesLinear) ||
                (type == ColorType.FLOAT32 && caps.floatTexturesLinear)
            ) {
                context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
                context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
            } else {
                context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.NEAREST)
                context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.NEAREST)
            }
            context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
            context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
            return ColorBufferWebGLCors(
                context,
                GL.TEXTURE_2D,
                texture,
                width,
                height,
                contentScale,
                format,
                type,
                levels,
                multisample,
                session
            )
        }

        fun fromImage(context: WebGL2RenderingContext, image: Image, session: Session? = Session.active): ColorBufferWebGLCors {
            val texture = context.createTexture() ?: error("failed to create texture")
            context.activeTexture(GL.TEXTURE0)
            context.bindTexture(GL.TEXTURE_2D, texture)
            context.texImage2D(GL.TEXTURE_2D, 0, GL.RGBA, GL.RGBA, GL.UNSIGNED_BYTE, image)
            if (log2(image.width.toDouble()) % 1.0 == 0.0 && log2(image.height.toDouble()) % 1.0 == 0.0) {
                context.generateMipmap(GL.TEXTURE_2D)
            }
            context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
            context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
            context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE)
            context.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE)
            return ColorBufferWebGLCors(
                context, GL.TEXTURE_2D, texture, image.width, image.height, 1.0,
                ColorFormat.RGBa, ColorType.UINT8, 1, BufferMultisample.Disabled, session
            )
        }

        @Suppress("UNUSED_PARAMETER")
        fun fromUrl(context: GL, url: String, session: Session? = Session.active): ColorBufferWebGLCors {
            error("use fromUrlSuspend")
            //val image = promiseImage(url).await()
            //return fromImage(context, image, session)
        }

        suspend fun fromUrlSuspend(context: WebGL2RenderingContext, url: String, session: Session? = Session.active): ColorBufferWebGLCors {
            val image = promImage(url).await()
            return fromImage(context, image, session)
        }

    }

    override fun destroy() {
        context.deleteTexture(texture)
    }

    override fun bind(unit: Int) {
        context.checkErrors("pre-existing errors")
        context.activeTexture(unit + GL.TEXTURE0)
        context.bindTexture(target, texture)
        context.checkErrors("bindTexture unit:$unit $this")
    }

    override fun generateMipmaps() {
        context.checkErrors("pre-existing errors")
        bind(0)
        context.generateMipmap(target)
        context.checkErrors("generateMipmap $this")
    }

    override var anisotropy: Double
        get() = TODO("Not yet implemented")
        set(_) {}

    override var flipV: Boolean = false

    override fun copyTo(target: ColorBuffer, fromLevel: Int, toLevel: Int, filter: MagnifyingFilter) {
        val sourceRectangle: IntRectangle = IntRectangle(
            0,
            0,
            this.effectiveWidth / (1 shl fromLevel),
            this.effectiveHeight / (1 shl fromLevel)
        )
        val targetRectangle: IntRectangle = IntRectangle(
            0,
            0,
            sourceRectangle.width,
            sourceRectangle.height
        )
        copyTo(target, fromLevel, toLevel, sourceRectangle, targetRectangle, filter)
    }

    fun bound(f: ColorBufferWebGLCors.() -> Unit) {
        context.activeTexture(GL.TEXTURE0)
        val current = context.getParameter(GL.TEXTURE_BINDING_2D) as WebGLTexture?
        context.bindTexture(target, texture)
        this.f()
        context.bindTexture(target, current)
    }

    override fun copyTo(
        target: ColorBuffer,
        fromLevel: Int,
        toLevel: Int,
        sourceRectangle: IntRectangle,
        targetRectangle: IntRectangle,
        filter: MagnifyingFilter
    ) {
        val fromDiv = 1 shl fromLevel
        val toDiv = 1 shl toLevel
        val refRectangle = IntRectangle(0, 0, effectiveWidth / fromDiv, effectiveHeight / fromDiv)

        val useTexSubImage =false
        //target.type.compressed || (refRectangle == sourceRectangle && refRectangle == targetRectangle && multisample == target.multisample)

        if (!useTexSubImage) {
            val readTarget = renderTarget(
                width / fromDiv,
                height / fromDiv,
                contentScale,
                multisample = multisample
            ) {
                colorBuffer(this@ColorBufferWebGLCors, fromLevel)
            } as RenderTargetWebGL

            val writeTarget = renderTarget(
                target.width / toDiv,
                target.height / toDiv,
                target.contentScale,
                multisample = target.multisample
            ) {
                colorBuffer(target, toLevel)
            } as RenderTargetWebGL

            writeTarget.bind()
            context.bindFramebuffer(WebGL2RenderingContext.READ_FRAMEBUFFER, readTarget.framebuffer)
            context.checkErrors("bindFrameBuffer $this $target")

            val ssx = sourceRectangle.x
            val ssy = sourceRectangle.y
            val sex = sourceRectangle.width + ssx
            val sey = sourceRectangle.height + ssy

            val tsx = targetRectangle.x
            val tsy = targetRectangle.y
            val tex = targetRectangle.width + tsx
            val tey = targetRectangle.height + tsy

            fun sflip(y: Int): Int {
                return this.effectiveHeight / fromDiv - y
            }

            fun tflip(y: Int): Int {
                return target.effectiveHeight / toDiv - y
            }

            context.blitFramebuffer(
                ssx,
                sflip(ssy),
                sex,
                sflip(sey),
                tsx,
                tflip(tsy),
                tex,
                tflip(tey),
                GL.COLOR_BUFFER_BIT,
                filter.toGLFilter2()
            )
            context.bindFramebuffer(WebGL2RenderingContext.READ_FRAMEBUFFER, null)
            context.checkErrors("blitFramebuffer $this $target")
            writeTarget.unbind()

            writeTarget.detachColorAttachments()
            writeTarget.destroy()

            readTarget.detachColorAttachments()
            readTarget.destroy()
        } else {
            require(sourceRectangle == refRectangle && targetRectangle == refRectangle) {
                "cropped or scaled copyTo is not allowed with the selected color buffers: ${this} -> ${target}"
            }

            val useFrameBufferCopy =
                true // Driver.glVersion < DriverVersionGL.VERSION_4_3 || (type != target.type || format != target.format)

            if (useFrameBufferCopy) {
//                checkDestroyed()
                val readTarget = renderTarget(width / fromDiv, height / fromDiv, contentScale) {
                    colorBuffer(this@ColorBufferWebGLCors, fromLevel)
                } as RenderTargetWebGL

                target as ColorBufferWebGLCors
                readTarget.bind()
                context.readBuffer(GL.COLOR_ATTACHMENT0)
                context.checkErrors("readBuffer $this $target")
                target.bound {
                    context.copyTexSubImage2D(
                        target.target,
                        toLevel,
                        0,
                        0,
                        0,
                        0,
                        target.effectiveWidth / toDiv,
                        target.effectiveHeight / toDiv
                    )
                    context.checkErrors("copyTexSubImage2D $this $target")
//                    debugGLErrors() {
//                        when (it) {
//                            GL_INVALID_VALUE -> "level ($toLevel) less than 0, effective target is GL_TEXTURE_RECTANGLE (${target.target == GL_TEXTURE_RECTANGLE} and level is not 0"
//                            else -> null
//                        }
//                    }
                }
                readTarget.unbind()

                readTarget.detachColorAttachments()
                readTarget.destroy()
            }
        }
    }

    override fun copyTo(target: ArrayTexture, layer: Int, fromLevel: Int, toLevel: Int) {
        TODO("Not yet implemented")
    }

    override fun write(
        source: TexImageSource,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        level:Int
    ) {
        require(!type.compressed)
        bind(0)
        context.pixelStorei(GL.UNPACK_FLIP_Y_WEBGL, 1)
        this.context.texSubImage2D(target, level, x, y, GL.RGBA, GL.UNSIGNED_BYTE, source)
    }

    override fun write(
        source: ArrayBufferView,
        sourceFormat: ColorFormat,
        sourceType: ColorType,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        level: Int
    ) {
        bind(0)
        context.pixelStorei(GL.UNPACK_FLIP_Y_WEBGL, 1)

        if (!sourceType.compressed) {
            this.context.texSubImage2D(
                target,
                level,
                x,
                y,
                width,
                height,
                sourceFormat.glFormat2(),
                sourceType.glType2(),
                source
            )
        } else {
            this.context.compressedTexSubImage2D(
                target,
                level,
                x,
                y,
                width,
                height,
                sourceType.glType2(),
                source
            )
        }
    }

    override fun write(
        sourceBuffer: MPPBuffer,
        sourceFormat: ColorFormat,
        sourceType: ColorType,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        level: Int
    ) {
        write(sourceBuffer.dataView, sourceFormat, sourceType, x, y, width, height, level)
    }

    private val readFrameBuffer by lazy {
        context.createFramebuffer() ?: error("failed to create framebuffer")
    }

    override fun read(target: ArrayBufferView, x: Int, y: Int, width: Int, height: Int, level: Int) {
        bind(0)
        val current = context.getParameter(GL.FRAMEBUFFER_BINDING) as WebGLFramebuffer?
        context.bindFramebuffer(GL.FRAMEBUFFER, readFrameBuffer)
        context.framebufferTexture2D(GL.FRAMEBUFFER, GL.COLOR_ATTACHMENT0, this.target, texture,0)
        context.readPixels(x, y, effectiveWidth, effectiveHeight, GL.RGBA, GL.UNSIGNED_BYTE, target)
        context.bindFramebuffer(GL.FRAMEBUFFER, current)
    }

    override fun filter(filterMin: MinifyingFilter, filterMag: MagnifyingFilter) {
        bind(0)
        context.texParameteri(target, GL.TEXTURE_MIN_FILTER, filterMin.toGLFilter2())
        context.texParameteri(target, GL.TEXTURE_MAG_FILTER, filterMag.toGLFilter2())
    }

    override var wrapU: WrapMode
        get() = TODO("Not yet implemented")
        set(_) {}
    override var wrapV: WrapMode
        get() = TODO("Not yet implemented")
        set(_) {}

    override fun fill(color: ColorRGBa) {
        val writeTarget = renderTarget(width, height, contentScale) {
            colorBuffer(this@ColorBufferWebGLCors)
        } as RenderTargetWebGL

        writeTarget.bind()
        val floatColorData = float32Array2(color.r.toFloat(), color.g.toFloat(), color.b.toFloat(), color.alpha.toFloat())
        context.clearBufferfv(WebGL2RenderingContext.COLOR, 0, floatColorData)
        writeTarget.unbind()

        writeTarget.detachColorAttachments()
        writeTarget.destroy()
    }

    override fun toString(): String {
        return "ColorBufferWebGL(target=$target, width=$width, height=$height, contentScale=$contentScale, format=$format, type=$type, levels=$levels, multisample=$multisample, flipV=$flipV)"
    }
}

internal fun MinifyingFilter.toGLFilter2(): Int {
    return when (this) {
        MinifyingFilter.NEAREST -> GL.NEAREST
        MinifyingFilter.LINEAR -> GL.LINEAR
        MinifyingFilter.LINEAR_MIPMAP_LINEAR -> GL.LINEAR_MIPMAP_LINEAR
        MinifyingFilter.LINEAR_MIPMAP_NEAREST -> GL.LINEAR_MIPMAP_NEAREST
        MinifyingFilter.NEAREST_MIPMAP_LINEAR -> GL.NEAREST_MIPMAP_LINEAR
        MinifyingFilter.NEAREST_MIPMAP_NEAREST -> GL.NEAREST_MIPMAP_NEAREST
    }
}

internal fun MagnifyingFilter.toGLFilter2(): Int {
    return when (this) {
        MagnifyingFilter.NEAREST -> GL.NEAREST
        MagnifyingFilter.LINEAR -> GL.LINEAR
    }
}

internal fun float32Array2(vararg floats: Float): Float32Array {
    return Float32Array(floats.toTypedArray())
}

internal data class ConversionEntry2(val format: ColorFormat, val type: ColorType,
                                     val glInternalFormat: Int,
                                     val glFormat: Int,
                                     val glType: Int
)

internal fun internalFormat2(format: ColorFormat, type: ColorType): Triple<Int, Int, Int> {
    val entries = arrayOf(
        ConversionEntry2(ColorFormat.R, ColorType.UINT8, WebGL2RenderingContext.R8, WebGL2RenderingContext.RED, GL.UNSIGNED_BYTE),
        ConversionEntry2(ColorFormat.RG, ColorType.UINT8, WebGL2RenderingContext.RG8, WebGL2RenderingContext.RG, GL.UNSIGNED_BYTE),
        ConversionEntry2(ColorFormat.RGB, ColorType.UINT8, GL.RGB, GL.RGB, GL.UNSIGNED_BYTE),
        ConversionEntry2(ColorFormat.RGBa, ColorType.UINT8, GL.RGBA, GL.RGBA, GL.UNSIGNED_BYTE),
        ConversionEntry2(ColorFormat.R, ColorType.FLOAT16, WebGL2RenderingContext.R16F, WebGL2RenderingContext.RED, WebGL2RenderingContext.HALF_FLOAT),
        ConversionEntry2(ColorFormat.RG, ColorType.FLOAT16, WebGL2RenderingContext.RG16F, WebGL2RenderingContext.RG, WebGL2RenderingContext.HALF_FLOAT),
        ConversionEntry2(ColorFormat.RGB, ColorType.FLOAT16, WebGL2RenderingContext.RGB16F, GL.RGB, WebGL2RenderingContext.HALF_FLOAT),
        ConversionEntry2(ColorFormat.RGBa, ColorType.FLOAT16, WebGL2RenderingContext.RGBA16F, GL.RGBA,  WebGL2RenderingContext.HALF_FLOAT),
        ConversionEntry2(ColorFormat.R, ColorType.FLOAT32, WebGL2RenderingContext.R16F, WebGL2RenderingContext.RED, GL.FLOAT),
        ConversionEntry2(ColorFormat.RG, ColorType.FLOAT32, WebGL2RenderingContext.RG16F, WebGL2RenderingContext.RG, GL.FLOAT),
        ConversionEntry2(ColorFormat.RGB, ColorType.FLOAT32, WebGL2RenderingContext.RGB32F, GL.RGB, GL.FLOAT),
        ConversionEntry2(ColorFormat.RGBa, ColorType.FLOAT32,WebGL2RenderingContext.RGBA32F, GL.RGBA, GL.FLOAT),
        ConversionEntry2(ColorFormat.RGB, ColorType.DXT1, COMPRESSED_RGB_S3TC_DXT1_EXT, GL.RGB, 0 ),
        ConversionEntry2(ColorFormat.RGBa, ColorType.DXT1, COMPRESSED_RGBA_S3TC_DXT1_EXT, GL.RGBA, 0),
        ConversionEntry2(ColorFormat.RGBa, ColorType.DXT3, COMPRESSED_RGBA_S3TC_DXT3_EXT, GL.RGBA, 0),
        ConversionEntry2(ColorFormat.RGBa, ColorType.DXT5, COMPRESSED_RGBA_S3TC_DXT5_EXT, GL.RGBA, 0)
    )
    for (entry in entries) {
        if (entry.format === format && entry.type === type) {
            return Triple(entry.glInternalFormat, entry.glFormat, entry.glType)
        }
    }
    throw Exception("no conversion entry for $format/$type")
}


internal fun ColorFormat.glFormat2(): Int {
    return when (this) {
        ColorFormat.R -> GL.LUMINANCE
        ColorFormat.RG -> GL.LUMINANCE_ALPHA
        ColorFormat.RGB -> GL.RGB
        ColorFormat.RGBa -> GL.RGBA
        ColorFormat.sRGB -> GL.RGB
        ColorFormat.sRGBa -> GL.RGBA
        ColorFormat.BGR -> error("BGR not supported")
        ColorFormat.BGRa -> error("BGRa not supported")
    }
}

internal fun ColorType.glType2(): Int {
    return when (this) {
        ColorType.UINT8, ColorType.UINT8_INT -> GL.UNSIGNED_BYTE
        ColorType.SINT8_INT -> GL.BYTE
        ColorType.UINT16, ColorType.UINT16_INT -> GL.UNSIGNED_SHORT
        ColorType.SINT16_INT -> GL.SHORT
        ColorType.UINT32_INT -> GL.UNSIGNED_INT
        ColorType.SINT32_INT -> GL.INT
        ColorType.FLOAT16 -> HALF_FLOAT_OES
        ColorType.FLOAT32 -> GL.FLOAT
        ColorType.DXT1, ColorType.DXT3, ColorType.DXT5,
        ColorType.BPTC_UNORM, ColorType.BPTC_FLOAT, ColorType.BPTC_UFLOAT -> throw RuntimeException("gl type of compressed types cannot be queried")
    }
}