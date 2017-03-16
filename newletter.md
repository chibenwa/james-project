# Linagora's James newsletter

Linagora have a team of developpers devoted to the James server. We are already contributing for a few years to the project. This document will try to give you information about what we recently did for the James project, and give hints about the road-map we follow. We will also try to write it on a regular bases

## Deploying James internally

We now use James as the mail server behind OpenPaaS. Thus we deployed it and use it on an every-day base.

Our deployment is based on the following components :
 - We enabled IMAP, SMTP and JMAP protocols
 - We use Cassandra for storing e-mails
 - We rely on ElasticSearch for searching e-mails
 - We authenticate our users with LDAP
 - We use Guice for bringing all pieces together.
 - We plan to bring soon Sieve and ManageSieve part of this deployment as many peaple request filtering.

Let me share a few numbers with you :

 - We handle about 100 users
 - We have so far a number of 1.500.000 e-mails, that we imported using IMAP-sync scripts
 - We receive around 15.000 incoming e-mails a day
 - We execute 42.500 IMAP commands a day
 - We answer 20.000 JMAP requests a day

This deployment help us detecting bugs, and performance issues. We follow the current state of master branch, that we update at midnight 
with the built image uploaded on dockerhub.

## Tracking performance

Everybody wants to read their e-mails fast. Thus the team made performance tracking a priority. And we developped the tools to follow performance.

We started implementing Gatling load testing for the James server :

 - First with a JMAP implementation.
 - Then we added a naive SMTP implementation
 - And finally contributed a IMAP DSL for Gatling
 
 Running these load tests we :
 
  - Succeeded to support 1.000 thunderbird like users
  - However, it turned out we had problems with 10.000 users.
 
 Additionnaly, we added metrics a bit everywhere in James using the brand new metrics API. We collect and export everything in ElasticSearch using Dropwizard metrics.
 Then we graph it all using Grafana. This allow us to cellect all statistic and percentiles. We track so far :
 
  - Protocols detailed time execution (and count)
  - Percentile of mailet and matcher executions
  - Enqueue and Dequeue time
 
 All these solutions allow us to identify the componants that needs improvement. For instance receiving  too much incoming e-mails overload James with very heavy garbage collection.
 We then plan to move our mail queue to RabbitMQ, to parse e-mails only once on top of the mail pipeline, to reorganize a bit our configuration. A Camel upgrade (impossible in java-6) might also help.
 
 ## MessageId refactoring
 
 We succeeded to finish and merge the MessageId refactoring. This hudge task allow us to address messages by their ID, outside of mailbox context.
 
 This is required for JMAP protocol implementation.
 
 This is now supported by the Cassandra and Memory implementation. We design a system of capabilities to allow enabling only the supported parts of protocols, in an implementation agnostic way.
 
 ## Our incoming plans
 
 We are pretty happy with the current state of the James server. We will then push for the 3.0 release of the James server.
 
 For this :
 
  - We need to do some load testing on top of JPA implementation
  - We will continue doing bugfixes
  - We need some additional performance enhacement, especially with IMAP SELECT command on large mailboxes
  - We plan to start working again on the new website, which have been paused a few months
 
 
 
 
 
 
 
