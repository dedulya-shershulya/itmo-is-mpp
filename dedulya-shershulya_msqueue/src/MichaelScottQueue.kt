import java.util.concurrent.atomic.*

/**
 * Implementation of the Michael-Scott queue algorithm.
 *
 * @author : Chkareuli Georgiy
 */
class MichaelScottQueue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    fun enqueue(element: E) {
        val newNode = Node(element)

        while (true) {
            val currentTail = tail.get()
            val tailNext = currentTail.next.get()

            if (currentTail === tail.get()) {
                if (tailNext == null) {
                    if (currentTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(currentTail, newNode)
                        return
                    }
                } else {
                    tail.compareAndSet(currentTail, tailNext)
                }
            }
        }
    }

    fun dequeue(): E? {
        while (true) {
            val currentHead = head.get()
            val currentTail = tail.get()
            val headNext = currentHead.next.get()

            if (currentHead === head.get()) {
                if (currentHead === currentTail) {
                    if (headNext == null) {
                        return null
                    }
                    tail.compareAndSet(currentTail, headNext)
                } else {
                    val value = headNext!!.element
                    if (head.compareAndSet(currentHead, headNext)) {
                        headNext.element = null
                        return value
                    }
                }
            }
        }
    }

    // FOR TEST PURPOSE, DO NOT CHANGE IT.
    fun validate() {
        check(tail.get().next.get() == null) {
            "At the end of the execution, `tail.next` must be `null`"
        }
        check(head.get().element == null) {
            "At the end of the execution, the dummy node shouldn't store an element"
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>>(null)
    }
}
