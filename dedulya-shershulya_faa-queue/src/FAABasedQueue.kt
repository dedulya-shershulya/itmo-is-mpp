import java.util.concurrent.atomic.*
import kotlin.math.*

/**
 * @author Chkareuli Georgiy
 */
class FAABasedQueue<E> : Queue<E> {

    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    private val first = Segment(0)

    override fun enqueue(element: E) {
        while (true) {
            val i = enqIdx.getAndIncrement()
            val segment = getSegment(i)
            val cellIndex = (i % SEGMENT_SIZE).toInt()
            if (segment.cells.compareAndSet(
                    cellIndex,
                    null,
                    element))
            {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val i = deqIdx.getAndIncrement()
            val segment = getSegment(i)
            val cellIndex = (i % SEGMENT_SIZE).toInt()
            val elem = segment.cells.getAndSet(cellIndex, POISONED)
            if (elem != null && elem != POISONED)
            {
                return elem as E
            }
            if (enqIdx.get() <= i)
            {
                return null
            }
        }
    }

    private fun getSegment(pos: Long): Segment {
        val targetId = pos / SEGMENT_SIZE
        var segment = first
        while (segment.id < targetId) {
            var next = segment.next.get()
            if (next == null) {
                val newSegment = Segment(segment.id + 1)
                next = if (segment.next.compareAndSet(
                        null,
                        newSegment))
                {
                    newSegment
                } else
                {
                    segment.next.get()!!
                }
            }
            segment = next
        }
        return segment
    }
}

private val POISONED = Any()

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

private const val SEGMENT_SIZE = 2