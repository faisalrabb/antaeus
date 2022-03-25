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

Postconditions: All invalid invoices are removed, all valid and payable invoices are processed, and all valid but non-payable invoices (i.e. invoices for which the customer has insufficient balance to pay) are unchanged. 


To schedule this periodic job, I make use of Kotlin's concurrency package, specifically the scheduleAtFixedRate function. The scheduler logic is defined in AntaeusApp.main(), and calls a function paySubscriptions() which is defined in the BillingService class. 

The paySubscriptions() functions relies on a function I declared in the DAL, fetchPending(), which is similar to fetchInvoices(), except that it only selects invoices which have a status of Pending. This is done to reduce runtime; as the system grows, there will be a growing number of paid invoices which are not relevant in the scope of this operation, and it is wasteful to construct Invoice objects for database entries which are not useful to us. Peforming an SQL Select Where operation reduces the number of database entries we have to deal with and leads to more better performance. 

The paySubscriptions() function then iterates through all of the pending invoices, and sends them along to the process() function. The process function attempts to charge the customer using the PaymentProvider's charge function, and includes exception handling for the various exceptions defined in the PaymentProvider class: 

* CustomerNotFoundException - in this case we can assume a customer no longer exists (or never has), and therefore their pending invoices are no longer relevant. When this exception is encountered, the invoice is deleted. 
* CurrencyMismatchException - When this exception is encountered, a new invoice is created in the database, with all the same information from the old invoice, except with a new currency to reflect the customer's chosen currency. The invoice is then fed back into the process() function to be retried. This solution of course assumes that prices are the same across different markets. 
* NetworkException - An assumption is made here that this exception occurs due to a temporary network issue, and therefore the thread is put to sleep for 5 seconds and then the transaction is retried. 

## Design Decisions (and Implications) 

### Task Scheduling

One of the goals of this implementation was to not introduce any new external dependencies to the system. For this reason, Kotlin's concurrency package was used. The way the task is scheduled is that it is executed when the system starts up, and then in intervals of exactly 24 hours thereafter. The task runs as a background process on a separate thread, so it should not interfere with the rest of the system's functions. 

The reason this task is scheduled to be run every 24 hours is that Kotlin's internal scheduling capabilities only allow for jobs to be scheduled with a predetermined period; since months have varying durations, it is not possible to find such a period, and all solutions require unnecessarily complicated workarounds that could lead to more unexpected behaviours.

The paySubscriptions() functions checks whether it is the first day of the month, if it is not, the function simply returns without doing any work. Since checking the date is quite a light task computationally, and due to the reduction in complexity that this approach allows, this implementation was chosen. 

Another benefit of this system of task scheduling is that temporary system outages should not have any effect on the processing of invoices (unless of course the system is down for the whole day on the 1st of the month), since the paySubscriptions() function is called as soon as the system is started, and then every 24 hours, we do not have to set a pre-determined time for this task to be run, and therefore we don't run the risk that the system is down during the time we set previously (i.e. we will never miss a task as long as the system has at least some uptime during the 1st day of the month). In the worst case, we can have a situation in which this task is run multiple times on the 1st of the month. Given the postconditions specified above, however, this would most likely be a good thing rather than a bad thing, since it would give the system a chance to retry valid invoices for which the customer was not able to pay previously. 

### Exception Handling

* CustomerNotFoundException - 
* CurrencyMismatchException - 
* NetworkException - 


### DAL Functions 

