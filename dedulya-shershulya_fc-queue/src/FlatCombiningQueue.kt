import java.util.concurrent.*
import java.util.concurrent.atomic.*

/**
 * @author Chkareuli Georgiy
 */
class FlatCombiningQueue<E> : Queue<E> {
    private val queue = ArrayDeque<E>()
    private val combinerLock = AtomicBoolean(false)
    private val tasksForCombiner = AtomicReferenceArray<Any?>(TASKS_FOR_COMBINER_SIZE)

    override fun enqueue(element: E) {
        var cellIndex = -1
        var isAnnounced = false
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                helpOthers()
                if (isAnnounced) {
                    tasksForCombiner.set(cellIndex, null)
                } else {
                    queue.addLast(element)
                }
                combinerLock.set(false)
                return
            }

            if (isAnnounced) {
                if (tasksForCombiner.get(cellIndex) is Result<*>) {
                    tasksForCombiner.set(cellIndex, null)
                    return
                }
            } else {
                cellIndex = randomCellIndex()
                if (tasksForCombiner.compareAndSet(
                        cellIndex,
                        null,
                        element))
                {
                    isAnnounced = true
                }
            }
        }
    }

    override fun dequeue(): E? {
        var cellIndex = -1
        var isAnnounced = false
        while (true) {
            if (combinerLock.compareAndSet(false, true)) {
                helpOthers()
                val result: E? = if (isAnnounced) {
                    @Suppress("UNCHECKED_CAST")
                    (tasksForCombiner.get(cellIndex) as Result<E?>).also {
                        tasksForCombiner.set(cellIndex, null)
                    }.value
                } else {
                    queue.removeFirstOrNull()
                }
                combinerLock.set(false)
                return result
            }

            if (isAnnounced) {
                val state = tasksForCombiner.get(cellIndex)
                if (state is Result<*>) {
                    tasksForCombiner.set(cellIndex, null)
                    @Suppress("UNCHECKED_CAST")
                    return (state as Result<E?>).value
                }
            } else {
                cellIndex = randomCellIndex()
                if (tasksForCombiner.compareAndSet(
                        cellIndex,
                        null,
                        Dequeue))
                {
                    isAnnounced = true
                }
            }
        }
    }

    private fun helpOthers() {
        for (pos in 0 until tasksForCombiner.length()) {
            when (val task = tasksForCombiner.get(pos)) {
                null, is Result<*> -> continue
                Dequeue -> tasksForCombiner.set(pos, Result(queue.removeFirstOrNull()))
                else -> {
                    @Suppress("UNCHECKED_CAST")
                    queue.addLast(task as E)
                    tasksForCombiner.set(pos, Result(Unit))
                }
            }
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(tasksForCombiner.length())
}

private const val TASKS_FOR_COMBINER_SIZE = 3

private object Dequeue

private class Result<V>(val value: V)