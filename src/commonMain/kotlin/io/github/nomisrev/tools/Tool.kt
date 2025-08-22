package io.github.nomisrev.tools

import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolResult
import com.xemantic.ai.tool.schema.ArraySchema
import com.xemantic.ai.tool.schema.BooleanSchema
import com.xemantic.ai.tool.schema.IntegerSchema
import com.xemantic.ai.tool.schema.JsonSchema
import com.xemantic.ai.tool.schema.NumberSchema
import com.xemantic.ai.tool.schema.ObjectSchema
import com.xemantic.ai.tool.schema.StringSchema
import com.xemantic.ai.tool.schema.generator.generateSchema
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.StringFormat
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import kotlin.jvm.JvmInline
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import ai.koog.agents.core.tools.Tool as KoogTool

inline fun <reified A, reified B> Tool(
    description: String,
    noinline invoke: suspend (A) -> B
): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Tool<A, B>>> = PropertyDelegateProvider { _, property ->
    ReadOnlyProperty<Any?, Tool<A, B>> { _, _ ->
        val snakeCased =
            property.name
                .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2].lowercase()}" }
                .lowercase()
        Tool(snakeCased, description, serializer<A>(), serializer<B>(), invoke)
    }
}

class Tool<A, B>(
    val name: String,
    val description: String,
    val inputSerializer: KSerializer<A>,
    val outputSerializer: KSerializer<B>,
    val invoke: suspend (input: @Serializable A) -> @Serializable B
)

fun <Input, Output> Tool<Input, Output>.asKoogTool(format: StringFormat):
        KoogTool<GenericKoogTool.SerializedToolArgs<Input>, GenericKoogTool.SerializedResult<Output>> =
    GenericKoogTool(format, this)

class GenericKoogTool<Input, Output>(
    private val format: StringFormat,
    private val tool: Tool<Input, Output>,
) : KoogTool<GenericKoogTool.SerializedToolArgs<Input>,
        GenericKoogTool.SerializedResult<Output>>() {

    override suspend fun execute(args: SerializedToolArgs<Input>): SerializedResult<Output> =
        SerializedResult(tool.invoke(args.value), format, tool.outputSerializer)

    override val argsSerializer: KSerializer<SerializedToolArgs<Input>> =
        when (tool.inputSerializer.descriptor.kind) {
            is PrimitiveKind,
            is StructureKind.LIST,
                -> SerializedToolArgs.serializer(tool.inputSerializer)

            else -> SerializedToolArgsSerializer(tool.inputSerializer)
        }

    @Serializable
    @JvmInline
    value class SerializedToolArgs<Input>(val value: Input) : ToolArgs

    class SerializedResult<Output>(
        val value: Output,
        private val format: StringFormat,
        private val serializer: KSerializer<Output>,
    ) : ToolResult {
        override fun toStringDefault(): String = format.encodeToString(serializer, value)
    }

    fun JsonSchema.toToolParameterType(): ToolParameterType = when (this) {
        is ArraySchema -> ToolParameterType.List(this.items.toToolParameterType())
        is BooleanSchema -> ToolParameterType.Boolean
        is IntegerSchema -> ToolParameterType.Integer
        is NumberSchema -> ToolParameterType.Float
        is ObjectSchema -> ToolParameterType.Object(
            properties = this.properties.orEmpty().map { (name, schema) ->
                ToolParameterDescriptor(
                    name = name,
                    description = "TODO: @LLMDescription",
                    type = schema.toToolParameterType()
                )
            },
            requiredProperties = required.orEmpty(),
            additionalProperties = false,
            additionalPropertiesType = null
        )

        is StringSchema -> ToolParameterType.String
        is JsonSchema.Const -> ToolParameterType.String
        is JsonSchema.Ref -> TODO("Impossible, always resolved")
    }

    override val descriptor: ToolDescriptor
        get() = when (val schema =
            generateSchema(tool.inputSerializer.descriptor, inlineRefs = true, additionalProperties = false)) {
            is ArraySchema -> ToolParameterType.List(schema.items.toToolParameterType())
                .asValueTool(schema.description)

            is BooleanSchema -> ToolParameterType.Boolean.asValueTool(schema.description)
            is NumberSchema -> ToolParameterType.Float.asValueTool(schema.description)
            is JsonSchema.Const, is StringSchema -> ToolParameterType.String.asValueTool(schema.description)
            is IntegerSchema -> ToolParameterType.Integer.asValueTool(schema.description)
            is ObjectSchema -> ToolDescriptor(
                name = tool.inputSerializer.descriptor.serialName,
                description = schema.description ?: "",
                requiredParameters = schema.properties.orEmpty()
                    .filter { schema.required.orEmpty().contains(it.key) }.map {
                        ToolParameterDescriptor(
                            name = it.key,
                            description = it.value.description ?: "",
                            type = it.value.toToolParameterType()
                        )
                    }
            )

            is JsonSchema.Ref -> TODO("Impossible, always resolved")
        }

    private fun ToolParameterType.asValueTool(description: String?) =
        ToolDescriptor(
            name = tool.name,
            description = tool.description,
            requiredParameters = listOf(
                ToolParameterDescriptor(name = "value", description = description ?: "", this)
            )
        )
}

private class SerializedToolArgsSerializer<Input>(
    private val inputSerializer: KSerializer<Input>
) : KSerializer<GenericKoogTool.SerializedToolArgs<Input>> {

    override val descriptor = inputSerializer.descriptor

    override fun serialize(
        encoder: Encoder,
        value: GenericKoogTool.SerializedToolArgs<Input>
    ) {
        encoder.encodeSerializableValue(inputSerializer, value.value)
    }

    override fun deserialize(decoder: Decoder): GenericKoogTool.SerializedToolArgs<Input> {
        val input = decoder.decodeSerializableValue(inputSerializer)
        return GenericKoogTool.SerializedToolArgs(input)
    }
}
