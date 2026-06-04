import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

/**
 * Bank implementation.
 *
 * @author Chkareuli Georgiy
 */
class BankImpl(n: Int) : Bank {
    private val accounts: Array<Account> = Array(n) { Account() }

    override val accountsCount: Int
        get() = accounts.size

    override fun amount(id: Int): Long {
        return accounts[id].lock.readLock().withLock {
            accounts[id].amount
        }
    }

    override val totalAmount: Long
        get() {
            accounts.forEach {
                it.lock.readLock().lock()
            }

            return try {
                accounts.sumOf { it.amount }
            }
            finally {
                accounts.forEach {
                    it.lock.readLock().unlock()
                }
            }
        }

    override fun deposit(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }

        return accounts[id].lock.writeLock().withLock {
            val account = accounts[id]

            check(amount <= Bank.MAX_AMOUNT && account.amount + amount <= Bank.MAX_AMOUNT) { "Overflow" }

            account.amount += amount
            account.amount
        }
    }

    override fun withdraw(id: Int, amount: Long): Long {
        require(amount > 0) { "Invalid amount: $amount" }

        return accounts[id].lock.writeLock().withLock {
            val account = accounts[id]

            check(account.amount - amount >= 0) { "Underflow" }

            account.amount -= amount
            account.amount
        }
    }

    override fun transfer(fromId: Int, toId: Int, amount: Long) {
        require(amount > 0) { "Invalid amount: $amount" }
        require(fromId != toId) { "fromId == toId" }

        val (first, second) = if (fromId < toId) {
            accounts[fromId] to accounts[toId]
        } else {
            accounts[toId] to accounts[fromId]
        }

        first.lock.writeLock().lock()

        try {
            second.lock.writeLock().lock()
            try {
                val from = accounts[fromId]
                val to = accounts[toId]

                check(amount <= from.amount) { "Underflow" }
                check(amount <= Bank.MAX_AMOUNT && to.amount + amount <= Bank.MAX_AMOUNT) { "Overflow" }

                from.amount -= amount
                to.amount += amount
            }
            finally {
                second.lock.writeLock().unlock()
            }
        }
        finally {
            first.lock.writeLock().unlock()
        }
    }

    override fun consolidate(fromIds: List<Int>, toId: Int) {
        require(fromIds.isNotEmpty()) { "empty fromIds" }
        require(fromIds.distinct() == fromIds) { "duplicates in fromIds" }
        require(toId !in fromIds) { "toId in fromIds" }

        val allIds = (fromIds + toId).sorted()
        allIds.forEach {
            accounts[it].lock.writeLock().lock()
        }

        try {
            val fromList = fromIds.map { accounts[it] }
            val to = accounts[toId]
            val amount = fromList.sumOf { it.amount }

            check(to.amount + amount <= Bank.MAX_AMOUNT) { "Overflow" }

            for (from in fromList) from.amount = 0
            to.amount += amount
        }
        finally {
            allIds.forEach {
                accounts[it].lock.writeLock().unlock()
            }
        }
    }

    class Account {
        var amount: Long = 0
        val lock = ReentrantReadWriteLock()
    }
}