import java.util.concurrent.atomic.*

/**
 * @author Chkareuli Georgiy
 */
open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val rendezvousSlots = AtomicReferenceArray<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushWithElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushWithElimination(element: E): Boolean {
        val index = (0 until ELIMINATION_ARRAY_SIZE).random()

        if (rendezvousSlots.compareAndSet(
                index,
                CELL_STATE_EMPTY,
                element)) {
            repeat(ELIMINATION_WAIT_CYCLES) {
                if (rendezvousSlots.get(index) === CELL_STATE_RETRIEVED) {
                    rendezvousSlots.compareAndSet(
                        index,
                        CELL_STATE_RETRIEVED,
                        CELL_STATE_EMPTY
                    )
                    return true
                }
            }
            if (rendezvousSlots.compareAndSet(
                    index,
                    element,
                    CELL_STATE_EMPTY)) {
                return false
            } else {
                if (rendezvousSlots.get(index) === CELL_STATE_RETRIEVED) {
                    rendezvousSlots.compareAndSet(
                        index,
                        CELL_STATE_RETRIEVED,
                        CELL_STATE_EMPTY
                    )
                }
                return true
            }
        }
        return false
    }

    override fun pop(): E? = tryPopWithElimination() ?: stack.pop()

    private fun tryPopWithElimination(): E? {
        val index = (0 until ELIMINATION_ARRAY_SIZE).random()
        val slotValue = rendezvousSlots.get(index)

        if (slotValue != null && slotValue !== CELL_STATE_RETRIEVED) {
            @Suppress("UNCHECKED_CAST")
            val item = slotValue as E
            if (rendezvousSlots.compareAndSet(
                    index,
                    item,
                    CELL_STATE_RETRIEVED)) {
                rendezvousSlots.compareAndSet(
                    index,
                    CELL_STATE_RETRIEVED,
                    CELL_STATE_EMPTY
                )
                return item
            }
        }
        return null
    }

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 3
        private const val ELIMINATION_WAIT_CYCLES = 1

        private val CELL_STATE_EMPTY = null

        private val CELL_STATE_RETRIEVED = Any()
    }
}
