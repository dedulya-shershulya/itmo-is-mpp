import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Bank implementation.
 *
 * Thread-safe implementation
 *
 * @author Chkareuli George
 */
class BankImpl(n: Int) : Bank {
    private val accounts: Array<Account> = Array(n) { Account() }

    override val accountsCount: Int
        get() = accounts.size

    /**
     * Thread-safe
     */
    override fun amount(id: Int): Long {
        return accounts[id].lock.withLock { accounts[id].amount }
    }

    /**
     * Thread-safe
     */
    override val totalAmount: Long
        get() {
            for (account in accounts) {
                account.lock.lock()
            }
            try {
                return accounts.sumOf { it.amount }
            } finally {
                for (i in accounts.lastIndex downTo 0) {
                    accounts[i].lock.unlock()
                }
            }
        }

    /**
     * Thread-safe deposit method
     */
    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        return accounts[id].lock.withLock {
            val account = accounts[id]
            check(!(amount > Bank.MAX_AMOUNT || account.amount + amount > Bank.MAX_AMOUNT)) { "Overflow" }
            account.amount += amount
            account.amount
        }
    }

    /**
     * Thread-safe withdraw method
     */
    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }
        return accounts[id].lock.withLock {
            val account = accounts[id]
            check(account.amount - amount >= 0) { "Underflow" }
            account.amount -= amount
            account.amount
        }
    }

    /**
     *  Thread-safe transfer method
     */
    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }

        val firstId = minOf(fromId, toId)
        val secondId = maxOf(fromId, toId)

        accounts[firstId].lock.lock()
        try {
            accounts[secondId].lock.lock()
            try {
                val from = accounts[fromId]
                val to = accounts[toId]
                check(amount <= from.amount) { "Underflow" }
                check(!(amount > Bank.MAX_AMOUNT || to.amount + amount > Bank.MAX_AMOUNT)) { "Overflow" }
                from.amount -= amount
                to.amount += amount
            } finally {
                accounts[secondId].lock.unlock()
            }
        } finally {
            accounts[firstId].lock.unlock()
        }
    }

    /**
     * Private account data structure.
     */
    class Account {
        /**
         * Amount of funds in this account.
         */
        var amount: Long = 0

        val lock = ReentrantLock()
    }
}