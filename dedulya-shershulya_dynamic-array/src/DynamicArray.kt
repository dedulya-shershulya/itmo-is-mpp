@file:OptIn(ExperimentalAtomicApi::class)

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.atomicArrayOfNulls

/**
 * @author Chkareuli Georgiy
 */
interface DynamicArray<E> {
    fun get(index: Int): E
    fun put(index: Int, element: E)
    fun pushBack(element: E)
    val size: Int
}

class DynamicArrayImpl<E> : DynamicArray<E> {
    private val core = AtomicReference(Core<E>(INITIAL_CAPACITY, 0))

    override fun get(index: Int): E = core.load().get(index)
    override fun put(index: Int, element: E) = core.load().put(index, element)

    override fun pushBack(element: E) {
        while (true) {
            val c = core.load()

            if (c.pushBack(element)) return

            core.compareAndSet(c, c.migrate())
        }
    }

    override val size: Int get() = core.load().size
}

private class Core<E>(val capacity: Int, initialSize: Int) {
    val array = atomicArrayOfNulls<Node<E>>(capacity)

    private val _size = AtomicInt(initialSize)
    private val _next = AtomicReference<Core<E>?>(null)

    val size: Int get() = _size.load()

    @Suppress("UNCHECKED_CAST")
    fun get(index: Int): E {
        require(index in 0 until size) { "index out of bounds" }

        return when (val n = array.loadAt(index)) {
            is Value<*> -> n.v as E
            is Moving<*> -> n.v as E
            is Moved<*> -> _next.load()!!.get(index)
            else -> throw IllegalStateException("Unexpected state or null")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun put(index: Int, element: E) {
        require(index in 0 until size) { "index out of bounds" }

        while (true) {
            when (val n = array.loadAt(index)) {
                is Value<*> -> {
                    if (array.compareAndSetAt(index, n as Node<E>, Value(element)))
                        return
                }

                is Moving<*> -> {
                    _next.load()!!.array.compareAndSetAt(index, null, Value(n.v as E))
                    array.compareAndSetAt(index, n as Node<E>, Moved())
                }

                is Moved<*> -> {
                    _next.load()!!.put(index, element)
                    return
                }

                else -> throw IllegalStateException("Unexpected state or null")
            }
        }
    }

    fun pushBack(element: E): Boolean {
        while (true) {
            val sz = _size.load()

            if (sz == capacity) return false

            if (array.compareAndSetAt(sz, null, Value(element))) {
                _size.compareAndSet(sz, sz + 1)
                return true
            }

            _size.compareAndSet(sz, sz + 1)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun migrate(): Core<E> {
        val candidate = Core<E>(capacity * 2, size)
        _next.compareAndSet(null, candidate)
        val dst = _next.load()!!

        for (i in 0 until size) {
            while (true) {
                when (val n = array.loadAt(i)) {
                    is Value<*> -> {
                        array.compareAndSetAt(i, n as Node<E>, Moving(n.v as E))
                    }

                    is Moving<*> -> {
                        dst.array.compareAndSetAt(i, null, Value(n.v as E))

                        if (array.compareAndSetAt(i, n as Node<E>, Moved()))
                            break
                    }

                    is Moved<*> -> break

                    else -> throw IllegalStateException("Unexpected state or null")
                }
            }
        }
        return dst
    }
}

private sealed interface Node<out E>
private class Value<E>(val v: E) : Node<E>
private class Moved<E> : Node<E>
private class Moving<E>(val v: E) : Node<E>

private const val INITIAL_CAPACITY = 1