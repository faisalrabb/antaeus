package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import java.util.*
import io.pleo.antaeus.core.exceptions.*
import io.pleo.antaeus.models.*


class BillingService(
    private val paymentProvider: PaymentProvider
) {
// TODO - Add code e.g. here


    fun paySubscriptions(dal: AntaeusDal) {
        //check that today is the 1st
        val calendar = Calendar.getInstance()
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        if (day != 1) {
            return
        }
        //get invoices, process
        val invoices = dal.fetchPending()
        for (invoice in invoices) {
            //process invoice
            var complete = process(invoice, dal)
            //var complete is of type Boolean and indicates whether the charge was successful
        }
    }


    private fun process(invoice: Invoice, dal: AntaeusDal): Boolean {
        var complete = false
        try {
            complete = paymentProvider.charge(invoice)
        }
        catch(e: CustomerNotFoundException) {
            //do nothing/delete invoice
            dal.deleteInvoice(invoice)
            return false
        }
        catch(e: CurrencyMismatchException) {
            //create new invoice with correct currency, try again
            val customer = dal.fetchCustomer(invoice.customerId)
            if (customer != null && invoice.amount.currency != customer.currency) {
                val money = Money(value=invoice.amount.value, currency=customer.currency)
                dal.deleteInvoice(invoice)
                process(dal.createInvoice(money, customer)!!, dal)
            }
        }
        catch(e: NetworkException) {
            //retry after 5s
            Thread.sleep(5000)
            process(invoice, dal)
        }
        if (complete) {
            //update invoice in the db to reflect that the payment is successful
            dal.markPaid(invoice)
        }
        return complete
    }
}

