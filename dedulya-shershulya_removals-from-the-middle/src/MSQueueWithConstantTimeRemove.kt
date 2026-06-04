@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

import java.util.concurrent.atomic.*

/**
 * @author Chkareuli Georgiy
 */
class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        val newNode = Node(element, null)

        while (true) {
            val curTail = tail.get()
            newNode.prev.set(curTail)

            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)

                if (curTail.extractedOrRemoved)
                    curTail.remove()

                return

            } else {
                val tailNext = curTail.next.get()

                if (tailNext != null)
                    tail.compareAndSet(curTail, tailNext)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.get()
            val candidate = currentHead.next.get() ?: return null

            if (head.compareAndSet(currentHead, candidate)) {
                candidate.prev.set(null)

                val nextNode = candidate.next.get()
                nextNode?.prev?.compareAndSet(currentHead, candidate)

                if (candidate.markExtractedOrRemoved()) {
                    val result = candidate.element
                    candidate.element = null
                    return result
                }
            }
        }
    }

    override fun remove(element: E): Boolean {
        var node = head.get()
        while (true) {
            val next = node.next.get()
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    override fun validate() {
        check(head.get().prev.get() == null) { "`head.prev` must be null" }
        check(tail.get().next.get() == null) { "tail.next must be null" }
        var node = head.get()

        while (true) {
            if (node !== head.get() && node !== tail.get())
                check(!node.extractedOrRemoved) { "Removed node with element ${node.element} found in the middle of the queue" }

            val nodeNext = node.next.get()

            if (nodeNext == null) break

            val nodeNextPrev = nodeNext.prev.get()

            check(nodeNextPrev != null) { "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue" }
            check(nodeNextPrev == node) { "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}" }

            node = nodeNext
        }
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next = AtomicReference<Node<E>?>(null)
        val prev = AtomicReference(prev)
        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() = _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        fun remove(): Boolean {
            val logicallyRemoved = markExtractedOrRemoved()
            val left = prev.get()
            val right = next.get()

            if (left == null || right == null)
                return logicallyRemoved

            left.next.compareAndSet(this, right)

            if (right.prev.compareAndSet(this, left)) {
                if (this.prev.get() == null) {
                    right.prev.compareAndSet(left, this)
                }
            }

            if (left.extractedOrRemoved) left.remove()
            if (right.extractedOrRemoved) right.remove()

            return logicallyRemoved
        }
    }
}