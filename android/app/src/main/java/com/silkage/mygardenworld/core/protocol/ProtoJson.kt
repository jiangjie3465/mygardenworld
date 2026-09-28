package com.silkage.mygardenworld.core.protocol

import android.content.Context
import com.google.protobuf.ByteString
import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.google.protobuf.DescriptorProtos.DescriptorProto
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type
import com.google.protobuf.DescriptorProtos.FileDescriptorSet
import com.google.protobuf.WireFormat
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Base64

class ProtoJsonException(message: String) : IllegalArgumentException(message)

/**
 * Canonical protojson for Java Lite messages, driven by the descriptor set
 * that protoc writes next to the generated classes. Output matches the Web
 * policy export (`useProtoFieldName`, `alwaysEmitImplicit`, two-space indent);
 * input accepts proto or JSON field names and rejects unknown fields.
 */
class ProtoJson(descriptorSet: ByteArray) {
    private val messages = HashMap<String, DescriptorProto>()
    private val enums = HashMap<String, EnumDescriptorProto>()

    init {
        val set = FileDescriptorSet.parseFrom(descriptorSet)
        for (file in set.fileList) {
            val prefix = if (file.`package`.isEmpty()) "" else ".${file.`package`}"
            file.messageTypeList.forEach { index(prefix, it) }
            file.enumTypeList.forEach { enums["$prefix.${it.name}"] = it }
        }
    }

    private fun index(prefix: String, message: DescriptorProto) {
        val name = "$prefix.${message.name}"
        messages[name] = message
        message.nestedTypeList.forEach { index(name, it) }
        message.enumTypeList.forEach { enums["$name.${it.name}"] = it }
    }

    /** [typeName] is fully qualified with a leading dot, e.g. `.mygardenworld.v1.Policy`. */
    fun toJson(typeName: String, bytes: ByteArray): String = JsonText.write(decode(message(typeName), CodedInputStream.newInstance(bytes)))

    fun fromJson(typeName: String, text: String): ByteArray {
        val root = JsonText.parse(text) as? Json.Obj ?: throw ProtoJsonException("配置 JSON 顶层必须是对象")
        return encode(message(typeName), root, typeName.substringAfterLast('.'))
    }

    private fun message(typeName: String): DescriptorProto {
        if (typeName.startsWith(".google.protobuf.")) throw ProtoJsonException("不支持的类型 $typeName")
        return messages[typeName] ?: throw ProtoJsonException("未知类型 $typeName")
    }

    // --- binary → JSON ---

    private fun decode(desc: DescriptorProto, input: CodedInputStream): Json.Obj {
        val byNumber = desc.fieldList.associateBy { it.number }
        val values = HashMap<Int, Any>()
        while (true) {
            val tag = input.readTag()
            if (tag == 0) break
            val field = byNumber[WireFormat.getTagFieldNumber(tag)]
            if (field == null) {
                input.skipField(tag)
                continue
            }
            val wireType = WireFormat.getTagWireType(tag)
            when {
                isMap(field) -> {
                    val entry = message(field.typeName)
                    val decoded = decode(entry, CodedInputStream.newInstance(input.readByteArray()))
                    val keyField = entry.fieldList.first { it.number == 1 }
                    val valueField = entry.fieldList.first { it.number == 2 }
                    val key = decoded.fields[keyField.name] ?: defaultValue(keyField)
                    val value = decoded.fields[valueField.name] ?: defaultValue(valueField)
                    @Suppress("UNCHECKED_CAST")
                    (values.getOrPut(field.number) { LinkedHashMap<String, Json>() } as LinkedHashMap<String, Json>)[mapKey(key)] = value
                }
                field.label == Label.LABEL_REPEATED -> {
                    @Suppress("UNCHECKED_CAST")
                    val list = values.getOrPut(field.number) { ArrayList<Json>() } as ArrayList<Json>
                    if (wireType == WireFormat.WIRETYPE_LENGTH_DELIMITED && packable(field)) {
                        val limit = input.pushLimit(input.readRawVarint32())
                        while (input.bytesUntilLimit > 0) list += readScalar(field, input)
                        input.popLimit(limit)
                    } else {
                        list += readScalar(field, input)
                    }
                }
                else -> values[field.number] = readScalar(field, input)
            }
        }
        val out = LinkedHashMap<String, Json>()
        for (field in desc.fieldList) {
            val value = values[field.number]
            when {
                isMap(field) -> {
                    @Suppress("UNCHECKED_CAST")
                    out[field.name] = Json.Obj((value as? LinkedHashMap<String, Json>) ?: LinkedHashMap())
                }
                field.label == Label.LABEL_REPEATED -> {
                    @Suppress("UNCHECKED_CAST")
                    out[field.name] = Json.Arr((value as? List<Json>) ?: emptyList())
                }
                value != null -> out[field.name] = (value as Json)
                !explicitPresence(field) -> out[field.name] = defaultValue(field)
            }
        }
        return Json.Obj(out)
    }

