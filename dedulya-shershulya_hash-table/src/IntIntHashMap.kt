import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicReference

/**
 * @author Chkareuli Georgiy
 */
class IntIntHashMap {
    private val core = AtomicReference(Core(INITIAL_CAPACITY))

    operator fun get(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(core.get().getInternal(key))
    }

    fun put(key: Int, value: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        require(isValue(value)) { "Invalid value: $value" }
        return toValue(putAndRehashWhileNeeded(key, value))
    }

    fun remove(key: Int): Int {
        require(key > 0) { "Key must be positive: $key" }
        return toValue(putAndRehashWhileNeeded(key, DEL_VALUE))
    }

    private fun putAndRehashWhileNeeded(key: Int, value: Int): Int {
        while (true) {
            val currentCore = core.get()
            val oldValue = currentCore.putInternal(key, value)

            if (oldValue != NEEDS_REHASH) return oldValue

            val nextCore = currentCore.rehash()
            core.compareAndSet(currentCore, nextCore)
        }
    }

    private class Core(capacity: Int) {
        val map = AtomicIntegerArray(2 * capacity)
        val shift: Int
        val next: AtomicReference<Core> = AtomicReference(null)

        init {
            val mask = capacity - 1
            assert(mask > 0 && mask and capacity == 0) { "Capacity must be power of 2: $capacity" }
            shift = 32 - Integer.bitCount(mask)
        }

        fun getInternal(key: Int): Int {
            var index = index(key)
            var probes = 0

            while (true) {
                val currentKey = map.get(index)

                if (currentKey == NULL_KEY) return NULL_VALUE

                if (currentKey == key) {
                    return unmark(map.get(index + 1))
                }

                if (++probes >= MAX_PROBES) return NULL_VALUE

                if (index == 0) index = map.length()

                index -= 2
            }
        }

        fun putInternal(key: Int, value: Int): Int {
            var index = index(key)
            var probes = 0

            while (true) {
                val currentKey = map.get(index)

                if (currentKey == NULL_KEY) {
                    if (value == DEL_VALUE) return NULL_VALUE

                    if (map.compareAndSet(index, NULL_KEY, key)) {
                        break
                    }
                    continue
                }

                if (currentKey == key) break
                if (++probes >= MAX_PROBES) return NEEDS_REHASH
                if (index == 0) index = map.length()

                index -= 2
            }

            while (true) {
                val oldValue = map.get(index + 1)

                if (isMoved(oldValue)) return NEEDS_REHASH

                if (map.compareAndSet(index + 1, oldValue, value)) {
                    return oldValue
                }
            }
        }

        fun rehash(): Core {
            next.compareAndSet(null, Core(map.length()))
            val newCore = next.get()
            var index = 0

            while (index < map.length()) {
                var value = map.get(index + 1)

                while (!isMoved(value)) {
                    map.compareAndSet(index + 1, value, markMoved(value))
                    value = map.get(index + 1)
                }

                val unmarked = unmark(value)

                if (isValue(unmarked)) {
                    newCore.copyInternal(map.get(index), unmarked)
                }

                index += 2
            }
            return newCore
        }

        fun copyInternal(key: Int, value: Int) {
            var index = index(key)
            var probes = 0

            while (true) {
                val currentKey = map.get(index)

                if (currentKey == NULL_KEY) {
                    map.compareAndSet(index, NULL_KEY, key)
                    continue
                }

                if (currentKey == key) {
                    map.compareAndSet(index + 1, NULL_VALUE, value)
                    return
                }

                if (++probes >= MAX_PROBES) {
                    return
                }

                if (index == 0) index = map.length()

                index -= 2
            }
        }

        fun index(key: Int): Int = (key * MAGIC ushr shift) * 2
    }
}

private const val MAGIC = -0x61c88647
private const val INITIAL_CAPACITY = 2
private const val MAX_PROBES = 8
private const val NULL_KEY = 0
private const val NULL_VALUE = 0
private const val DEL_VALUE = Int.MAX_VALUE
private const val NEEDS_REHASH = -1

private fun isMoved(value: Int): Boolean = value < 0
private fun markMoved(value: Int): Int = value or Int.MIN_VALUE
private fun unmark(value: Int): Int = value and Int.MAX_VALUE

private fun isValue(value: Int): Boolean = value in (1 until DEL_VALUE)

private fun toValue(value: Int): Int = if (isValue(value)) value else 0