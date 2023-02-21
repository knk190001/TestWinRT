import Windows.Data.Json.IJsonValue
import Windows.Data.Json.JsonArray
import Windows.Data.Json.JsonObject
import Windows.Data.Json.JsonValue
import com.github.knk190001.winrtbinding.runtime.WinRT

fun main() {
    WinRT.RoInitialize(0)
    val jo = JsonObject()
    val ja = JsonArray()
    val arr = (1..10).map(Int::toDouble)
        .map(JsonValue.Companion::CreateNumberValue)
        .toTypedArray()
    val na = IJsonValue.makeArray(*arr)


    ja.ReplaceAll(na as Array<IJsonValue?>)

    println(ja.Stringify())

}