    private fun readScalar(field: FieldDescriptorProto, input: CodedInputStream): Json = when (field.type) {
        Type.TYPE_DOUBLE -> doubleJson(input.readDouble())
        Type.TYPE_FLOAT -> doubleJson(input.readFloat().toDouble())
        Type.TYPE_INT64 -> Json.Str(input.readInt64().toString())
        Type.TYPE_UINT64 -> Json.Str(java.lang.Long.toUnsignedString(input.readUInt64()))
        Type.TYPE_FIXED64 -> Json.Str(java.lang.Long.toUnsignedString(input.readFixed64()))
        Type.TYPE_SFIXED64 -> Json.Str(input.readSFixed64().toString())
        Type.TYPE_SINT64 -> Json.Str(input.readSInt64().toString())
        Type.TYPE_INT32 -> Json.Num(input.readInt32().toString())
        Type.TYPE_UINT32 -> Json.Num(Integer.toUnsignedString(input.readUInt32()))
        Type.TYPE_FIXED32 -> Json.Num(Integer.toUnsignedString(input.readFixed32()))
        Type.TYPE_SFIXED32 -> Json.Num(input.readSFixed32().toString())
        Type.TYPE_SINT32 -> Json.Num(input.readSInt32().toString())
        Type.TYPE_BOOL -> Json.Bool(input.readBool())
        Type.TYPE_STRING -> Json.Str(input.readString())
        Type.TYPE_BYTES -> Json.Str(Base64.getEncoder().encodeToString(input.readByteArray()))
        Type.TYPE_ENUM -> enumJson(field.typeName, input.readEnum())
        Type.TYPE_MESSAGE -> decode(message(field.typeName), CodedInputStream.newInstance(input.readByteArray()))
        else -> throw ProtoJsonException("不支持的字段类型 ${field.name}")
    }

    private fun defaultValue(field: FieldDescriptorProto): Json = when (field.type) {
        Type.TYPE_BOOL -> Json.Bool(false)
        Type.TYPE_STRING, Type.TYPE_BYTES -> Json.Str("")
        Type.TYPE_INT64, Type.TYPE_UINT64, Type.TYPE_FIXED64, Type.TYPE_SFIXED64, Type.TYPE_SINT64 -> Json.Str("0")
        Type.TYPE_ENUM -> enumJson(field.typeName, 0)
        Type.TYPE_MESSAGE -> Json.Obj(LinkedHashMap())
        else -> Json.Num("0")
    }

    private fun enumJson(typeName: String, number: Int): Json =
        enums[typeName]?.valueList?.firstOrNull { it.number == number }?.let { Json.Str(it.name) } ?: Json.Num(number.toString())

    private fun doubleJson(value: Double): Json = when {
        value.isNaN() -> Json.Str("NaN")
        value == Double.POSITIVE_INFINITY -> Json.Str("Infinity")
        value == Double.NEGATIVE_INFINITY -> Json.Str("-Infinity")
        value == Math.floor(value) && Math.abs(value) < 1e15 -> Json.Num(value.toLong().toString())
        else -> Json.Num(value.toString())
    }

