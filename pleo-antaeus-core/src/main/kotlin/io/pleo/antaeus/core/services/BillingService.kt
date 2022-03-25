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
            var complete = process(invoice)
            if (complete) {
                dal.markPaid(invoice)
            }
        }
    }


    private fun process(invoice: Invoice): Boolean {
        var complete = false
        try {
            complete = paymentProvider.charge(invoice)
            return complete
        }
        catch(e: CustomerNotFoundException) {
            //do nothing/delete invoice
            println("handle")
        }
        catch(e: CurrencyMismatchException) {
            //fix currency, try again
            println("handle")
        }
        catch(e: NetworkException) {
            //retry after 10s
            Thread.sleep(10000)
            process(invoice)
        }
        return complete
    }
}

