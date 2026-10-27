package com.cordobot.cuentaclara.domain

import com.cordobot.cuentaclara.domain.model.Expense
import com.cordobot.cuentaclara.domain.model.Settlement

object Balances {

    /**
     * Net balance of every member: positive means the group owes them, negative that they owe.
     * Each expense is split in equal shares; leftover cents go to the first participants so the
     * shares always add up to the exact amount.
     */
    fun net(memberIds: List<String>, expenses: List<Expense>): Map<String, Long> {
        val balance = memberIds.associateWith { 0L }.toMutableMap()
        for (expense in expenses) {
            val participants = expense.splitAmong.ifEmpty { memberIds }
            val share = expense.amountCents / participants.size
            val remainder = expense.amountCents % participants.size
            participants.forEachIndexed { index, uid ->
                val owed = share + if (index < remainder) 1 else 0
                balance[uid] = (balance[uid] ?: 0) - owed
            }
            balance[expense.paidBy] = (balance[expense.paidBy] ?: 0) + expense.amountCents
        }
        return balance
    }

    /**
     * Few transfers that settle every balance: the biggest debtor pays the biggest creditor
     * until one of them reaches zero. At most n - 1 transfers for n members.
     */
    fun settle(net: Map<String, Long>): List<Settlement> {
        val creditors = net.filterValues { it > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val debtors = net.filterValues { it < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
        val result = mutableListOf<Settlement>()
        while (creditors.isNotEmpty() && debtors.isNotEmpty()) {
            val (creditor, credit) = creditors.first()
            val (debtor, debt) = debtors.first()
            val amount = minOf(credit, debt)
            result += Settlement(debtor, creditor, amount)
            creditors[0] = creditor to credit - amount
            debtors[0] = debtor to debt - amount
            if (creditors[0].second == 0L) creditors.removeAt(0)
            if (debtors[0].second == 0L) debtors.removeAt(0)
            creditors.sortByDescending { it.second }
            debtors.sortByDescending { it.second }
        }
        return result
    }
}