    private fun mapKey(key: Json): String = when (key) {
        is Json.Str -> key.value
        is Json.Num -> key.raw
        is Json.Bool -> key.value.toString()
        else -> throw ProtoJsonException("无效的映射键")
    }

    // --- JSON → binary ---

    private fun encode(desc: DescriptorProto, obj: Json.Obj, path: String): ByteArray {
        val byName = HashMap<String, FieldDescriptorProto>()
        desc.fieldList.forEach { field ->
            byName[field.name] = field
            if (field.hasJsonName()) byName[field.jsonName] = field
        }
        val chosen = HashMap<Int, Pair<FieldDescriptorProto, Json>>()
        for ((key, value) in obj.fields) {
            val field = byName[key] ?: throw ProtoJsonException("未知字段 ${fieldPath(path, key)}")
            if (chosen.containsKey(field.number)) throw ProtoJsonException("字段重复 ${fieldPath(path, field.name)}")
            if (value is Json.Null) continue
            chosen[field.number] = field to value
        }
        val buffer = ByteArrayOutputStream()
        val out = CodedOutputStream.newInstance(buffer)
        for ((field, value) in chosen.values.sortedBy { it.first.number }) {
            val here = fieldPath(path, field.name)
            when {
                isMap(field) -> {
                    val entries = value as? Json.Obj ?: throw ProtoJsonException("$here 必须是对象")
                    val entry = message(field.typeName)
                    val keyField = entry.fieldList.first { it.number == 1 }
                    val valueField = entry.fieldList.first { it.number == 2 }
                    for ((k, v) in entries.fields) {
                        if (v is Json.Null) throw ProtoJsonException("$here 的值不能为 null")
                        val entryBuffer = ByteArrayOutputStream()
                        val entryOut = CodedOutputStream.newInstance(entryBuffer)
                        writeScalar(entryOut, keyField, parseMapKey(keyField, k, here), here)
                        writeScalar(entryOut, valueField, v, "$here.$k")
                        entryOut.flush()
                        out.writeByteArray(field.number, entryBuffer.toByteArray())
                    }
                }
                field.label == Label.LABEL_REPEATED -> {
                    val items = value as? Json.Arr ?: throw ProtoJsonException("$here 必须是数组")
                    if (items.items.any { it is Json.Null }) throw ProtoJsonException("$here 不能包含 null")
                    if (packable(field)) {
                        if (items.items.isEmpty()) continue
                        val packed = ByteArrayOutputStream()
                        val packedOut = CodedOutputStream.newInstance(packed)
                        items.items.forEach { writeScalarNoTag(packedOut, field, it, here) }
                        packedOut.flush()
                        out.writeTag(field.number, WireFormat.WIRETYPE_LENGTH_DELIMITED)
                        out.writeUInt32NoTag(packed.size())
                        out.writeRawBytes(packed.toByteArray())
                    } else {
                        items.items.forEach { writeScalar(out, field, it, here) }
                    }
                }
                else -> writeScalar(out, field, value, here)
            }
        }
        out.flush()
        return buffer.toByteArray()
    }

    private fun writeScalar(out: CodedOutputStream, field: FieldDescriptorProto, value: Json, path: String) {
        val n = field.number
        when (field.type) {
            Type.TYPE_STRING -> out.writeString(n, string(value, path))
            Type.TYPE_BYTES -> out.writeBytes(n, ByteString.copyFrom(bytes(value, path)))
            Type.TYPE_MESSAGE -> {
                val obj = value as? Json.Obj ?: throw ProtoJsonException("$path 必须是对象")
                out.writeByteArray(n, encode(message(field.typeName), obj, path))
            }
            else -> {
                out.writeTag(n, wireType(field))
                writeScalarNoTag(out, field, value, path)
            }
        }
    }

