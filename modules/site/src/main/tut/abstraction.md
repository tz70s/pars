---
layout: page
title:  "Abstraction"
section: "abstraction"
position: 2

---

### Abstraction

To construct the computation, the basic primitive is `Task`, which is analog to a computation instance, but with distributed capabilities.

The `Task` instance has following properties:

1. Stateless.
2. Serving like a function with topic-based pub/sub model, with one binding for specific topic.
3. Data coming from different topic served as columnar data for analytics workload.
4. Can be distinguished for two types, `LocalTask` and `ClusterTask`, which refer to capability of executing transparently on cluster or not.

### Create Tasks

To build a task, you need to define following properties:

* Analytics flow which binds to single source and single sink.
* Type of task, i.e. local or cluster. It can be constructed via factory methods, analog to `Task.local` and `Task.cluster`.

