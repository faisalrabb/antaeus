package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import java.util.*
import io.pleo.antaeus.core.exceptions.*


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
        //get invoices, charge
        val invoices = dal.fetchInvoices()
        for (invoice in invoices) {
            //charge, handle errors
            try {
                val complete = paymentProvider.charge(invoice)
            }
            catch(e: CustomerNotFoundException) {
                println("handle")
            }
            catch(e: CurrencyMismatchException) {
                println("handle")
            }
            catch(e: NetworkException) {
                println("handle")
            }
            finally {
                //change invoice status
            }
        }
    }
}