    private fun writeScalarNoTag(out: CodedOutputStream, field: FieldDescriptorProto, value: Json, path: String) {
        when (field.type) {
            Type.TYPE_DOUBLE -> out.writeDoubleNoTag(double(value, path))
            Type.TYPE_FLOAT -> out.writeFloatNoTag(double(value, path).toFloat())
            Type.TYPE_INT64 -> out.writeInt64NoTag(integer(value, path, LONG_MIN, LONG_MAX).toLong())
            Type.TYPE_SINT64 -> out.writeSInt64NoTag(integer(value, path, LONG_MIN, LONG_MAX).toLong())
            Type.TYPE_SFIXED64 -> out.writeSFixed64NoTag(integer(value, path, LONG_MIN, LONG_MAX).toLong())
            Type.TYPE_UINT64 -> out.writeUInt64NoTag(integer(value, path, BigInteger.ZERO, ULONG_MAX).toLong())
            Type.TYPE_FIXED64 -> out.writeFixed64NoTag(integer(value, path, BigInteger.ZERO, ULONG_MAX).toLong())
            Type.TYPE_INT32 -> out.writeInt32NoTag(integer(value, path, INT_MIN, INT_MAX).toInt())
            Type.TYPE_SINT32 -> out.writeSInt32NoTag(integer(value, path, INT_MIN, INT_MAX).toInt())
            Type.TYPE_SFIXED32 -> out.writeSFixed32NoTag(integer(value, path, INT_MIN, INT_MAX).toInt())
            Type.TYPE_UINT32 -> out.writeUInt32NoTag(integer(value, path, BigInteger.ZERO, UINT_MAX).toLong().toInt())
            Type.TYPE_FIXED32 -> out.writeFixed32NoTag(integer(value, path, BigInteger.ZERO, UINT_MAX).toLong().toInt())
            Type.TYPE_BOOL -> out.writeBoolNoTag((value as? Json.Bool ?: throw ProtoJsonException("$path 必须是布尔值")).value)
            Type.TYPE_ENUM -> out.writeEnumNoTag(enumNumber(field.typeName, value, path))
            else -> throw ProtoJsonException("$path 类型不支持")
        }
    }

    private fun parseMapKey(field: FieldDescriptorProto, key: String, path: String): Json = when (field.type) {
        Type.TYPE_STRING -> Json.Str(key)
        Type.TYPE_BOOL -> when (key) {
            "true" -> Json.Bool(true)
            "false" -> Json.Bool(false)
            else -> throw ProtoJsonException("$path 的键 $key 必须是 true 或 false")
        }
        else -> Json.Num(key)
    }

    private fun integer(value: Json, path: String, min: BigInteger, max: BigInteger): BigInteger {
        val raw = when (value) {
            is Json.Num -> value.raw
            is Json.Str -> value.value.trim()
            else -> throw ProtoJsonException("$path 必须是整数")
        }
        val parsed = try {
            BigDecimal(raw).toBigIntegerExact()
        } catch (_: ArithmeticException) {
            throw ProtoJsonException("$path 必须是整数")
        } catch (_: NumberFormatException) {
            throw ProtoJsonException("$path 必须是整数")
        }
        if (parsed < min || parsed > max) throw ProtoJsonException("$path 超出取值范围")
        return parsed
    }

    private fun double(value: Json, path: String): Double = when (value) {
        is Json.Num -> value.raw.toDouble()
        is Json.Str -> when (value.value) {
            "NaN" -> Double.NaN
            "Infinity" -> Double.POSITIVE_INFINITY
            "-Infinity" -> Double.NEGATIVE_INFINITY
            else -> value.value.toDoubleOrNull() ?: throw ProtoJsonException("$path 必须是数字")
        }
        else -> throw ProtoJsonException("$path 必须是数字")
    }

    private fun string(value: Json, path: String): String = (value as? Json.Str ?: throw ProtoJsonException("$path 必须是字符串")).value

