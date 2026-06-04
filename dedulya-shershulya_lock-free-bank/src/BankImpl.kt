import kotlin.concurrent.atomics.AtomicArray
import kotlin.concurrent.atomics.ExperimentalAtomicApi
/*
    @author: Chkareuli Georgiy
 */
@OptIn(ExperimentalAtomicApi::class)
class BankImpl(override val accountsCount: Int) : Bank {
    private val accounts = AtomicArray<Account>(accountsCount) { Account(0) }

    private fun account(id: Int) = accounts.loadAt(id)

    override fun amount(id: Int): Long {
        while (true) {
            val account = account(id)
            if (!account.invokeOperation()) return account.amount
        }
    }

    override val totalAmount: Long
        get() {
            val op = TotalAmountOp()
            op.invokeOperation()
            return op.sum
        }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        check(amount <= MAX_AMOUNT) { "Overflow" }

        while (true) {
            val account = account(id)
            if (account.invokeOperation()) continue
            check(account.amount + amount <= MAX_AMOUNT) { "Overflow" }
            val updated = Account(account.amount + amount)
            if (accounts.compareAndSetAt(id, account, updated))
                return updated.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }

        while (true) {
            val account = account(id)

            if (account.invokeOperation()) continue

            check(account.amount - amount >= 0) { "Underflow" }

            val updated = Account(account.amount - amount)
            if (accounts.compareAndSetAt(id, account, updated))
                return updated.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }
        check(amount <= MAX_AMOUNT) { "Underflow/overflow" }

        val op = TransferOp(fromId, toId, amount)
        op.invokeOperation()
        op.errorMessage?.let { error(it) }
    }

    private fun acquire(id: Int, op: Op): AcquiredAccount? {
        while (true) {
            val account = account(id)
            if (op.completed) return null

            if (account is AcquiredAccount && account.op === op)
                return account

            if (account.invokeOperation()) continue

            val acquiredAccount = AcquiredAccount(account.amount, op)
            if (accounts.compareAndSetAt(id, account, acquiredAccount))
                return acquiredAccount
        }
    }

    private fun release(id: Int, op: Op) {
        assert(op.completed)
        val account = account(id)
        if (account is AcquiredAccount && account.op === op) {
            val updated = Account(account.newAmount)
            accounts.compareAndSetAt(id, account, updated)
        }
    }

    private open class Account(val amount: Long) {
        open fun invokeOperation(): Boolean = false
    }

    private class AcquiredAccount(
        var newAmount: Long,
        val op: Op
    ) : Account(newAmount) {
        override fun invokeOperation(): Boolean {
            op.invokeOperation()
            return true
        }
    }

    private abstract class Op {
        @Volatile
        var completed = false

        abstract fun invokeOperation()
    }

    private inner class TotalAmountOp : Op() {
        var sum = 0L

        override fun invokeOperation() {
            var sum = 0L
            var acquired = 0
            while (acquired < accountsCount) {
                val account = acquire(acquired, this) ?: break
                sum += account.newAmount
                acquired++
            }
            if (acquired == accountsCount) {
                this.sum = sum
                completed = true
            }
            for (i in 0 until accountsCount) {
                release(i, this)
            }
        }
    }

    private inner class TransferOp(val fromId: Int, val toId: Int, val amount: Long) : Op() {
        var errorMessage: String? = null

        override fun invokeOperation() {
            val firstId = if (fromId < toId) fromId else toId
            val secondId = if (fromId < toId) toId else fromId

            val firstAccount = acquire(firstId, this)
            if (firstAccount == null) {
                release(firstId, this)
                release(secondId, this)
                return
            }

            val secondAccount = acquire(secondId, this)
            if (secondAccount == null) {
                release(firstId, this)
                release(secondId, this)
                return
            }

            val fromAcc = if (fromId == firstId) firstAccount else secondAccount
            val toAcc = if (toId == firstId) firstAccount else secondAccount

            when {
                amount > fromAcc.amount -> errorMessage = "Underflow"
                toAcc.amount + amount > MAX_AMOUNT -> errorMessage = "Overflow"
                else -> {
                    fromAcc.newAmount = fromAcc.amount - amount
                    toAcc.newAmount = toAcc.amount + amount
                }
            }

            completed = true

            release(firstId, this)
            release(secondId, this)
        }
    }
}