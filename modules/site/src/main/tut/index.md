---
layout: home

---

## task4s [![Build Status](https://travis-ci.com/tz70s/task4s.svg?token=q2MTgdyCTSXkarGyJWZp&branch=master)](https://travis-ci.com/tz70s/task4s)

Self-contained, lightweight distributed computation library for data-intensive workload.

### Quick Start

To construct an infamous word count task,

```scala
implicit val stage = Task4s.stage("QuickStartWordCountApp")

// Define cluster level parallelism strategy, a.k.a cluster-wide task allocation.
val strategy = ParStrategy(replicas = 4, role = Seq("node1", "node2"))

// Create a task.
val wordCountTask = Task4s.of(strategy) { implicit stage =>
  val in = Portal("text-data").asIn[String]
  val out = Portal("word-count").asOut[(String, Int)]

  val shape = in
    .flatMap(lines => lines.split(""))
    .map(word => (word, 1)).par(strategy)
    .reduceByKey(_ + _)
    .to(out)

  TaskParShape(shape)
}

// The spawn method will required stage as implicit parameter.
// While the return lifecycle can be used to monitor task status.
val lifecycleSignal = Task4s.spawn(wordCountTask)
```