    private fun bytes(value: Json, path: String): ByteArray {
        val text = string(value, path)
        return runCatching { Base64.getDecoder().decode(text) }.recoverCatching { Base64.getUrlDecoder().decode(text) }
            .getOrElse { throw ProtoJsonException("$path 不是有效的 base64") }
    }

    private fun enumNumber(typeName: String, value: Json, path: String): Int {
        val enum = enums[typeName] ?: throw ProtoJsonException("未知枚举 $typeName")
        return when (value) {
            is Json.Str -> enum.valueList.firstOrNull { it.name == value.value }?.number ?: throw ProtoJsonException("$path 的枚举值 ${value.value} 无效")
            is Json.Num -> integer(value, path, INT_MIN, INT_MAX).toInt()
            else -> throw ProtoJsonException("$path 必须是枚举名称")
        }
    }

    private fun isMap(field: FieldDescriptorProto): Boolean =
        field.label == Label.LABEL_REPEATED && field.type == Type.TYPE_MESSAGE && messages[field.typeName]?.options?.mapEntry == true

    /** Message, oneof and proto3 `optional` fields are omitted when unset. */
    private fun explicitPresence(field: FieldDescriptorProto): Boolean =
        field.type == Type.TYPE_MESSAGE || field.hasOneofIndex() || field.proto3Optional

    private fun packable(field: FieldDescriptorProto): Boolean =
        field.type != Type.TYPE_STRING && field.type != Type.TYPE_BYTES && field.type != Type.TYPE_MESSAGE && field.type != Type.TYPE_GROUP

    private fun wireType(field: FieldDescriptorProto): Int = when (field.type) {
        Type.TYPE_DOUBLE, Type.TYPE_FIXED64, Type.TYPE_SFIXED64 -> WireFormat.WIRETYPE_FIXED64
        Type.TYPE_FLOAT, Type.TYPE_FIXED32, Type.TYPE_SFIXED32 -> WireFormat.WIRETYPE_FIXED32
        else -> WireFormat.WIRETYPE_VARINT
    }

    private fun fieldPath(path: String, name: String): String = "$path.$name"

    companion object {
        private val INT_MIN = BigInteger.valueOf(Int.MIN_VALUE.toLong())
        private val INT_MAX = BigInteger.valueOf(Int.MAX_VALUE.toLong())
        private val UINT_MAX = BigInteger.valueOf(0xFFFF_FFFFL)
        private val LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE)
        private val LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
        private val ULONG_MAX = BigInteger.ONE.shiftLeft(64) - BigInteger.ONE

        /** Descriptor set written by the generateProto Gradle task. */
        fun load(context: Context): ProtoJson = context.assets.open("proto.desc").use { ProtoJson(it.readBytes()) }
    }
}

/** Minimal ordered JSON model; numbers keep their literal text. */
sealed interface Json {
    data class Obj(val fields: LinkedHashMap<String, Json>) : Json
    data class Arr(val items: List<Json>) : Json
    data class Str(val value: String) : Json
    data class Num(val raw: String) : Json
    data class Bool(val value: Boolean) : Json
    data object Null : Json
}

object JsonText {
    private const val MAX_DEPTH = 64

    fun parse(text: String): Json {
        val parser = Parser(text)
        val value = parser.value(0)
        parser.skipWhitespace()
        if (!parser.atEnd()) parser.fail("JSON 末尾有多余内容")
        return value
    }

    /** Pretty-prints like `JSON.stringify(value, null, 2)`. */
    fun write(value: Json): String = StringBuilder().also { write(it, value, 0) }.toString()

