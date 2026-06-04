import org.jetbrains.lincheck.datastructures.*
import org.junit.*

@Param(name = "index", gen = IntGen::class, conf = "0:5")
class DynamicArrayTest {
    private val q = DynamicArrayImpl<Int>()

    @Operation
    fun get(@Param(name = "index") index: Int) = q.get(index)

    @Operation
    fun put(@Param(name = "index") index: Int, element: Int) = q.put(index, element)

    @Operation
    fun pushBack(@Param(name = "index") element: Int) = q.pushBack(element)

    @Operation
    fun size() = q.size

    @Test
    fun modelCheckingTest() = ModelCheckingOptions()
        .iterations(100)
        .invocationsPerIteration(10_000)
        .threads(3)
        .actorsPerThread(3)
        .checkObstructionFreedom()
        .sequentialSpecification(DynamicArrayIntSequential::class.java)
        .check(this::class.java)

    @Test
    fun stressTest() = StressOptions()
        .iterations(100)
        .invocationsPerIteration(50_000)
        .threads(3)
        .actorsPerThread(3)
        .sequentialSpecification(DynamicArrayIntSequential::class.java)
        .check(this::class.java)
}

class DynamicArrayIntSequential {
    private val array = ArrayList<Int>()

    fun get(index: Int): Int =
        if (index < array.size) array[index]
        else throw IllegalArgumentException()

    fun put(index: Int, element: Int): Unit =
        if (index < array.size) array[index] = element
        else throw IllegalArgumentException()

    fun pushBack(element: Int) {
        array.add(element)
    }

    fun size(): Int = array.size
}