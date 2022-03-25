# Antaeus Pleo Coding Challenge 

In this markdown file I will go over my solution for Pleo's Backend Coding Challenge.

## Altered Classes and Functions

### AntaeusDal.kt:

*  deleteInvoice(invoice: Invoice)
*  fetchPending(): List<<Invoice>Invoice>
*  markPaid(invoice: Invoice)

### BillingService.kt:

* paySubscriptions(dal: AntaeusDal)
* process(invoice: Invoice, dal: AntaeusDal)

### AntaeusApp.kt:

* main()

## Solution (Explained)

Preconditions: Invoices with PENDING status are inserted into the database prior to the first of the month for each customer.

Postconditions: All invalid invoices are either removed or fixed, all valid and payable invoices have their status updated to 'PAID', and all valid but non-payable invoices (i.e. invoices for which the customer has insufficient balance to pay) are unchanged. 


To schedule this periodic job, I make use of Kotlin's concurrency package, specifically the scheduleAtFixedRate function. The scheduler logic is defined in AntaeusApp.main(), and calls a function paySubscriptions() which is defined in the BillingService class. 

The paySubscriptions() functions relies on a function I declared in the DAL, fetchPending(), which is similar to fetchInvoices(), except that it only selects invoices which have a status of Pending. This is done to reduce runtime; as the system grows, there will be a growing number of paid invoices which are not relevant in the scope of this operation, and it is wasteful to construct Invoice objects using the map operation for database entries which are not useful to us. Peforming an SQL Select Where operation reduces the number of database entries we have to deal with and thus leads to better performance. 

The paySubscriptions() function iterates through all of the pending invoices, and sends them along to the process() function. The process function attempts to charge the customer using the PaymentProvider's charge function, and handles the various exceptions defined in the PaymentProvider class:

* CustomerNotFoundException - in this case we assume a customer no longer exists (or never has), and therefore their pending invoices are no longer relevant.
* CurrencyMismatchException - When this exception is encountered, a new invoice is created in the database, with all the same information from the old invoice, except with a new currency to reflect the customer's chosen currency. The invoice is then fed back into the process() function to be retried.
* NetworkException - An assumption is made here that this exception occurs due to a temporary network issue, and therefore the thread is put to sleep for 5 seconds and then the transaction is retried.

If an invoice is successfuly charged to the customer, markPaid(invoice: Invoice) is called, and the 'status' of the invoice in the database is changed to 'PAID'. 

## Design Decisions (and Implications) 

### Task Scheduling

One of the goals of this implementation was to not introduce any new external dependencies to the system. For this reason, Kotlin's concurrency package was used. The way the task is scheduled is that it is executed when the system starts up, and then in intervals of exactly 24 hours thereafter. The task runs as a background process on a separate thread, so it should not interfere with the rest of the system's functions. 

The reason this task is scheduled to be run every 24 hours is that Kotlin's internal scheduling capabilities only allow for jobs to be scheduled with a predetermined period; since months have varying durations, it is not possible to find a period that would ensure this task is always run on the 1st of the month.

The paySubscriptions() functions checks whether it is the first day of the month, if it is not, the function simply returns without doing any work. Since checking the date is quite a light task computationally, and due to the reduction in complexity that this approach allows, this implementation was chosen. 

Another benefit of this system of task scheduling is that temporary system outages should not have any effect on the processing of invoices (unless of course the system is down for the whole day on the 1st of the month). For example, if we had set a pre-determined time for the invoices to be processed (say 00:00 UTC on the 1st of the month), we would need to either ensure that the system is always up at that time, or develop a rescheduling policy. This adds unnecessary complexity and is less flexible than just running paySubscriptions() once a day. Using this implementation, we would only need to ensure that the system has at least some uptime anytime in the day on the 1st of the month every month. 

In the worst case, we can have a situation in which this task is run multiple times on the 1st of the month (if the system is restarted multiple times on that day). Given the postconditions specified above, however, this would be a good thing, since it would give the system a chance to retry valid invoices for which the customer was not able to pay previously. 

### Exception Handling

* CustomerNotFoundException - An important implication of the handling of this exception is that certain state information is forever removed from the database. There could be situations in which it would be beneficial to keep track of unpaid invoices for old customers, in which case the handling of this exception should be changed.
* CurrencyMismatchException - The reason that the invoice is deleted from the database, a new one is created, and a new Invoice object is constructed from the new database entry (as opposed to creating a 'dummy' object with all the same values except for the currency) is to ensure that the database always has a valid state and that there is no situation in which the application layer is processing an Invoice object that does not exist in the database. This increases the number of database operations, which are relatively 'slow', but increases data integrity in the system overall.
* NetworkException - Here we choose to block the thread and retry after 5s. The benefit of this solution is that when a network error is detected, all invoice processing is temporary halted until the network is fixed, and so in theory temporary interruptions should not affect the final outcome of the paySubscriptions() function. There is always the possibility that NetworkExceptions keep occuring, and the thread can remain blocked for some time. Since, however, this process is not run on the main thread, this should not affect the rest of the system. 


