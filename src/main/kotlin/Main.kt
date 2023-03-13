import Windows.Data.Json.JsonArray
import Windows.Data.Json.JsonObject
import Windows.Data.Json.JsonValue
import Windows.Storage.StorageFile
import com.github.knk190001.winrtbinding.runtime.WinRT
import java.nio.file.Path
import Windows.AI.MachineLearning.*
import Windows.Data.Text.SelectableWordSegmentsTokenizingHandler
import Windows.Data.Text.SelectableWordsSegmenter
import Windows.Foundation.*
import Windows.Foundation.Collections.IVectorView
import Windows.Foundation.Collections.IVectorView_Single_
import Windows.Graphics.Imaging.BitmapDecoder
import Windows.Media.VideoFrame
import Windows.Storage.FileAccessMode
import com.github.doyaaaaaken.kotlincsv.dsl.context.ExcessFieldsRowBehaviour
import com.github.doyaaaaaken.kotlincsv.dsl.context.InsufficientFieldsRowBehaviour
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.knk190001.winrtbinding.runtime.interfaces.IUnknown
import com.sun.jna.FromNativeContext
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.WinDef
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.io.path.pathString

fun main() = runBlocking {
    WinRT.RoInitialize(0)

    val jsonObject = JsonObject()
    val jsonArray = JsonArray()

    (1..10)
        .map(Int::toDouble)
        .map(JsonValue.Companion::CreateNumberValue)
        .forEach(jsonArray::Append)


    jsonObject.SetNamedValue("array", jsonArray)
    jsonObject.SetNamedValue("string", JsonValue.CreateStringValue("Hello world!"))
    jsonObject.SetNamedValue("number", JsonValue.CreateNumberValue(10.0))
    jsonObject.SetNamedValue("boolean", JsonValue.CreateBooleanValue(true))
    jsonObject.SetNamedValue("null", JsonValue.CreateNullValue())
    jsonObject.SetNamedValue("nestedObject", JsonObject())

    println(jsonObject.Stringify())


    val segmenter = SelectableWordsSegmenter("en-US")
    val handler = SelectableWordSegmentsTokenizingHandler.create { precedingWords, words ->
        val precedingWordsIttr = precedingWords!!.First()
        while (precedingWordsIttr.get_HasCurrent()) {
            println("Preceding: " + precedingWordsIttr.get_Current().get_Text())
            precedingWordsIttr.MoveNext()
        }

        val wordsItr = words!!.First()
        while (wordsItr.get_HasCurrent()) {
            println("Word: " + wordsItr.get_Current().get_Text())
            wordsItr.MoveNext()
        }
    }
    segmenter.Tokenize("Hello World!", WinDef.UINT(0), handler)

    testMl()
}

suspend fun testMl() {
    val cwd = Path.of(System.getProperty("user.dir"))
    val contentPath = cwd.resolve("winml_content")

    val modelPath = contentPath.resolve("SqueezeNet.onnx")
    val model = loadModel(modelPath)

    val imageFile = contentPath.resolve("kitten_224.png")
    val imageFrame = loadImageFile(imageFile)

    val (session, binding) = bindModel(model, imageFrame)
    val results = evaluateModel(session, binding)

    val labels_path = contentPath.resolve("Labels.txt")
    val labels = loadLabels(labels_path)

    printResults(results, labels)
}

fun printResults(results: IVectorView<Float>, labels: Map<Int, List<String>>) {
    results.toArray().zip(labels.entries)
        .map { it.first to it.second.value }
        .sortedBy(Pair<Float?, List<String>>::first)
        .reversed()
        .forEach {
            println("${it.second.joinToString()}: ${it.first!! * 100f}")
        }
}

inline fun <reified T> IVectorView<T>.toArray(): Array<T?> {
    val result = arrayOfNulls<T>(get_Size().toInt())
    for (i in 0 until this.get_Size().toInt()) {
        result[i] = this.GetAt(WinDef.UINT(i.toLong()))
    }
    return result
}

fun loadLabels(labelsPath: Path): Map<Int, List<String>> {
    return csvReader {
        this.excessFieldsRowBehaviour = ExcessFieldsRowBehaviour.TRIM
        insufficientFieldsRowBehaviour = InsufficientFieldsRowBehaviour.EMPTY_STRING
    }
        .readAll(labelsPath.toFile())
        .foldRight(mutableMapOf<Int, List<String>>()) { labels, map ->
            map[labels[0].toInt()] = labels.drop(1)
            map
        }
}

fun evaluateModel(session: LearningModelSession, binding: LearningModelBinding): IVectorView<Float> {
    val results = session.Evaluate(binding, "RunId")
    val outputs = results.get_Outputs()
    println("Outputs: " + outputs.get_Size())
    val kvp = outputs.First().get_Current()
    println("Key: ${kvp.get_Key()}")
    val o = results.get_Outputs().Lookup("softmaxout_1")
    val resultTensor = TensorFloat(o.iUnknown_Ptr)
    return resultTensor.GetAsVectorView()
}

suspend fun bindModel(model: LearningModel, imageFrame: VideoFrame): Pair<LearningModelSession, LearningModelBinding> {
    val device = LearningModelDevice(LearningModelDeviceKind.Default)
    val session = LearningModelSession(model, device)
    val binding = LearningModelBinding(session)
    val imageFeatureValue = ImageFeatureValue.CreateFromVideoFrame(imageFrame)
    binding.Bind("data_0", IUnknown.ABI.makeIUnknown(imageFeatureValue.pointer))
    val shape =
        TensorFloat.CreateFromShapeArrayAndDataArray(
            arrayOf(1L, 1000L, 1L, 1L),
            Array(1000) { 0f }
        )

    binding.Bind("softmaxout_1", IUnknown.ABI.makeIUnknown(shape.pointer))
    return session to binding
}

suspend fun loadImageFile(imageFile: Path): VideoFrame {
    val file = StorageFile.GetFileFromPathAsync(imageFile.pathString).await()
    val stream = file!!.OpenAsync(FileAccessMode.Read).await()!!
    val decoder = BitmapDecoder.CreateAsync(stream).await()
    val softwareBitmap = decoder!!.GetSoftwareBitmapAsync().await()!!
    return VideoFrame.CreateWithSoftwareBitmap(softwareBitmap)
}

suspend inline fun <reified T> IAsyncOperation<T>.await(): T {
    while (get_Status() != AsyncStatus.Completed) {
        if (get_Status() == AsyncStatus.Error || get_Status() == AsyncStatus.Canceled) {
            throw RuntimeException(get_Status().toString())
        }
        yield()
    }

    return this.GetResults()
}


suspend fun StorageFile.Companion.GetFileFromPathSuspend(path: String): StorageFile {
    var status = AsyncStatus.Started
    val async = StorageFile.GetFileFromPathAsync(path)
    val completedHandler = AsyncOperationCompletedHandler_StorageFile_.create { asyncInfo, asyncStatus ->
        status = asyncStatus!!
    }
    async.put_Completed(completedHandler)
    while (status == AsyncStatus.Started) {
        delay(20)
    }
    if (status != AsyncStatus.Completed) {
        throw Error("Error")
    }
    return async.GetResults()
}

fun loadModel(modelPath: Path): LearningModel {
    return LearningModel.LoadFromFilePath(modelPath.pathString)
}
