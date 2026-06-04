import kotlin.concurrent.atomics.*

/**
 * @author Chkareuli Georgiy
 */
@Suppress("UNCHECKED_CAST")
@OptIn(ExperimentalAtomicApi::class)
class AtomicArray<E>(size: Int, initialValue: E) {
    private val a = atomicArrayOfNulls<Any>(size)

    init {
        for (i in 0 until size) a.storeAt(i, initialValue)
    }

    companion object {
        private const val UNDECIDED = 0
        private const val SUCCESS = 1
        private const val FAILED = 2
    }

    abstract inner class Descriptor {
        abstract fun complete()
    }

    inner class RDCSSDescriptor(
        val desc: CAS2Descriptor,
        val dataIndex: Int,
        val expectedData: Any?
    ) : Descriptor() {
        override fun complete() {
            val currentStatus = desc.status.loadAt(0) as Int
            val update = if (currentStatus == UNDECIDED) desc else expectedData
            a.compareAndSetAt(dataIndex, this, update)
        }
    }

    inner class CAS2Descriptor(
        val i1: Int, val e1: Any?, val u1: Any?,
        val i2: Int, val e2: Any?, val u2: Any?
    ) : Descriptor() {
        val status = atomicArrayOfNulls<Any>(1)

        init {
            status.storeAt(0, UNDECIDED)
        }

        override fun complete() {
            var currentStatus = status.loadAt(0) as Int

            while (currentStatus == UNDECIDED) {
                val v2 = a.loadAt(i2)

                if (v2 === this) {
                    status.compareAndSetAt(0, UNDECIDED, SUCCESS)
                }
                else if (v2 == e2) {
                    val rdcss = RDCSSDescriptor(this, i2, e2)

                    if (a.compareAndSetAt(i2, e2, rdcss)) {
                        rdcss.complete()
                    }
                }
                else if (v2 is AtomicArray<*>.Descriptor) {
                    v2.complete()
                }
                else {
                    status.compareAndSetAt(0, UNDECIDED, FAILED)
                }
                currentStatus = status.loadAt(0) as Int
            }

            val success = currentStatus == SUCCESS
            a.compareAndSetAt(i1, this, if (success) u1 else e1)
            a.compareAndSetAt(i2, this, if (success) u2 else e2)
        }
    }

    fun get(index: Int): E {
        while (true) {
            val v = a.loadAt(index)

            if (v is AtomicArray<*>.Descriptor) {
                v.complete()
            }
            else {
                return v as E
            }
        }
    }

    fun set(index: Int, value: E) {
        while (true) {
            val v = a.loadAt(index)

            if (v is AtomicArray<*>.Descriptor) {
                v.complete()
            }
            else {
                if (a.compareAndSetAt(index, v, value))
                    return
            }
        }
    }

    fun cas(index: Int, expected: E, update: E): Boolean {
        while (true) {
            val v = a.loadAt(index)

            if (v is AtomicArray<*>.Descriptor) {
                v.complete()
            }
            else if (v == expected) {
                if (a.compareAndSetAt(index, expected, update))
                    return true
            }
            else {
                return false
            }
        }
    }

    fun cas2(
        index1: Int, expected1: E, update1: E,
        index2: Int, expected2: E, update2: E
    ): Boolean {
        if (index1 == index2) {
            if (expected1 == expected2) {
                return cas(index1, expected1, update2)
            }

            return false
        }

        val desc = if (index1 < index2) {
            CAS2Descriptor(
                index1,
                expected1,
                update1,
                index2,
                expected2,
                update2
            )
        } else {
            CAS2Descriptor(
                index2,
                expected2,
                update2,
                index1,
                expected1,
                update1
            )
        }

        while (true) {
            val v1 = a.loadAt(desc.i1)

            if (v1 is AtomicArray<*>.Descriptor) {
                v1.complete()
                continue
            }

            if (v1 != desc.e1) return false

            if (a.compareAndSetAt(desc.i1, desc.e1, desc)) {
                desc.complete()
                return desc.status.loadAt(0) == SUCCESS
            }
        }
    }
}