    private fun write(out: StringBuilder, value: Json, depth: Int) {
        when (value) {
            is Json.Obj -> {
                if (value.fields.isEmpty()) { out.append("{}"); return }
                out.append("{\n")
                value.fields.entries.forEachIndexed { index, (key, item) ->
                    indent(out, depth + 1)
                    quote(out, key)
                    out.append(": ")
                    write(out, item, depth + 1)
                    if (index < value.fields.size - 1) out.append(',')
                    out.append('\n')
                }
                indent(out, depth)
                out.append('}')
            }
            is Json.Arr -> {
                if (value.items.isEmpty()) { out.append("[]"); return }
                out.append("[\n")
                value.items.forEachIndexed { index, item ->
                    indent(out, depth + 1)
                    write(out, item, depth + 1)
                    if (index < value.items.size - 1) out.append(',')
                    out.append('\n')
                }
                indent(out, depth)
                out.append(']')
            }
            is Json.Str -> quote(out, value.value)
            is Json.Num -> out.append(value.raw)
            is Json.Bool -> out.append(value.value)
            Json.Null -> out.append("null")
        }
    }

    private fun indent(out: StringBuilder, depth: Int) {
        repeat(depth * 2) { out.append(' ') }
    }

    private fun quote(out: StringBuilder, text: String) {
        out.append('"')
        for (c in text) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd() = pos >= text.length

        fun fail(message: String): Nothing = throw ProtoJsonException("$message（位置 $pos）")

        fun skipWhitespace() {
            while (pos < text.length && text[pos] in " \t\r\n\uFEFF") pos++
        }

        fun value(depth: Int): Json {
            if (depth > MAX_DEPTH) fail("JSON 嵌套过深")
            skipWhitespace()
            if (atEnd()) fail("JSON 意外结束")
            return when (val c = text[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> Json.Str(string())
                't' -> literal("true", Json.Bool(true))
                'f' -> literal("false", Json.Bool(false))
                'n' -> literal("null", Json.Null)
                else -> if (c == '-' || c.isDigit()) number() else fail("无法识别的 JSON 内容")
            }
        }

        private fun obj(depth: Int): Json.Obj {
            pos++
            val fields = LinkedHashMap<String, Json>()
            skipWhitespace()
            if (peek('}')) { pos++; return Json.Obj(fields) }
            while (true) {
                skipWhitespace()
                if (!peek('"')) fail("对象键必须是字符串")
                val key = string()
                skipWhitespace()
                expect(':')
                if (fields.containsKey(key)) fail("JSON 键重复：$key")
                fields[key] = value(depth + 1)
                skipWhitespace()
                if (peek(',')) { pos++; continue }
                expect('}')
                return Json.Obj(fields)
            }
        }

        private fun arr(depth: Int): Json.Arr {
            pos++
            val items = ArrayList<Json>()
            skipWhitespace()
            if (peek(']')) { pos++; return Json.Arr(items) }
            while (true) {
                items += value(depth + 1)
                skipWhitespace()
                if (peek(',')) { pos++; continue }
                expect(']')
                return Json.Arr(items)
            }
        }

        private fun string(): String {
            pos++
            val out = StringBuilder()
            while (true) {
                if (atEnd()) fail("字符串未结束")
                val c = text[pos++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        if (atEnd()) fail("字符串未结束")
                        when (val e = text[pos++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (pos + 4 > text.length) fail("无效的 \\u 转义")
                                val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("无效的 \\u 转义")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("无效的转义字符 \\$e")
                        }
                    }
                    c < ' ' -> fail("字符串包含控制字符")
                    else -> out.append(c)
                }
            }
        }

        private fun number(): Json.Num {
            val start = pos
            if (peek('-')) pos++
            if (peek('0')) pos++ else digits()
            if (peek('.')) { pos++; digits() }
            if (peek('e') || peek('E')) {
                pos++
                if (peek('+') || peek('-')) pos++
                digits()
            }
            return Json.Num(text.substring(start, pos))
        }

        private fun digits() {
            val start = pos
            while (pos < text.length && text[pos].isDigit()) pos++
            if (pos == start) fail("无效的数字")
        }

        private fun literal(word: String, value: Json): Json {
            if (!text.startsWith(word, pos)) fail("无法识别的 JSON 内容")
            pos += word.length
            return value
        }

        private fun peek(c: Char) = pos < text.length && text[pos] == c

        private fun expect(c: Char) {
            if (!peek(c)) fail("缺少 '$c'")
            pos++
        }
    }
}
