import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/*
   Obstruction-free STM implementation.
   @author : Chkareuli Georgiy
*/

fun <T> atomic(block: TxScope.() -> T): T {
    while (true) {
        val transaction = Transaction()
        try {
            val result = block(transaction)
            if (transaction.commit()) return result
            transaction.abort()
        } catch (e: AbortException) {
            transaction.abort()
        }
    }
}

abstract class TxScope {
    abstract fun <T> TxVar<T>.read(): T
    abstract fun <T> TxVar<T>.write(x: T): T
}

@OptIn(ExperimentalAtomicApi::class)
class TxVar<T>(initial: T)  {
    private val loc = AtomicReference(Loc(initial, initial, rootTx))

    fun openIn(tx: Transaction, update: (T) -> T): T {
        while (true) {
            val curLoc = loc.load()
            val owner = curLoc.owner

            val curValue = when (owner.status) {
                TxStatus.COMMITTED -> curLoc.newValue
                TxStatus.ABORTED -> curLoc.oldValue
                TxStatus.ACTIVE -> {
                    if (owner != tx) {
                        owner.abort()
                        continue
                    } else {
                        curLoc.newValue
                    }
                }
            }

            val newValue = update(curValue)
            val newLoc = Loc(curValue, newValue, tx)

            if (loc.compareAndSet(curLoc, newLoc)) {
                if (tx.status == TxStatus.ABORTED) {
                    throw AbortException
                }
                return newValue
            }
        }
    }
}

private class Loc<T>(
    val oldValue: T,
    val newValue: T,
    val owner: Transaction
)

private val rootTx = Transaction().apply { commit() }

enum class TxStatus { ACTIVE, COMMITTED, ABORTED }

@OptIn(ExperimentalAtomicApi::class)
class Transaction : TxScope() {
    private val _status = AtomicReference(TxStatus.ACTIVE)
    val status: TxStatus get() = _status.load()

    fun commit(): Boolean =
        _status.compareAndSet(TxStatus.ACTIVE, TxStatus.COMMITTED)

    fun abort() {
        _status.compareAndSet(TxStatus.ACTIVE, TxStatus.ABORTED)
    }

    override fun <T> TxVar<T>.read(): T = openIn(this@Transaction) { it }
    override fun <T> TxVar<T>.write(x: T) = openIn(this@Transaction) { x }
}

private object AbortException : Exception() {
    override fun fillInStackTrace(): Throwable = this
}